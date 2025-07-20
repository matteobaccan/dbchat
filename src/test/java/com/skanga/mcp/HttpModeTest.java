package com.skanga.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for HTTP mode functionality
 */
@ExtendWith(MockitoExtension.class)
class HttpModeTest {
    private static final int TEST_PORT = 18080; // Use different port to avoid conflicts
    private static final String BASE_URL = "http://localhost:" + TEST_PORT;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Mock
    private DatabaseService mockDatabaseService;
    
    @Mock
    private ConfigParams mockConfigParams;
    
    private McpServer server;
    private HttpClient httpClient;
    private Thread serverThread;

    @BeforeEach
    void setUp() {
        // Setup mock config params
        lenient().when(mockConfigParams.dbUrl()).thenReturn("jdbc:h2:mem:testdb");
        lenient().when(mockConfigParams.dbUser()).thenReturn("sa");
        lenient().when(mockConfigParams.dbPass()).thenReturn("");
        lenient().when(mockConfigParams.dbDriver()).thenReturn("org.h2.Driver");
        lenient().when(mockConfigParams.maxRowsLimit()).thenReturn(10000);
        lenient().when(mockConfigParams.maxSqlLength()).thenReturn(10000);
        lenient().when(mockConfigParams.queryTimeoutSeconds()).thenReturn(30);
        lenient().when(mockConfigParams.selectOnly()).thenReturn(true);
        lenient().when(mockConfigParams.getDatabaseType()).thenReturn("h2");
        lenient().when(mockDatabaseService.getDatabaseConfig()).thenReturn(mockConfigParams);

        // Create server with mocked database service
        server = new McpServer(mockConfigParams) {
            @Override
            protected DatabaseService createDatabaseService(ConfigParams configParams) {
                return mockDatabaseService;
            }
        };
        
        // Setup HTTP client
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }
    
    @AfterEach
    void tearDown() {
        if (serverThread != null && serverThread.isAlive()) {
            serverThread.interrupt();
            try {
                serverThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private void startHttpServer() throws InterruptedException {
        CountDownLatch serverStartLatch = new CountDownLatch(1);
        
        serverThread = new Thread(() -> {
            try {
                // Signal that we're about to start
                serverStartLatch.countDown();
                server.startHttpMode(TEST_PORT);
            } catch (IOException e) {
                if (!Thread.currentThread().isInterrupted()) {
                    fail("Failed to start HTTP server: " + e.getMessage());
                }
            }
        });
        
        serverThread.setDaemon(true);
        serverThread.start();
        
        // Wait for server thread to start
        assertTrue(serverStartLatch.await(5, TimeUnit.SECONDS), "Server thread failed to start");
        
        // Wait for HTTP server to be ready
        waitForServerReady();
    }
    
    private void waitForServerReady() throws InterruptedException {
        int maxAttempts = 20;
        for (int i = 0; i < maxAttempts; i++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/health"))
                        .timeout(Duration.ofSeconds(2))
                        .GET()
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, 
                        HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    return; // Server is ready
                }
            } catch (Exception e) {
                // Server not ready yet, continue waiting
            }
            
            Thread.sleep(100);
        }
        
        fail("HTTP server did not become ready within timeout");
    }
    
    private HttpResponse<String> sendMcpRequest(String jsonRequest) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/mcp"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                .build();
        
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
    
    @Test
    void testHealthEndpoint() throws Exception {
        startHttpServer();
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, response.statusCode());
        assertEquals("application/json", response.headers().firstValue("Content-Type").orElse(""));
        
        JsonNode healthResponse = objectMapper.readTree(response.body());
        assertEquals("healthy", healthResponse.get("status").asText());
        assertEquals("Database MCP Server", healthResponse.get("server").asText());
        assertTrue(healthResponse.has("timestamp"));
    }
    
