package com.skanga.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for HTTP mode with real database
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpIntegrationTest {
    
    private static final int TEST_PORT = 18081;
    private static final String BASE_URL = "http://localhost:" + TEST_PORT;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private static McpServer server;
    private static HttpClient httpClient;
    private static Thread serverThread;

    @BeforeAll
    static void setUpAll() throws InterruptedException {
        // Create test configuration with H2 in-memory database
        ConfigParams testConfig = ConfigParams.customConfig(
                "jdbc:h2:mem:integrationtest;DB_CLOSE_DELAY=-1",
                "sa",
                "",
                "org.h2.Driver",
                false, 10000
        );
        
        // Create server
        server = new McpServer(testConfig);
        
        // Setup HTTP client
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        
        startHttpServer();
    }
    
    @AfterAll
    static void tearDownAll() {
        if (serverThread != null && serverThread.isAlive()) {
            serverThread.interrupt();
            try {
                serverThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private static void startHttpServer() throws InterruptedException {
        CountDownLatch serverStartLatch = new CountDownLatch(1);
        
        serverThread = new Thread(() -> {
            try {
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
        
        assertTrue(serverStartLatch.await(5, TimeUnit.SECONDS), "Server thread failed to start");
        waitForServerReady();
    }
    
    private static void waitForServerReady() throws InterruptedException {
        int maxAttempts = 30;
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
                    return;
                }
            } catch (Exception e) {
                // Server not ready yet
            }
            
            Thread.sleep(200);
        }
        
        fail("HTTP server did not become ready within timeout");
    }
    
    private HttpResponse<String> sendMcpRequest(String jsonRequest) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/mcp"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonRequest))
                .build();
        
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
    
    @Test
    @Order(1)
    void testServerHealthAndReadiness() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
        
        HttpResponse<String> response = httpClient.send(request, 
                HttpResponse.BodyHandlers.ofString());
        
        assertEquals(200, response.statusCode());
        
        JsonNode healthResponse = objectMapper.readTree(response.body());
        assertEquals("healthy", healthResponse.get("status").asText());
        assertEquals("Database MCP Server", healthResponse.get("server").asText());
        assertEquals("connected", healthResponse.get("database").asText());
    }
    
    @Test
    @Order(2)
    void testFullMcpWorkflow() throws Exception {
        // 1. Initialize
        String initRequest = """
            {
                "jsonrpc": "2.0",
                "id": "init-1",
                "method": "initialize",
                "params": {
                    "protocolVersion": "2024-11-05",
                    "capabilities": {},
                    "clientInfo": {
                        "name": "integration-test",
                        "version": "1.0.0"
                    }
                }
            }
            """;
        
        HttpResponse<String> initResponse = sendMcpRequest(initRequest);
        assertEquals(200, initResponse.statusCode());
        
        JsonNode initJson = objectMapper.readTree(initResponse.body());
        assertEquals("init-1", initJson.get("id").asText());
        assertTrue(initJson.has("result"));
        
        // 2. List Tools
        String listToolsRequest = """
            {
                "jsonrpc": "2.0",
                "id": "tools-1",
                "method": "tools/list",
                "params": {}
            }
            """;
        
        HttpResponse<String> toolsResponse = sendMcpRequest(listToolsRequest);
        assertEquals(200, toolsResponse.statusCode());
        
        JsonNode toolsJson = objectMapper.readTree(toolsResponse.body());
        assertEquals("tools-1", toolsJson.get("id").asText());
        assertTrue(toolsJson.get("result").get("tools").isArray());
        
        // 3. Create a test table
        String createTableRequest = """
            {
                "jsonrpc": "2.0",
                "id": "create-1",
                "method": "tools/call",
                "params": {
                    "name": "query",
                    "arguments": {
                        "sql": "CREATE TABLE integration_test (id INT PRIMARY KEY, name VARCHAR(100), created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)",
                        "maxRows": 1000
                    }
                }
            }
            """;
        
        HttpResponse<String> createResponse = sendMcpRequest(createTableRequest);
        assertEquals(200, createResponse.statusCode());
        
        JsonNode createJson = objectMapper.readTree(createResponse.body());
        assertEquals("create-1", createJson.get("id").asText());
        assertTrue(createJson.has("result"));
        assertFalse(createJson.get("result").get("isError").asBoolean());
        
        // 4. Insert test data
        String insertRequest = """
            {
                "jsonrpc": "2.0",
                "id": "insert-1",
                "method": "tools/call",
                "params": {
                    "name": "query",
                    "arguments": {
                        "sql": "INSERT INTO integration_test (id, name) VALUES (1, 'Test User 1'), (2, 'Test User 2'), (3, 'Test User 3')",
                        "maxRows": 1000
                    }
                }
            }
            """;
        
        HttpResponse<String> insertResponse = sendMcpRequest(insertRequest);
        assertEquals(200, insertResponse.statusCode());
        
        JsonNode insertJson = objectMapper.readTree(insertResponse.body());
        assertEquals("insert-1", insertJson.get("id").asText());
        assertTrue(insertJson.has("result"));
        
        // 5. Query the data
        String queryRequest = """
            {
                "jsonrpc": "2.0",
                "id": "query-1",
                "method": "tools/call",
                "params": {
                    "name": "query",
                    "arguments": {
                        "sql": "SELECT id, name FROM integration_test ORDER BY id",
                        "maxRows": 100
                    }
                }
            }
            """;
        
        HttpResponse<String> queryResponse = sendMcpRequest(queryRequest);
        assertEquals(200, queryResponse.statusCode());
        
        JsonNode queryJson = objectMapper.readTree(queryResponse.body());
        assertEquals("query-1", queryJson.get("id").asText());
        assertTrue(queryJson.has("result"));
        
        JsonNode content = queryJson.get("result").get("content").get(0);
        String resultText = content.get("text").asText();
        assertTrue(resultText.contains("Test User 1"));
        assertTrue(resultText.contains("Test User 2"));
        assertTrue(resultText.contains("Test User 3"));
        
        // 6. List Resources
        String listResourcesRequest = """
            {
                "jsonrpc": "2.0",
                "id": "resources-1",
                "method": "resources/list",
                "params": {}
            }
            """;
        
        HttpResponse<String> resourcesResponse = sendMcpRequest(listResourcesRequest);
        assertEquals(200, resourcesResponse.statusCode());
        
        JsonNode resourcesJson = objectMapper.readTree(resourcesResponse.body());
        assertEquals("resources-1", resourcesJson.get("id").asText());
        assertTrue(resourcesJson.get("result").get("resources").isArray());
        
        // Check that our table appears in resources
        JsonNode resources = resourcesJson.get("result").get("resources");
        boolean foundTable = false;
        for (JsonNode resource : resources) {
            if (resource.get("uri").asText().contains("INTEGRATION_TEST")) {
                foundTable = true;
                break;
            }
        }
        assertTrue(foundTable, "Integration test table should appear in resources");
        
        // 7. Read table resource
        String readResourceRequest = """
            {
                "jsonrpc": "2.0",
                "id": "read-1",
                "method": "resources/read",
                "params": {
                    "uri": "database://table/INTEGRATION_TEST"
                }
            }
            """;
        
        HttpResponse<String> readResponse = sendMcpRequest(readResourceRequest);
        assertEquals(200, readResponse.statusCode());
        
        JsonNode readJson = objectMapper.readTree(readResponse.body());
        assertEquals("read-1", readJson.get("id").asText());
        assertTrue(readJson.has("result"));
        
        JsonNode contents = readJson.get("result").get("contents");
        assertTrue(contents.isArray());
        assertTrue(contents.size() > 0);
        
        String tableInfo = contents.get(0).get("text").asText();
        assertTrue(tableInfo.contains("INTEGRATION_TEST"));
        assertTrue(tableInfo.contains("Columns:"));
    }
    
    @Test
    @Order(3)
    void testErrorHandling() throws Exception {
        // Test invalid SQL
        String invalidSqlRequest = """
            {
                "jsonrpc": "2.0",
                "id": "error-1",
                "method": "tools/call",
                "params": {
                    "name": "query",
                    "arguments": {
                        "sql": "SELECT * FROM nonexistent_table",
                        "maxRows": 100
                    }
                }
            }
            """;
        
        HttpResponse<String> response = sendMcpRequest(invalidSqlRequest);
        assertEquals(200, response.statusCode());
        
        JsonNode jsonResponse = objectMapper.readTree(response.body());
        assertEquals("error-1", jsonResponse.get("id").asText());
        assertTrue(jsonResponse.has("error"));
        
        JsonNode error = jsonResponse.get("error");
        assertEquals(-32000, error.get("code").asInt()); // Database error
        assertTrue(error.get("message").asText().toLowerCase().contains("database error"));
        
        // Test invalid method
        String invalidMethodRequest = """
            {
                "jsonrpc": "2.0",
                "id": "error-2",
                "method": "nonexistent/method",
                "params": {}
            }
            """;
        
        response = sendMcpRequest(invalidMethodRequest);
        assertEquals(200, response.statusCode());
        
        jsonResponse = objectMapper.readTree(response.body());
        assertEquals("error-2", jsonResponse.get("id").asText());
        assertTrue(jsonResponse.has("error"));
        
        error = jsonResponse.get("error");
        assertEquals(-32601, error.get("code").asInt()); // Method not found
        assertTrue(error.get("message").asText().contains("Method not found"));
        
        // Test empty SQL
        String emptySqlRequest = """
            {
                "jsonrpc": "2.0",
                "id": "error-3",
                "method": "tools/call",
                "params": {
                    "name": "query",
                    "arguments": {
                        "sql": "",
                        "maxRows": 100
                    }
                }
            }
            """;
        
        response = sendMcpRequest(emptySqlRequest);
        assertEquals(200, response.statusCode());
        
        jsonResponse = objectMapper.readTree(response.body());
        assertEquals("error-3", jsonResponse.get("id").asText());
        assertTrue(jsonResponse.has("error"));
        
        error = jsonResponse.get("error");
        assertTrue(error.get("message").asText().contains("SQL query cannot be empty"));
    }
    
    @Test
    @Order(4)
    void testNotificationHandling() throws Exception {
        // Send notification (no id field) - should get 204 response
        String notificationRequest = """
            {
                "jsonrpc": "2.0",
                "method": "tools/list",
                "params": {}
            }
            """;
        
        HttpResponse<String> response = sendMcpRequest(notificationRequest);
        assertEquals(204, response.statusCode());
        assertTrue(response.body().isEmpty());
        
        // Send notification with error - should still get 204
        String errorNotificationRequest = """
            {
                "jsonrpc": "2.0",
                "method": "invalid/method",
                "params": {}
            }
            """;
        
        response = sendMcpRequest(errorNotificationRequest);
        assertEquals(204, response.statusCode());
        assertTrue(response.body().isEmpty());
    }
    
    @Test
    @Order(5)
    void testDifferentIdTypes() throws Exception {
        // Test with numeric ID
        String numericIdRequest = """
            {
                "jsonrpc": "2.0",
                "id": 42,
                "method": "tools/list",
                "params": {}
            }
            """;
        
        HttpResponse<String> response = sendMcpRequest(numericIdRequest);
        assertEquals(200, response.statusCode());
        
        JsonNode jsonResponse = objectMapper.readTree(response.body());
        assertEquals(42, jsonResponse.get("id").asInt());
        
        // Test with string ID
        String stringIdRequest = """
            {
                "jsonrpc": "2.0",
                "id": "my-string-id",
                "method": "tools/list",
                "params": {}
            }
            """;
        
        response = sendMcpRequest(stringIdRequest);
        assertEquals(200, response.statusCode());
        
        jsonResponse = objectMapper.readTree(response.body());
        assertEquals("my-string-id", jsonResponse.get("id").asText());
        
        // Test with null ID
        String nullIdRequest = """
            {
                "jsonrpc": "2.0",
                "id": null,
                "method": "tools/list",
                "params": {}
            }
            """;
        
        response = sendMcpRequest(nullIdRequest);
        assertEquals(200, response.statusCode());
        
        jsonResponse = objectMapper.readTree(response.body());
        assertTrue(jsonResponse.get("id").isNull());
        
        // Test with complex ID (object)
        String complexIdRequest = """
            {
                "jsonrpc": "2.0",
                "id": {"requestId": "complex", "timestamp": 123456789},
                "method": "tools/list",
                "params": {}
            }
            """;
        
        response = sendMcpRequest(complexIdRequest);
        assertEquals(200, response.statusCode());
        
        jsonResponse = objectMapper.readTree(response.body());
        JsonNode responseId = jsonResponse.get("id");
        assertTrue(responseId.isObject());
        assertEquals("complex", responseId.get("requestId").asText());
        assertEquals(123456789, responseId.get("timestamp").asLong());
    }
    
    @Test
    @Order(6)
    void testQueryWithMaxRows() throws Exception {
        // First, insert more test data
        String insertMoreDataRequest = """
            {
                "jsonrpc": "2.0",
                "id": "insert-more",
                "method": "tools/call",
                "params": {
                    "name": "query",
                    "arguments": {
                        "sql": "INSERT INTO integration_test (id, name) VALUES (4, 'User 4'), (5, 'User 5'), (6, 'User 6'), (7, 'User 7'), (8, 'User 8')",
                        "maxRows": 1000
                    }
                }
            }
            """;
        
        HttpResponse<String> insertResponse = sendMcpRequest(insertMoreDataRequest);
        assertEquals(200, insertResponse.statusCode());
        
        // Query with maxRows limit
        String limitedQueryRequest = """
            {
                "jsonrpc": "2.0",
                "id": "limited-query",
                "method": "tools/call",
                "params": {
                    "name": "query",
                    "arguments": {
                        "sql": "SELECT id, name FROM integration_test ORDER BY id",
                        "maxRows": 3
                    }
                }
            }
            """;
        
        HttpResponse<String> queryResponse = sendMcpRequest(limitedQueryRequest);
        assertEquals(200, queryResponse.statusCode());
        
        JsonNode queryJson = objectMapper.readTree(queryResponse.body());
        assertEquals("limited-query", queryJson.get("id").asText());
        assertTrue(queryJson.has("result"));
        
        JsonNode content = queryJson.get("result").get("content").get(0);
        String resultText = content.get("text").asText();
        
        // Should contain row count information
        assertTrue(resultText.contains("Rows returned: 3"));
        
        // Should contain the first 3 users
        assertTrue(resultText.contains("Test User 1"));
        assertTrue(resultText.contains("Test User 2"));
        assertTrue(resultText.contains("Test User 3"));
        
        // Should NOT contain users 4 and beyond (due to maxRows limit)
        assertFalse(resultText.contains("User 4"));
    }
    
    @Test
    @Order(7)
    void testInvalidResourceRead() throws Exception {
        String invalidResourceRequest = """
            {
                "jsonrpc": "2.0",
                "id": "invalid-resource",
                "method": "resources/read",
                "params": {
                    "uri": "database://table/NONEXISTENT_TABLE"
                }
            }
            """;
        
        HttpResponse<String> response = sendMcpRequest(invalidResourceRequest);
        assertEquals(200, response.statusCode());
        
        JsonNode jsonResponse = objectMapper.readTree(response.body());
        assertEquals("invalid-resource", jsonResponse.get("id").asText());
        assertTrue(jsonResponse.has("error"));
        
        JsonNode error = jsonResponse.get("error");
        assertTrue(error.get("message").asText().contains("Resource not found"));
    }
    
    @Test
    @Order(8)
    void testLongRunningQuery() throws Exception {
        // Test a query that takes some time (create and populate a larger table)
        String createLargeTableRequest = """
            {
                "jsonrpc": "2.0",
                "id": "create-large",
                "method": "tools/call",
                "params": {
                    "name": "query",
                    "arguments": {
                        "sql": "CREATE TABLE large_test AS SELECT x as id, 'Data ' || x as name FROM SYSTEM_RANGE(1, 1000)",
                        "maxRows": 1000
                    }
                }
            }
            """;
        
        long startTime = System.currentTimeMillis();
        HttpResponse<String> response = sendMcpRequest(createLargeTableRequest);
        long endTime = System.currentTimeMillis();
        
        assertEquals(200, response.statusCode());
        
        JsonNode jsonResponse = objectMapper.readTree(response.body());
        assertEquals("create-large", jsonResponse.get("id").asText());
        assertTrue(jsonResponse.has("result"));
        
        // Verify execution time is reported
        JsonNode content = jsonResponse.get("result").get("content").get(0);
        String resultText = content.get("text").asText();
        assertTrue(resultText.contains("Execution time:"));
        assertTrue(resultText.contains("ms"));
        
        // Verify the query actually took some measurable time
        assertTrue(endTime - startTime > 0);
    }
    
    @Test
    @Order(9)
    void testDatabaseInfoResource() throws Exception {
        String dbInfoRequest = """
            {
                "jsonrpc": "2.0",
                "id": "db-info",
                "method": "resources/read",
                "params": {
                    "uri": "database://info"
                }
            }
            """;
        
        HttpResponse<String> response = sendMcpRequest(dbInfoRequest);
        assertEquals(200, response.statusCode());
        
        JsonNode jsonResponse = objectMapper.readTree(response.body());
        assertEquals("db-info", jsonResponse.get("id").asText());
        assertTrue(jsonResponse.has("result"));
        
        JsonNode contents = jsonResponse.get("result").get("contents");
        assertTrue(contents.isArray());
        assertTrue(contents.size() > 0);
        
        String dbInfo = contents.get(0).get("text").asText();
        assertTrue(dbInfo.contains("Database Information"));
        assertTrue(dbInfo.contains("Product: H2"));
        assertTrue(dbInfo.contains("Driver:"));
        assertTrue(dbInfo.contains("URL:"));
        assertTrue(dbInfo.contains("Username:"));
    }
    
    @Test
    @Order(10)
    void testCleanupAndResourceVerification() throws Exception {
        // Drop the test tables
        String dropTableRequest = """
            {
                "jsonrpc": "2.0",
                "id": "cleanup",
                "method": "tools/call",
                "params": {
                    "name": "query",
                    "arguments": {
                        "sql": "DROP TABLE IF EXISTS integration_test; DROP TABLE IF EXISTS large_test",
                        "maxRows": 1000
                    }
                }
            }
            """;
        
        HttpResponse<String> response = sendMcpRequest(dropTableRequest);
        assertEquals(200, response.statusCode());
        
        JsonNode jsonResponse = objectMapper.readTree(response.body());
        assertEquals("cleanup", jsonResponse.get("id").asText());
        assertTrue(jsonResponse.has("result"));
        
        // Verify tables are gone by listing resources
        String listResourcesRequest = """
            {
                "jsonrpc": "2.0",
                "id": "verify-cleanup",
                "method": "resources/list",
                "params": {}
            }
            """;
        
        response = sendMcpRequest(listResourcesRequest);
        assertEquals(200, response.statusCode());
        
        jsonResponse = objectMapper.readTree(response.body());
        JsonNode resources = jsonResponse.get("result").get("resources");
        
        // Check that our test tables are no longer in the resources
        boolean foundIntegrationTest = false;
        boolean foundLargeTest = false;
        
        for (JsonNode resource : resources) {
            String uri = resource.get("uri").asText();
            if (uri.contains("INTEGRATION_TEST")) {
                foundIntegrationTest = true;
            }
            if (uri.contains("LARGE_TEST")) {
                foundLargeTest = true;
            }
        }
        
        assertFalse(foundIntegrationTest, "Integration test table should be removed");
        assertFalse(foundLargeTest, "Large test table should be removed");
    }
}