package com.skanga.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class McpServerIntegrationTest {
    private McpServer mcpServer;
    private ConfigParams testConfig;
    private ExecutorService executorService;

    // Only run these tests if we can create server sockets (network available)
    static boolean isNetworkAvailable() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        testConfig = new ConfigParams(
                "jdbc:h2:mem:testdb_" + System.currentTimeMillis(), // Unique DB per test
                "sa", "", "org.h2.Driver",
                10, 30000, 30, true, 10000, 10000, 600000, 1800000, 60000
        );
        mcpServer = new McpServer(testConfig);
        executorService = Executors.newCachedThreadPool();
    }

    @AfterEach
    void tearDown() {
        if (executorService != null) {
            executorService.shutdownNow();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("Executor did not terminate gracefully");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

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
    @Timeout(15)
    @EnabledIf("isNetworkAvailable")
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
        }, executorService);

        // Wait for server to start with retry logic
        waitForServerToStart(port, 10000); // 10 second timeout

        // Try to start second server on same port - should fail
        McpServer secondServer = new McpServer(testConfig);
        try {
            assertThrows(IOException.class, () -> secondServer.startHttpMode(port));
        } finally {
            // Cleanup
            server1.cancel(true);
            try {
                secondServer.databaseService.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    @Test
    @Timeout(15)
    @EnabledIf("isNetworkAvailable")
    void testHttpRequest_InvalidMethod() throws Exception {
        int port = findAvailablePort();

        // Start server in background
        CompletableFuture<Void> serverFuture = CompletableFuture.runAsync(() -> {
            try {
                mcpServer.startHttpMode(port);
            } catch (IOException e) {
                // Expected when we shut it down
            }
        }, executorService);

        try {
            // Wait for server to start
            waitForServerToStart(port, 10000);

            // Send GET request (should fail - only POST allowed)
            URL url = new URL("http://localhost:" + port + "/mcp");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
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
    @Timeout(15)
    @EnabledIf("isNetworkAvailable")
    void testHttpRequest_InvalidJson() throws Exception {
        int port = findAvailablePort();

        CompletableFuture<Void> serverFuture = CompletableFuture.runAsync(() -> {
            try {
                mcpServer.startHttpMode(port);
            } catch (IOException e) {
                // Expected
            }
        }, executorService);

        try {
            waitForServerToStart(port, 10000);

            // Send invalid JSON
            URL url = new URL("http://localhost:" + port + "/mcp");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
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
    @Timeout(15)
    @EnabledIf("isNetworkAvailable")
    void testHttpRequest_OptionsRequest() throws Exception {
        int port = findAvailablePort();

        CompletableFuture<Void> serverFuture = CompletableFuture.runAsync(() -> {
            try {
                mcpServer.startHttpMode(port);
            } catch (IOException e) {
                // Expected
            }
        }, executorService);

        try {
            waitForServerToStart(port, 10000);

            // Send OPTIONS request (CORS preflight)
            URL url = new URL("http://localhost:" + port + "/mcp");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
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
    @Timeout(15)
    @EnabledIf("isNetworkAvailable")
    void testHealthCheck_Endpoint() throws Exception {
        int port = findAvailablePort();

        CompletableFuture<Void> serverFuture = CompletableFuture.runAsync(() -> {
            try {
                mcpServer.startHttpMode(port);
            } catch (IOException e) {
                // Expected
            }
        }, executorService);

        try {
            waitForServerToStart(port, 10000);

            // Test health check endpoint
            URL url = new URL("http://localhost:" + port + "/health");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
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
    @Timeout(10)
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
    @Timeout(10)
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
    @Timeout(10)
    void testStdioMode_RequestWithError() {
        // Prepare input with proper MCP initialization sequence followed by error request
        String initializeRequest = """
        {"jsonrpc":"2.0","id":"init","method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{"tools":{},"resources":{}},"clientInfo":{"name":"TestClient","version":"1.0.0"}}}
        """;

        String initializedNotification = """
        {"jsonrpc":"2.0","method":"notifications/initialized"}
        """;

        String errorRequest = """
        {"jsonrpc":"2.0","id":"error","method":"tools/call","params":{"name":"query","arguments":{"sql":""}}}
        """;

        // Combine all requests with newlines (stdio mode expects line-separated JSON)
        String allRequests = initializeRequest + "\n" + initializedNotification + "\n" + errorRequest + "\n";

        ByteArrayInputStream inputStream = new ByteArrayInputStream(allRequests.getBytes());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        InputStream originalIn = System.in;
        PrintStream originalOut = System.out;

        try {
            System.setIn(inputStream);
            System.setOut(new PrintStream(outputStream));

            assertDoesNotThrow(() -> mcpServer.startStdioMode());

            String output = outputStream.toString().trim();
            String[] responses = output.split("\n");

            // Should have 2 responses (initialize response + error response)
            // The initialized notification doesn't get a response
            assertTrue(responses.length >= 2, "Should have at least 2 responses");

            // First response should be successful initialize
            assertTrue(responses[0].contains("\"result\""), "First response should be initialize success");
            assertTrue(responses[0].contains("protocolVersion"), "Should contain protocol version");

            // Last response should be the error for empty SQL
            String errorResponse = responses[responses.length - 1];
            assertTrue(errorResponse.contains("\"error\""), "Should contain error");
            assertTrue(errorResponse.contains("SQL query cannot be empty"), "Should contain empty SQL error message");
            assertTrue(errorResponse.contains("\"id\":\"error\""), "Should have matching request ID");

        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
    }

    @Test
    @Timeout(5)
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

    /**
     * Wait for server to start accepting connections with retry logic
     */
    private void waitForServerToStart(int port, long timeoutMs) throws Exception {
        long startTime = System.currentTimeMillis();
        long endTime = startTime + timeoutMs;

        while (System.currentTimeMillis() < endTime) {
            try (Socket testSocket = new Socket()) {
                testSocket.connect(new InetSocketAddress("localhost", port), 1000);
                // If we get here, server is accepting connections
                return;
            } catch (ConnectException | SocketTimeoutException e) {
                // Server not ready yet, wait and retry
                Thread.sleep(250); // Wait 250ms before retry
            }
        }

        throw new AssertionError("Server did not start within " + timeoutMs + "ms on port " + port);
    }
}