    @Test
    void testCorsHeaders() throws Exception {
        startHttpServer();
        
        // Test preflight OPTIONS request
        HttpRequest optionsRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/mcp"))
                .timeout(Duration.ofSeconds(5))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();
        
        HttpResponse<String> optionsResponse = httpClient.send(optionsRequest, 
                HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, optionsResponse.statusCode());
        assertEquals("*", optionsResponse.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
        assertEquals("POST, OPTIONS", optionsResponse.headers().firstValue("Access-Control-Allow-Methods").orElse(""));
        assertEquals("Content-Type", optionsResponse.headers().firstValue("Access-Control-Allow-Headers").orElse(""));
    }
    
    @Test
    void testMethodNotAllowed() throws Exception {
        startHttpServer();
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/mcp"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
        
        assertEquals(405, response.statusCode());
        
        JsonNode errorResponse = objectMapper.readTree(response.body());
        assertEquals("Method not allowed. Use POST.", errorResponse.get("error").asText());
    }
    
    @Test
    void testInitializeRequest() throws Exception {
        startHttpServer();
        
        String initRequest = """
            {
                "jsonrpc": "2.0",
                "id": 1,
                "method": "initialize",
                "params": {
                    "protocolVersion": "2025-03-26",
                    "capabilities": {},
                    "clientInfo": {
                        "name": "test-client",
                        "version": "1.0.0"
                    }
                }
            }
            """;
        
        HttpResponse<String> response = sendMcpRequest(initRequest);
        
        assertEquals(200, response.statusCode());
        assertEquals("application/json", response.headers().firstValue("Content-Type").orElse(""));
        
        JsonNode jsonResponse = objectMapper.readTree(response.body());
        assertEquals("2.0", jsonResponse.get("jsonrpc").asText());
        assertEquals(1, jsonResponse.get("id").asInt());
        assertTrue(jsonResponse.has("result"));
        
        JsonNode result = jsonResponse.get("result");
        assertEquals("2025-03-26", result.get("protocolVersion").asText());
        assertTrue(result.has("capabilities"));
        assertTrue(result.has("serverInfo"));
    }

    @Test
    void testListToolsRequest() throws Exception {
        startHttpServer();

        // Ensure the database config is properly mocked for tool description
        when(mockDatabaseService.getDatabaseConfig()).thenReturn(mockConfigParams);
        when(mockConfigParams.maxRowsLimit()).thenReturn(10000); // Add this mock

        // Initialize the MCP server ONCE before concurrent requests
        initializeMcpServer();

        String listToolsRequest = """
        {
            "jsonrpc": "2.0",
            "id": 2,
            "method": "tools/list",
            "params": {}
        }
        """;

        HttpResponse<String> response = sendMcpRequest(listToolsRequest);

        assertEquals(200, response.statusCode());

        JsonNode jsonResponse = objectMapper.readTree(response.body());
        assertEquals("2.0", jsonResponse.get("jsonrpc").asText());
        assertEquals(2, jsonResponse.get("id").asInt());
        assertTrue(jsonResponse.has("result"));

        JsonNode result = jsonResponse.get("result");
        assertTrue(result.has("tools"));
        assertTrue(result.get("tools").isArray());

        JsonNode tools = result.get("tools");
        assertTrue(tools.size() > 0, "Should have at least one tool");

        // Check that query tool exists
        boolean hasQueryTool = false;
        for (JsonNode tool : tools) {
            if ("query".equals(tool.get("name").asText())) {
                hasQueryTool = true;
                assertTrue(tool.has("description"));
                assertTrue(tool.has("inputSchema"));

                // Verify the tool structure
                assertTrue(tool.get("description").asText().contains("SQL"));

                JsonNode inputSchema = tool.get("inputSchema");
                assertTrue(inputSchema.has("type"));
                assertEquals("object", inputSchema.get("type").asText());
                assertTrue(inputSchema.has("properties"));
                assertTrue(inputSchema.get("properties").has("sql"));

                break;
            }
        }
        assertTrue(hasQueryTool, "Query tool should be present in tools list");
    }

    @Test
    void testQueryToolExecution() throws Exception {
        startHttpServer();

        // Mock database service response
        QueryResult mockResult = new QueryResult(
                List.of("test_column"),
                List.of(List.of("test_value")),
                1,
                50L
        );
        when(mockDatabaseService.executeQuery(any(), any(Integer.class))).thenReturn(mockResult);
        when(mockDatabaseService.getDatabaseConfig()).thenReturn(mockConfigParams);

        // Initialize the MCP server
        initializeMcpServer();

        String queryRequest = """
            {
                "jsonrpc": "2.0",
                "id": 3,
                "method": "tools/call",
                "params": {
                    "name": "query",
                    "arguments": {
                        "sql": "SELECT 'test_value' as test_column",
                        "maxRows": 10
                    }
                }
            }
            """;
        
        HttpResponse<String> response = sendMcpRequest(queryRequest);
        
        assertEquals(200, response.statusCode());
        
        JsonNode jsonResponse = objectMapper.readTree(response.body());
        assertEquals("2.0", jsonResponse.get("jsonrpc").asText());
        assertEquals(3, jsonResponse.get("id").asInt());
        assertTrue(jsonResponse.has("result"));
        
        JsonNode result = jsonResponse.get("result");
        assertTrue(result.has("content"));
        assertTrue(result.get("content").isArray());
        assertFalse(result.get("isError").asBoolean());
    }
    
    @Test
    void testNotificationRequest() throws Exception {
        startHttpServer();
        
        // Notification request (no id field)
        String notificationRequest = """
            {
                "jsonrpc": "2.0",
                "method": "initialize",
                "params": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {},
                    "clientInfo": {
                        "name": "test-client",
                        "version": "1.0.0"
                    }
                }
            }
            """;
        
        HttpResponse<String> response = sendMcpRequest(notificationRequest);
        
        // Notifications should return 204 No Content
        assertEquals(204, response.statusCode());
        assertTrue(response.body().isEmpty());
    }
    
    @Test
    void testInvalidJsonRequest() throws Exception {
        startHttpServer();
        
        String invalidJson = "{ invalid json }";
        
        HttpResponse<String> response = sendMcpRequest(invalidJson);
        
        assertEquals(500, response.statusCode());
        
        JsonNode errorResponse = objectMapper.readTree(response.body());
        assertTrue(errorResponse.has("error"));
        assertTrue(errorResponse.get("error").asText().contains("Internal server error"));
    }
    
    @Test
    void testInvalidMethodRequest() throws Exception {
        startHttpServer();
        
        String invalidMethodRequest = """
            {
                "jsonrpc": "2.0",
                "id": 4,
                "method": "invalid/method",
                "params": {}
            }
            """;
        // Initialize the MCP server ONCE before concurrent requests
        initializeMcpServer();

        HttpResponse<String> response = sendMcpRequest(invalidMethodRequest);
        
        assertEquals(200, response.statusCode());
        
        JsonNode jsonResponse = objectMapper.readTree(response.body());
        assertEquals("2.0", jsonResponse.get("jsonrpc").asText());
        assertEquals(4, jsonResponse.get("id").asInt());
        assertTrue(jsonResponse.has("error"));
        
        JsonNode error = jsonResponse.get("error");
        assertEquals(-32601, error.get("code").asInt()); // Method not found
        assertTrue(error.get("message").asText().contains("Method not found"));
    }

    @Test
    void testDatabaseErrorHandling() throws Exception {
        startHttpServer();

        // Mock database service to throw exception
        when(mockDatabaseService.executeQuery(any(), any(Integer.class)))
                .thenThrow(new SQLException("Database connection failed"));
        when(mockDatabaseService.getDatabaseConfig()).thenReturn(mockConfigParams);
        when(mockConfigParams.getDatabaseType()).thenReturn("h2");

        String queryRequest = """
        {
            "jsonrpc": "2.0",
            "id": 5,
            "method": "tools/call",
            "params": {
                "name": "query",
                "arguments": {
                    "sql": "SELECT 1",
                    "maxRows": 10
                }
            }
        }
        """;
        // Initialize the MCP server ONCE before concurrent requests
        initializeMcpServer();

        HttpResponse<String> response = sendMcpRequest(queryRequest);

        assertEquals(200, response.statusCode());

        JsonNode jsonResponse = objectMapper.readTree(response.body());
        assertEquals("2.0", jsonResponse.get("jsonrpc").asText());
        assertEquals(5, jsonResponse.get("id").asInt());

        // Should be successful MCP response with tool error (MCP spec compliance)
        assertTrue(jsonResponse.has("result"));
        assertFalse(jsonResponse.has("error"));

        JsonNode result = jsonResponse.get("result");
        assertTrue(result.get("isError").asBoolean());
        assertTrue(result.has("content"));

        // Verify error content
        JsonNode content = result.get("content");
        assertTrue(content.isArray());
        assertTrue(content.size() > 0);

        JsonNode textContent = content.get(0);
        assertEquals("text", textContent.get("type").asText());

        String errorText = textContent.get("text").asText();
        assertTrue(errorText.contains("SQL Error"));
        assertTrue(errorText.contains("Database connection failed"));
    }

    @Test
    void testIdHandling() throws Exception {
        startHttpServer();
        
        // Test with string ID
        String stringIdRequest = """
            {
                "jsonrpc": "2.0",
                "id": "string-id",
                "method": "initialize",
                "params": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {},
                    "clientInfo": {"name": "test", "version": "1.0"}
                }
            }
            """;
        
        HttpResponse<String> response = sendMcpRequest(stringIdRequest);
        assertEquals(200, response.statusCode());
        
        JsonNode jsonResponse = objectMapper.readTree(response.body());
        assertEquals("string-id", jsonResponse.get("id").asText());
        
        // Test with null ID
        String nullIdRequest = """
            {
                "jsonrpc": "2.0",
                "id": null,
                "method": "initialize",
                "params": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {},
                    "clientInfo": {"name": "test", "version": "1.0"}
                }
            }
            """;
        
        response = sendMcpRequest(nullIdRequest);
        assertEquals(200, response.statusCode());
        
        jsonResponse = objectMapper.readTree(response.body());
        assertTrue(jsonResponse.get("id").isNull());
    }
    
    @Test
    void testConcurrentRequests() throws Exception {
        startHttpServer();
        
        // Mock database service
        QueryResult mockResult = new QueryResult(
                java.util.List.of("id"),
                java.util.List.of(java.util.List.of(1)),
                1,
                10L
        );
        when(mockDatabaseService.executeQuery(any(), any(Integer.class))).thenReturn(mockResult);
        when(mockDatabaseService.getDatabaseConfig()).thenReturn(mockConfigParams);

        // Initialize the MCP server ONCE before concurrent requests
        initializeMcpServer();

        // Send multiple concurrent requests
        int numRequests = 5;
        Thread[] threads = new Thread[numRequests];
        boolean[] results = new boolean[numRequests];
        
        for (int i = 0; i < numRequests; i++) {
            final int requestId = i;
            threads[i] = new Thread(() -> {
                try {
                    String request = String.format("""
                        {
                            "jsonrpc": "2.0",
                            "id": %d,
                            "method": "tools/call",
                            "params": {
                                "name": "query",
                                "arguments": {
                                    "sql": "SELECT %d as id",
                                    "maxRows": 10
                                }
                            }
                        }
                        """, requestId, requestId);
                    
                    HttpResponse<String> response = sendMcpRequest(request);
                    
                    if (response.statusCode() == 200) {
                        JsonNode jsonResponse = objectMapper.readTree(response.body());
                        if (jsonResponse.get("id").asInt() == requestId && 
                            jsonResponse.has("result")) {
                            results[requestId] = true;
                        }
                    }
                } catch (Exception e) {
                    // Request failed
                    results[requestId] = false;
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all to complete
        for (Thread thread : threads) {
            thread.join(10000); // 10 second timeout
        }
        
        // Check all requests succeeded
        for (int i = 0; i < numRequests; i++) {
            assertTrue(results[i], "Request " + i + " should have succeeded");
        }
    }
    
    @Test
    void testServerShutdownCleanup() throws Exception {
        startHttpServer();

        // Verify server is running
        HttpRequest healthRequest = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/health"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(healthRequest,
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());

        // Stop server
        serverThread.interrupt();
        serverThread.join(5000);

        // Wait a bit for the server to fully shut down
        Thread.sleep(1000);

        // Verify server is no longer responding
        // Use a more specific exception check
        assertThrows(IOException.class, () -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/health"))
                    .timeout(Duration.ofSeconds(1)) // Short timeout
                    .GET()
                    .build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }, "Should throw IOException when server is stopped");
    }

    /**
     * Helper method to initialize the MCP server for testing
     */
    private void initializeMcpServer() throws Exception {
        // Send initialize request
        String initializeRequest = """
        {
            "jsonrpc": "2.0",
            "id": 1,
            "method": "initialize",
            "params": {
                "protocolVersion": "2025-03-26",
                "capabilities": {
                    "tools": {},
                    "resources": {}
                },
                "clientInfo": {
                    "name": "TestClient",
                    "version": "1.0.0"
                }
            }
        }
        """;

        HttpResponse<String> initResponse = sendMcpRequest(initializeRequest);
        assertEquals(200, initResponse.statusCode());

        // Send initialized notification
        String initializedNotification = """
        {
            "jsonrpc": "2.0",
            "method": "notifications/initialized"
        }
        """;

        HttpResponse<String> notificationResponse = sendMcpRequest(initializedNotification);
        assertEquals(204, notificationResponse.statusCode());
    }
}