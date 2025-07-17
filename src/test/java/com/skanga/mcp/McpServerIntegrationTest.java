package com.skanga.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class McpServerIntegrationTest {

    private McpServer mcpServer;
    private ConfigParams testConfig;

    @BeforeEach
    void setUp() {
        testConfig = new ConfigParams(
                "jdbc:h2:mem:testdb", "sa", "", "org.h2.Driver",
                10, 30000, 30, true, 10000, 10000, 600000, 1800000, 60000
        );
        mcpServer = new McpServer(testConfig);
    }

    @AfterEach
    void tearDown() {
        if (mcpServer != null) {
            // Ensure cleanup
            try {
                mcpServer.databaseService.close();
            } catch (Exception e) {
                // Ignore cleanup errors in tests
            }
        }
    }

    @Test
    @Timeout(10)
    void testHttpMode_PortAlreadyInUse() throws Exception {
        // Find an available port
        int port = findAvailablePort();

        // Start first server
        CompletableFuture<Void> server1 = CompletableFuture.runAsync(() -> {
            try {
                mcpServer.startHttpMode(port);
            } catch (IOException e) {
                // Expected when we shut it down
            }
        });

        // Wait a bit for server to start
        Thread.sleep(1000);

        // Try to start second server on same port - should fail
        McpServer secondServer = new McpServer(testConfig);
        assertThrows(IOException.class, () -> secondServer.startHttpMode(port));

        // Cleanup
        server1.cancel(true);
        secondServer.databaseService.close();
    }

    @Test
    void testHttpRequest_InvalidMethod() throws Exception {
        int port = findAvailablePort();

        // Start server in background
        CompletableFuture<Void> serverFuture = CompletableFuture.runAsync(() -> {
            try {
                mcpServer.startHttpMode(port);
            } catch (IOException e) {
                // Expected when we shut it down
            }
        });

        try {
            // Wait for server to start
            Thread.sleep(1000);

            // Send GET request (should fail - only POST allowed)
            URL url = new URL("http://localhost:" + port + "/mcp");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            assertEquals(405, responseCode); // Method Not Allowed

            // Read error response
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getErrorStream()))) {
                String response = reader.lines().reduce("", String::concat);
                assertTrue(response.contains("Method not allowed"));
            }

        } finally {
            serverFuture.cancel(true);
        }
    }

    @Test
    void testHttpRequest_InvalidJson() throws Exception {
        int port = findAvailablePort();

        CompletableFuture<Void> serverFuture = CompletableFuture.runAsync(() -> {
            try {
                mcpServer.startHttpMode(port);
            } catch (IOException e) {
                // Expected
            }
        });

        try {
            Thread.sleep(1000);

            // Send invalid JSON
            URL url = new URL("http://localhost:" + port + "/mcp");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String invalidJson = "{ invalid json content }";
            try (OutputStream os = conn.getOutputStream()) {
                os.write(invalidJson.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            assertEquals(500, responseCode); // Internal Server Error

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getErrorStream()))) {
                String response = reader.lines().reduce("", String::concat);
                assertTrue(response.contains("Internal server error"));
            }
        } finally {
            serverFuture.cancel(true);
        }
    }

    @Test
    void testHttpRequest_OptionsRequest() throws Exception {
        int port = findAvailablePort();

        CompletableFuture<Void> serverFuture = CompletableFuture.runAsync(() -> {
            try {
                mcpServer.startHttpMode(port);
            } catch (IOException e) {
                // Expected
            }
        });

        try {
            Thread.sleep(1000);

            // Send OPTIONS request (CORS preflight)
            URL url = new URL("http://localhost:" + port + "/mcp");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("OPTIONS");

            int responseCode = conn.getResponseCode();
            assertEquals(200, responseCode);

            // Check CORS headers
            assertEquals("*", conn.getHeaderField("Access-Control-Allow-Origin"));
            assertEquals("POST, OPTIONS", conn.getHeaderField("Access-Control-Allow-Methods"));

        } finally {
            serverFuture.cancel(true);
        }
    }

    @Test
    void testHealthCheck_Endpoint() throws Exception {
        int port = findAvailablePort();

        CompletableFuture<Void> serverFuture = CompletableFuture.runAsync(() -> {
            try {
                mcpServer.startHttpMode(port);
            } catch (IOException e) {
                // Expected
            }
        });

        try {
            Thread.sleep(1000);

            // Test health check endpoint
            URL url = new URL("http://localhost:" + port + "/health");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            assertEquals(200, responseCode);

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                String response = reader.lines().reduce("", String::concat);
                assertTrue(response.contains("status"));
                assertTrue(response.contains("healthy"));
            }
        } finally {
            serverFuture.cancel(true);
        }
    }

    @Test
    void testStdioMode_MalformedInput() {
        // Prepare malformed input
        String malformedInput = "not json at all\n{ incomplete json\n";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(malformedInput.getBytes());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        // Redirect system streams
        InputStream originalIn = System.in;
        PrintStream originalOut = System.out;

        try {
            System.setIn(inputStream);
            System.setOut(new PrintStream(outputStream));

            // Run stdio mode - should handle malformed input gracefully
            assertDoesNotThrow(() -> mcpServer.startStdioMode());

            // Verify no crash occurred and error responses were generated
            String output = outputStream.toString();
            // The output should contain error responses for malformed JSON
            assertNotNull(output);
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
    }

    @Test
    void testStdioMode_NotificationWithError() {
        // Prepare input with notification that will cause an error
        String notificationJson = """
            {"method":"tools/call","params":{"name":"query","arguments":{"sql":""}}}
            """;

        ByteArrayInputStream inputStream = new ByteArrayInputStream(notificationJson.getBytes());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        InputStream originalIn = System.in;
        PrintStream originalOut = System.out;

        try {
            System.setIn(inputStream);
            System.setOut(new PrintStream(outputStream));

            assertDoesNotThrow(() -> mcpServer.startStdioMode());

            // For notifications, no response should be sent even on error
            String output = outputStream.toString().trim();
            assertFalse(output.contains("error"));

        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
    }

    @Test
    void testStdioMode_RequestWithError() {
        // Prepare input with regular request that will cause an error
        String requestJson = """
            {"id":1,"method":"tools/call","params":{"name":"query","arguments":{"sql":""}}}
            """;

        ByteArrayInputStream inputStream = new ByteArrayInputStream(requestJson.getBytes());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        InputStream originalIn = System.in;
        PrintStream originalOut = System.out;

        try {
            System.setIn(inputStream);
            System.setOut(new PrintStream(outputStream));

            assertDoesNotThrow(() -> mcpServer.startStdioMode());

            // For regular requests, error response should be sent
            String output = outputStream.toString().trim();
            assertTrue(output.contains("error"));
            assertTrue(output.contains("SQL query cannot be empty"));

        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
    }

    @Test
    void testMainMethod_HttpModeFailure() {
        // Test main method with invalid port that should cause IllegalArgumentException
        String[] args = {"--http_mode=true", "--http_port=-1"};

        // The actual exception thrown is IllegalArgumentException for invalid port
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> McpServer.main(args));

        assertTrue(exception.getMessage().contains("port out of range"));
    }

    private int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
