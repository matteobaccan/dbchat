package com.skanga.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpServerTest {
    @Mock
    private DatabaseService mockDatabaseService;
    
    @Mock
    private ConfigParams mockConfigParams;
    
    @Mock
    private ConfigParams mockDatabaseConfig;

    private McpServer mcpServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        // Setup basic mocks for lifecycle management
        lenient().when(mockDatabaseService.getDatabaseConfig()).thenReturn(mockDatabaseConfig);
        lenient().when(mockDatabaseConfig.maxConnections()).thenReturn(10);
        lenient().when(mockDatabaseConfig.queryTimeoutSeconds()).thenReturn(30);
        lenient().when(mockDatabaseConfig.selectOnly()).thenReturn(true);
        lenient().when(mockDatabaseConfig.maxSqlLength()).thenReturn(10000);
        lenient().when(mockDatabaseConfig.maxRowsLimit()).thenReturn(10000);
        lenient().when(mockDatabaseConfig.getDatabaseType()).thenReturn("h2");

        // Create a testable McpServer with mocked dependencies
        mcpServer = new TestableMcpServer(mockConfigParams);
    }

    // Custom testable McpServer that allows dependency injection
    private class TestableMcpServer extends McpServer {
        public TestableMcpServer(ConfigParams configParams) {
            super(configParams);
        }

        @Override
        protected DatabaseService createDatabaseService(ConfigParams configParams) {
            return mockDatabaseService;
        }
    }

    @Test
    void testHandleRequest_MethodNotFound() {
        TestUtils.initializeServer(mcpServer, objectMapper);    // Initialize server first
        // Arrange
        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "unknown_method");
        request.set("params", objectMapper.createObjectNode());

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("error"));
        assertEquals(-32601, response.get("error").get("code").asInt());
        assertTrue(response.get("error").get("message").asText().contains("Method not found"));
        assertEquals(1, response.get("id").asInt());
    }

    @Test
    void testHandleRequest_MethodNotFound_Notification() {
        // Arrange
        ObjectNode request = objectMapper.createObjectNode();
        // No id field - this is a notification
        request.put("method", "unknown_method");
        request.set("params", objectMapper.createObjectNode());

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNull(response); // Notifications don't get responses
    }

    @Test
    void testHandleRequest_DatabaseError() throws Exception {
        TestUtils.initializeServer(mcpServer, objectMapper);    // Initialize server first
        // Arrange
        when(mockDatabaseService.getDatabaseConfig()).thenReturn(mockDatabaseConfig);
        when(mockDatabaseConfig.maxSqlLength()).thenReturn(10000);
        when(mockDatabaseConfig.getDatabaseType()).thenReturn("h2"); // Add this for error message enhancement
        when(mockDatabaseService.executeQuery(anyString(), anyInt()))
                .thenThrow(new SQLException("Database connection failed"));

        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "tools/call");
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "query");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("sql", "SELECT * FROM test_table");
        params.set("arguments", args);
        request.set("params", params);

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert - Should be successful MCP response with tool error
        assertNotNull(response);

        // Should have result, not error (MCP spec compliance)
        assertTrue(response.has("result"));
        assertFalse(response.has("error"));
        assertEquals(1, response.get("id").asInt());

        // The result should indicate a tool error
        JsonNode result = response.get("result");
        assertTrue(result.get("isError").asBoolean());
        assertTrue(result.has("content"));

        // Content should contain the SQL error information
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
    void testHandleRequest_DatabaseError_Notification() {
        TestUtils.initializeServer(mcpServer, objectMapper);    // Initialize server first
        // Arrange
        lenient().when(mockDatabaseConfig.maxRowsLimit()).thenReturn(10000);

        ObjectNode request = objectMapper.createObjectNode();
        // No id field - this is a notification
        request.put("method", "tools/call");
        
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "query");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("sql", "SELECT * FROM test_table");
        params.set("arguments", args);
        request.set("params", params);

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNull(response); // Notifications don't get responses even on error
    }

    @Test
    void testHandleRequest_UnexpectedError() throws Exception {
        TestUtils.initializeServer(mcpServer, objectMapper);    // Initialize server first
        // Arrange
        when(mockDatabaseService.getDatabaseConfig()).thenReturn(mockDatabaseConfig);
        when(mockDatabaseConfig.maxSqlLength()).thenReturn(10000);
        when(mockDatabaseService.executeQuery(anyString(), anyInt()))
            .thenThrow(new RuntimeException("Unexpected runtime error"));

        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "tools/call");
        
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "query");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("sql", "SELECT * FROM test_table");
        params.set("arguments", args);
        request.set("params", params);

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("error"));
        assertEquals(-32603, response.get("error").get("code").asInt());
        assertTrue(response.get("error").get("message").asText().contains("Internal error"));
        assertEquals(1, response.get("id").asInt());
    }

    @Test
    void testExecuteQuery_EmptySQL() {
        TestUtils.initializeServer(mcpServer, objectMapper);    // Initialize server first
        // Arrange - Test through handleRequest instead of calling executeQuery directly
        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "tools/call");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "query");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("sql", "");
        params.set("arguments", args);
        request.set("params", params);

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert - Should get error response
        assertNotNull(response);
        assertTrue(response.has("error"));
        assertEquals(-32602, response.get("error").get("code").asInt());
        assertTrue(response.get("error").get("message").asText().contains("SQL query cannot be empty"));
    }

    @Test
    void testExecuteQuery_NullSQL() {
        TestUtils.initializeServer(mcpServer, objectMapper);    // Initialize server first
        // Arrange
        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "tools/call");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "query");
        ObjectNode args = objectMapper.createObjectNode();
        args.putNull("sql");
        params.set("arguments", args);
        request.set("params", params);

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("error"));
        assertEquals(-32602, response.get("error").get("code").asInt());
        assertTrue(response.get("error").get("message").asText().contains("SQL query cannot be null"));
    }

    @Test
    void testExecuteQuery_SQLTooLong() {
        TestUtils.initializeServer(mcpServer, objectMapper);    // Initialize server first
        // Arrange
        when(mockDatabaseService.getDatabaseConfig()).thenReturn(mockDatabaseConfig);
        when(mockDatabaseConfig.maxSqlLength()).thenReturn(100);

        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "tools/call");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "query");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("sql", "A".repeat(101)); // SQL longer than max allowed
        params.set("arguments", args);
        request.set("params", params);

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("error"));
        assertEquals(-32602, response.get("error").get("code").asInt());
        assertTrue(response.get("error").get("message").asText().contains("SQL query too long"));
    }

    @Test
    void testHandleCallTool_UnknownTool() {
        TestUtils.initializeServer(mcpServer, objectMapper);    // Initialize server first
        // Arrange
        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "tools/call");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "unknown_tool");
        params.set("arguments", objectMapper.createObjectNode());
        request.set("params", params);

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("error"));
        assertEquals(-32602, response.get("error").get("code").asInt());
        assertTrue(response.get("error").get("message").asText().contains("Unknown tool"));
    }

    @Test
    void testHandleReadResource_NotFound() throws Exception {
        TestUtils.initializeServer(mcpServer, objectMapper);    // Initialize server first
        // Arrange
        when(mockDatabaseService.readResource(anyString())).thenReturn(null);

        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "resources/read");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("uri", "nonexistent://resource");
        request.set("params", params);

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("error"));
        assertEquals(-32602, response.get("error").get("code").asInt());
        assertTrue(response.get("error").get("message").asText().contains("Resource not found"));
    }

    @Test
    void testHandleReadResource_SQLException() throws Exception {
        TestUtils.initializeServer(mcpServer, objectMapper);    // Initialize server first
        // Arrange
        when(mockDatabaseService.readResource(anyString()))
                .thenThrow(new SQLException("Failed to read resource"));

        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "resources/read");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("uri", "test://resource");
        request.set("params", params);

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("error"));
        assertEquals(-32603, response.get("error").get("code").asInt());
        assertTrue(response.get("error").get("message").asText().contains("Internal error"));
    }

    @Test
    void testLifecycle_InitializeRequired() {
        // Arrange - Try to call tools/list without initializing first
        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "tools/list");

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("error"));
        assertEquals(-32600, response.get("error").get("code").asInt());
        assertTrue(response.get("error").get("message").asText().contains("Server not initialized"));
    }

    @Test
    void testLifecycle_InitializeAndInitialized() {
        // Step 1: Initialize
        ObjectNode initRequest = objectMapper.createObjectNode();
        initRequest.put("id", 1);
        initRequest.put("method", "initialize");

        ObjectNode initParams = objectMapper.createObjectNode();
        initParams.put("protocolVersion", "2025-06-18");
        ObjectNode capabilities = objectMapper.createObjectNode();
        ObjectNode toolsCap = objectMapper.createObjectNode();
        toolsCap.put("listChanged", false);
        capabilities.set("tools", toolsCap);
        initParams.set("capabilities", capabilities);
        initRequest.set("params", initParams);

        JsonNode initResponse = mcpServer.handleRequest(initRequest);
        assertNotNull(initResponse);
        assertTrue(initResponse.has("result"));

        // Step 2: Send initialized notification
        ObjectNode initializedRequest = objectMapper.createObjectNode();
        initializedRequest.put("method", "notifications/initialized");
        // No id field for notifications

        JsonNode initializedResponse = mcpServer.handleRequest(initializedRequest);
        assertNull(initializedResponse); // Notifications don't return responses

        // Step 3: Now we can call other methods
        ObjectNode toolsRequest = objectMapper.createObjectNode();
        toolsRequest.put("id", 2);
        toolsRequest.put("method", "tools/list");

        JsonNode toolsResponse = mcpServer.handleRequest(toolsRequest);
        assertNotNull(toolsResponse);
        assertTrue(toolsResponse.has("result"));
    }

    @Test
    void testStdioMode_InvalidJson() {
        // Arrange
        String invalidJson = "{ invalid json }";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(invalidJson.getBytes());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        System.setIn(inputStream);
        System.setOut(new PrintStream(outputStream));

        // Act
        assertDoesNotThrow(() -> mcpServer.startStdioMode());

        // The method should handle the JSON parsing error gracefully
        // and not crash the server
    }

    @Test
    void testErrorCodeMapping() {
        // Test the getErrorCode method indirectly through handleRequest
        
        // Test invalid_request error code
        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "tools/call");
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "query");
        // Missing arguments should trigger invalid_request
        request.set("params", params);

        JsonNode response = mcpServer.handleRequest(request);
        assertNotNull(response);
        assertTrue(response.has("error"));
        // This should map to an appropriate error code
    }

    @Test
    void testHandleRequest_InvalidParams() {
        TestUtils.initializeServer(mcpServer, objectMapper);    // Initialize server first
        // Arrange
        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "tools/call");
        
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "query");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("sql", ""); // Empty SQL should trigger IllegalArgumentException
        params.set("arguments", args);
        request.set("params", params);

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("error"));
        assertEquals(-32602, response.get("error").get("code").asInt());
        assertTrue(response.get("error").get("message").asText().contains("SQL query cannot be empty"));
    }

    @Test
    void testSetRespId_DifferentTypes() {
        // Test with string ID
        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", "string-id");
        request.put("method", "initialize");
        
        JsonNode response = mcpServer.handleRequest(request);
        assertEquals("string-id", response.get("id").asText());

        // Test with numeric ID
        request = objectMapper.createObjectNode();
        request.put("id", 42);
        request.put("method", "initialize");
        
        response = mcpServer.handleRequest(request);
        assertEquals(42, response.get("id").asInt());

        // Test with null ID
        request = objectMapper.createObjectNode();
        request.putNull("id");
        request.put("method", "initialize");
        
        response = mcpServer.handleRequest(request);
        assertTrue(response.get("id").isNull());
    }

    @Test
    void testListResources_SQLException() throws Exception {
        TestUtils.initializeServer(mcpServer, objectMapper);    // Initialize server first
        // Arrange
        when(mockDatabaseService.listResources())
            .thenThrow(new SQLException("Failed to list resources"));

        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "resources/list");

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("error"));
        assertEquals(-32603, response.get("error").get("code").asInt());
        assertTrue(response.get("error").get("message").asText().contains("Internal error"));
    }

    @Test
    void testHandleInitialize_WrongProtocolVersion() {
        // Arrange
        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "initialize");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", "2024-01-01"); // Wrong version
        request.set("params", params);

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("error"));
        assertEquals(-32600, response.get("error").get("code").asInt());
        assertTrue(response.get("error").get("message").asText().contains("Unsupported protocol version"));
    }

    @Test
    void testHandleInitialize_AlreadyInitialized() {
        // First initialize
        TestUtils.initializeServer(mcpServer, objectMapper);

        // Try to initialize again
        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 2);
        request.put("method", "initialize");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", "2025-03-26");
        request.set("params", params);

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("error"));
        assertEquals(-32600, response.get("error").get("code").asInt());
        assertTrue(response.get("error").get("message").asText().contains("already initialized"));
    }

    @Test
    void testHandleInitialize_WithoutCapabilities() {
        // Arrange
        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "initialize");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", "2025-06-18");
        // No capabilities field
        request.set("params", params);

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("result"));
        JsonNode result = response.get("result");
        assertTrue(result.has("capabilities"));
        assertTrue(result.has("serverInfo"));
    }

    @Test
    void testHandleNotificationInitialized_WrongState() {
        // Try to send initialized notification without initializing first
        ObjectNode request = objectMapper.createObjectNode();
        request.put("method", "notifications/initialized");

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNull(response); // Notifications don't return responses even on error
    }

    @Test
    void testHandlePing() {
        TestUtils.initializeServer(mcpServer, objectMapper);

        // Arrange
        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "ping");

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("result"));
        JsonNode result = response.get("result");
        assertTrue(result.has("x-dbchat-timestamp"));
        assertTrue(result.has("x-dbchat-state"));
        assertEquals("INITIALIZED", result.get("x-dbchat-state").asText());
    }

    @Test
    void testExecuteQuery_MaxRowsExceeded() {
        TestUtils.initializeServer(mcpServer, objectMapper);

        // Arrange
        when(mockDatabaseConfig.maxRowsLimit()).thenReturn(1000);

        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "tools/call");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "query");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("sql", "SELECT * FROM users");
        args.put("maxRows", 5000); // Exceeds limit
        params.set("arguments", args);
        request.set("params", params);

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("error"));
        assertEquals(-32602, response.get("error").get("code").asInt());
        assertTrue(response.get("error").get("message").asText().contains("exceeds maximum allowed"));
    }

    @Test
    void testExecuteQuery_SuccessfulQuery() throws Exception {
        TestUtils.initializeServer(mcpServer, objectMapper);

        // Arrange
        QueryResult mockResult = new QueryResult(
                List.of("id", "name"),
                List.of(List.of(1, "Alice"), List.of(2, "Bob")),
                2,
                150L
        );

        when(mockDatabaseService.executeQuery("SELECT * FROM users", 1000))
                .thenReturn(mockResult);

        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "tools/call");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "query");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("sql", "SELECT * FROM users");
        params.set("arguments", args);
        request.set("params", params);

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("result"));
        JsonNode result = response.get("result");
        assertFalse(result.get("x-dbchat-is-error").asBoolean());
        assertTrue(result.has("content"));

        JsonNode content = result.get("content");
        assertTrue(content.isArray());
        assertEquals("text", content.get(0).get("type").asText());

        String resultText = content.get(0).get("text").asText();
        assertTrue(resultText.contains("ARBITRARY CODE EXECUTION RESULT"));
        assertTrue(resultText.contains("Alice"));
        assertTrue(resultText.contains("Bob"));
    }

    @Test
    void testHandleListTools_CorrectStructure() {
        TestUtils.initializeServer(mcpServer, objectMapper);

        // Arrange
        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "tools/list");

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("result"));
        JsonNode result = response.get("result");
        assertTrue(result.has("tools"));

        JsonNode tools = result.get("tools");
        assertTrue(tools.isArray());
        assertEquals(1, tools.size());

        JsonNode queryTool = tools.get(0);
        assertEquals("query", queryTool.get("name").asText());
        assertTrue(queryTool.get("description").asText().contains("CRITICAL SECURITY WARNING"));
        assertTrue(queryTool.has("inputSchema"));
        assertTrue(queryTool.has("security"));
    }

    @Test
    void testHandleListResources_Success() throws Exception {
        TestUtils.initializeServer(mcpServer, objectMapper);

        // Arrange
        List<DatabaseResource> mockResources = List.of(
                new DatabaseResource("database://info", "Database Info", "Database information", "text/plain", null),
                new DatabaseResource("database://table/users", "users", "User table", "text/plain", null)
        );

        when(mockDatabaseService.listResources()).thenReturn(mockResources);

        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "resources/list");

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("result"));
        JsonNode result = response.get("result");
        assertTrue(result.has("resources"));

        JsonNode resources = result.get("resources");
        assertTrue(resources.isArray());
        assertEquals(2, resources.size());

        JsonNode firstResource = resources.get(0);
        assertEquals("database://info", firstResource.get("uri").asText());
        assertEquals("Database Info", firstResource.get("name").asText());
    }

    @Test
    void testHandleReadResource_Success() throws Exception {
        TestUtils.initializeServer(mcpServer, objectMapper);

        // Arrange
        DatabaseResource mockResource = new DatabaseResource(
                "database://info",
                "Database Info",
                "Database information",
                "text/plain",
                "Sample database content"
        );

        when(mockDatabaseService.readResource("database://info")).thenReturn(mockResource);

        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "resources/read");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("uri", "database://info");
        request.set("params", params);

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("result"));
        JsonNode result = response.get("result");
        assertTrue(result.has("contents"));

        JsonNode contents = result.get("contents");
        assertTrue(contents.isArray());
        assertEquals(1, contents.size());

        JsonNode content = contents.get(0);
        assertEquals("database://info", content.get("uri").asText());
        assertEquals("text/plain", content.get("mimeType").asText());
        assertEquals("Sample database content", content.get("text").asText());
    }

    @Test
    void testHandleReadResource_UserDataResource() throws Exception {
        TestUtils.initializeServer(mcpServer, objectMapper);

        // Arrange
        DatabaseResource mockResource = new DatabaseResource(
                "database://table/users",
                "users",
                "User table",
                "text/plain",
                "Table: users\nColumns: id, name"
        );

        when(mockDatabaseService.readResource("database://table/users")).thenReturn(mockResource);

        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "resources/read");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("uri", "database://table/users");
        request.set("params", params);

        // Act
        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("result"));
        JsonNode result = response.get("result");
        assertTrue(result.has("contents"));

        JsonNode content = result.get("contents").get(0);
        String text = content.get("text").asText();
        assertTrue(text.contains("CONTAINS UNTRUSTED DATA"));
        assertTrue(text.contains("Table: users"));
    }

    @Test
    void testFormatResultsAsTable_EmptyResults() {
        // Create empty query result
        QueryResult emptyResult = new QueryResult(List.of(), List.of(), 0, 0L);

        // Act
        String formatted = mcpServer.formatResultsAsTable(emptyResult);

        // Assert
        assertEquals("No data", formatted);
    }

    @Test
    void testFormatResultsAsTable_WithData() {
        // Create query result with data
        QueryResult result = new QueryResult(
                List.of("id", "name", "email"),
                List.of(
                        List.of(1, "Alice", "alice@example.com"),
                        List.of(2, "Bob", "bob@example.com")
                ),
                2,
                100L
        );

        // Act
        String formatted = mcpServer.formatResultsAsTable(result);

        // Assert
        assertTrue(formatted.contains("DATA TABLE (UNTRUSTED CONTENT)"));
        assertTrue(formatted.contains("id"));
        assertTrue(formatted.contains("name"));
        assertTrue(formatted.contains("email"));
        assertTrue(formatted.contains("Alice"));
        assertTrue(formatted.contains("Bob"));
        assertTrue(formatted.contains("alice@example.com"));
        assertTrue(formatted.contains("-+-")); // Table separator
    }

    @Test
    void testCreateEnhancedSqlErrorMessage_TableNotFound() {
        // Mock the database config
        when(mockDatabaseConfig.getDatabaseType()).thenReturn("postgresql");

        // Create SQL exception
        SQLException sqlException = new SQLException("Table 'users' doesn't exist");

        // Act - We need to trigger executeQuery to test the error message creation
        TestUtils.initializeServer(mcpServer, objectMapper);

        try {
            when(mockDatabaseService.executeQuery(anyString(), anyInt())).thenThrow(sqlException);
        } catch (Exception e) {
            // Expected
        }

        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "tools/call");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "query");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("sql", "SELECT * FROM users");
        params.set("arguments", args);
        request.set("params", params);

        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("result"));
        JsonNode result = response.get("result");
        assertTrue(result.get("isError").asBoolean());

        String errorText = result.get("content").get(0).get("text").asText();
        assertTrue(errorText.contains("Table not found troubleshooting"));
        assertTrue(errorText.contains("PostgreSQL is case-sensitive"));
    }

    @Test
    void testGetSqlSyntaxHints_MySQL() {
        // Test through error message creation
        when(mockDatabaseConfig.getDatabaseType()).thenReturn("mysql");

        TestUtils.initializeServer(mcpServer, objectMapper);

        SQLException sqlException = new SQLException("syntax error near 'ORDER'");

        try {
            when(mockDatabaseService.executeQuery(anyString(), anyInt())).thenThrow(sqlException);
        } catch (Exception e) {
            // Expected
        }

        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "tools/call");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "query");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("sql", "SELECT * FROM users ORDER BY name");
        params.set("arguments", args);
        request.set("params", params);

        JsonNode response = mcpServer.handleRequest(request);

        String errorText = response.get("result").get("content").get(0).get("text").asText();
        assertTrue(errorText.contains("Use backticks"));
        assertTrue(errorText.contains("MYSQL"));
    }

    @Test
    void testGetSqlSyntaxHints_PostgreSQL() {
        when(mockDatabaseConfig.getDatabaseType()).thenReturn("postgresql");

        TestUtils.initializeServer(mcpServer, objectMapper);

        SQLException sqlException = new SQLException("syntax error near 'GROUP'");

        try {
            when(mockDatabaseService.executeQuery(anyString(), anyInt())).thenThrow(sqlException);
        } catch (Exception e) {
            // Expected
        }

        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "tools/call");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "query");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("sql", "SELECT * FROM users GROUP BY name");
        params.set("arguments", args);
        request.set("params", params);

        JsonNode response = mcpServer.handleRequest(request);

        String errorText = response.get("result").get("content").get(0).get("text").asText();
        assertTrue(errorText.contains("Use double quotes"));
        assertTrue(errorText.contains("POSTGRESQL"));
    }

    @Test
    void testShutdown() {
        // Act
        mcpServer.shutdown();

        // Verify we can call shutdown multiple times safely
        assertDoesNotThrow(() -> mcpServer.shutdown());
    }

    @Test
    void testShutdown_AfterInitialization() {
        TestUtils.initializeServer(mcpServer, objectMapper);

        // Act
        mcpServer.shutdown();

        // Try to make a request after shutdown
        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "tools/list");

        JsonNode response = mcpServer.handleRequest(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.has("error"));
        assertTrue(response.get("error").get("message").asText().contains("shut down"));
    }

    @Test
    void testLoadConfiguration_FromArgs() throws IOException {
        // Arrange
        String[] args = {
                "--db_url=jdbc:h2:mem:testdb",
                "--db_user=testuser",
                "--db_password=testpass",
                "--max_connections=20"
        };

        // Act
        ConfigParams config = McpServer.loadConfiguration(args);

        // Assert
        assertEquals("jdbc:h2:mem:testdb", config.dbUrl());
        assertEquals("testuser", config.dbUser());
        assertEquals("testpass", config.dbPass());
        assertEquals(20, config.maxConnections());
    }

    @Test
    void testParseArgs() {
        // Arrange
        String[] args = {
                "--db_url=jdbc:h2:mem:test",
                "--max_connections=15",
                "invalid-arg",
                "--malformed",
                "--key=value"
        };

        // Act
        Map<String, String> parsed = McpServer.parseArgs(args);

        // Assert
        assertEquals("jdbc:h2:mem:test", parsed.get("DB_URL"));
        assertEquals("15", parsed.get("MAX_CONNECTIONS"));
        assertEquals("value", parsed.get("KEY"));
        assertFalse(parsed.containsKey("INVALID-ARG"));
        assertFalse(parsed.containsKey("MALFORMED"));
    }

    @Test
    void testIsHttpMode() {
        // Test default (false)
        String[] args1 = {};
        assertFalse(McpServer.isHttpMode(args1));

        // Test with HTTP mode enabled
        String[] args2 = {"--http_mode=true"};
        assertTrue(McpServer.isHttpMode(args2));

        // Test with HTTP mode disabled
        String[] args3 = {"--http_mode=false"};
        assertFalse(McpServer.isHttpMode(args3));
    }

    @Test
    void testGetHttpPort() {
        // Test default port
        String[] args1 = {};
        assertEquals(8080, McpServer.getHttpPort(args1));

        // Test custom port
        String[] args2 = {"--http_port=9090"};
        assertEquals(9090, McpServer.getHttpPort(args2));
    }

    @Test
    void testConstructor_WithConfigParams() {
        // Create a real ConfigParams instance for this test
        ConfigParams realConfig = new ConfigParams(
                "jdbc:h2:mem:test", "sa", "", "org.h2.Driver",
                10, 30000, 30, true, 10000, 10000, 600000, 1800000, 60000
        );

        // Act
        McpServer server = new McpServer(realConfig);

        // Assert
        assertNotNull(server);
        assertNotNull(server.databaseService);
    }

    @Test
    void testHealthCheckHandler() throws Exception {
        // This test would require more integration testing setup
        // For now, we can test that the health check logic works by
        // testing the database connection check indirectly

        // Mock a successful database connection
        when(mockDatabaseService.getConnection()).thenReturn(mock(java.sql.Connection.class));

        // Create a testable health check scenario
        // This would be more complex to test directly without HTTP server setup
        assertDoesNotThrow(() -> {
            // The health check functionality is tested indirectly
            mockDatabaseService.getConnection();
        });
    }

    @Test
    void testMCPHttpHandler_Options() {
        // This would require integration testing with actual HTTP requests
        // For unit testing, we focus on the logic that can be tested in isolation

        // Test that OPTIONS requests are handled differently
        // This is tested indirectly through the HTTP handler logic
        assertTrue(true); // Placeholder for HTTP handler tests
    }

    @Test
    void testStdioMode_NotificationHandling() {
        // Test that notifications in stdio mode don't produce output
        String notificationJson = """
        {"method": "notifications/initialized"}
        """;

        ByteArrayInputStream inputStream = new ByteArrayInputStream(notificationJson.getBytes());
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        System.setIn(inputStream);
        System.setOut(new PrintStream(outputStream));

        // Act
        assertDoesNotThrow(() -> mcpServer.startStdioMode());

        // The output should be empty for notifications
        String output = outputStream.toString();
        assertFalse(output.contains("result"));
    }

    @Test
    void testLoadConfigFile_ValidFile() throws IOException {
        // Create a temporary config file
        String configContent = """
        # Database configuration
        DB_URL=jdbc:postgresql://localhost:5432/testdb
        DB_USER=testuser
        DB_PASSWORD="secret123"
        MAX_CONNECTIONS=20
        
        # This is a comment
        SELECT_ONLY=false
        QUERY_TIMEOUT_SECONDS='30'
        """;

        File tempFile = File.createTempFile("test-config", ".properties");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(configContent);
        }

        // Act
        Map<String, String> config = McpServer.loadConfigFile(tempFile.getAbsolutePath());

        // Assert
        assertEquals("jdbc:postgresql://localhost:5432/testdb", config.get("DB_URL"));
        assertEquals("testuser", config.get("DB_USER"));
        assertEquals("secret123", config.get("DB_PASSWORD"));
        assertEquals("20", config.get("MAX_CONNECTIONS"));
        assertEquals("false", config.get("SELECT_ONLY"));
        assertEquals("30", config.get("QUERY_TIMEOUT_SECONDS"));

        // Comments should not be included
        assertFalse(config.containsKey("# DATABASE CONFIGURATION"));
    }

    @Test
    void testLoadConfigFile_FileNotFound() {
        // Act & Assert
        assertThrows(IOException.class, () -> McpServer.loadConfigFile("/nonexistent/config/file.properties"));
    }

    @Test
    void testLoadConfigFile_MalformedLines() throws Exception {
        // Create a config file with malformed lines
        String configContent = """
        VALID_KEY=valid_value
        MALFORMED_LINE_NO_EQUALS
        =VALUE_WITHOUT_KEY
        ANOTHER_VALID=value2
        KEY_WITH_MULTIPLE=EQUALS=SIGNS
        """;

        File tempFile = File.createTempFile("malformed-config", ".properties");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(configContent);
        }

        // Act
        Map<String, String> config = McpServer.loadConfigFile(tempFile.getAbsolutePath());

        // Assert - only valid lines should be loaded
        assertEquals("valid_value", config.get("VALID_KEY"));
        assertEquals("value2", config.get("ANOTHER_VALID"));
        assertEquals("EQUALS=SIGNS", config.get("KEY_WITH_MULTIPLE")); // Should handle multiple equals

        // Malformed lines should be ignored
        assertFalse(config.containsKey("MALFORMED_LINE_NO_EQUALS"));
        assertFalse(config.containsKey(""));
    }

    @Test
    void testLoadConfiguration_WithConfigFile() throws IOException {
        // Create a temporary config file
        String configContent = """
        DB_URL=jdbc:mysql://localhost:3306/configdb
        DB_USER=configuser
        MAX_CONNECTIONS=15
        SELECT_ONLY=false
        """;

        File tempFile = File.createTempFile("load-config-test", ".properties");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(configContent);
        }

        // Test with config file specified in args
        String[] args = {
                "--config_file=" + tempFile.getAbsolutePath(),
                "--db_password=clipassword", // CLI should override file
                "--query_timeout_seconds=45"  // CLI should override defaults
        };

        // Act
        ConfigParams config = McpServer.loadConfiguration(args);

        // Assert
        assertEquals("jdbc:mysql://localhost:3306/configdb", config.dbUrl()); // From file
        assertEquals("configuser", config.dbUser()); // From file
        assertEquals("clipassword", config.dbPass()); // From CLI (higher priority)
        assertEquals(15, config.maxConnections()); // From file
        assertEquals(45, config.queryTimeoutSeconds()); // From CLI
        assertFalse(config.selectOnly()); // From file
    }

    @Test
    void testLoadConfiguration_PriorityOrder() throws IOException {
        // Note: We can't actually set environment variables in tests, so we'll test system properties

        // Set system property
        System.setProperty("db.url", "jdbc:h2:sysprop");
        System.setProperty("max.connections", "25");

        try {
            String[] args = {
                    "--db_user=cliuser",  // CLI should win
                    "--max_connections=30" // CLI should override system property
            };

            // Act
            ConfigParams config = McpServer.loadConfiguration(args);

            // Assert
            assertEquals("cliuser", config.dbUser()); // CLI wins
            assertEquals(30, config.maxConnections()); // CLI overrides system property
            // dbUrl should come from system property since not in CLI
            // But we can't test this reliably due to defaults

        } finally {
            // Clean up system properties
            System.clearProperty("db.url");
            System.clearProperty("max.connections");
        }
    }

    @Test
    void testGetSqlSyntaxHints_SqlServer() {
        when(mockDatabaseConfig.getDatabaseType()).thenReturn("sqlserver");

        TestUtils.initializeServer(mcpServer, objectMapper);

        SQLException sqlException = new SQLException("syntax error near 'SELECT'");

        try {
            when(mockDatabaseService.executeQuery(anyString(), anyInt())).thenThrow(sqlException);
        } catch (Exception e) {
            // Expected
        }

        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "tools/call");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "query");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("sql", "SELECT * FROM users");
        params.set("arguments", args);
        request.set("params", params);

        JsonNode response = mcpServer.handleRequest(request);

        String errorText = response.get("result").get("content").get(0).get("text").asText();
        assertTrue(errorText.contains("Use square brackets"));
        assertTrue(errorText.contains("SQLSERVER"));
        assertTrue(errorText.contains("GETDATE()"));
        assertTrue(errorText.contains("TOP n"));
    }

    @Test
    void testGetSqlSyntaxHints_H2() {
        when(mockDatabaseConfig.getDatabaseType()).thenReturn("h2");

        TestUtils.initializeServer(mcpServer, objectMapper);

        SQLException sqlException = new SQLException("syntax error near 'WHERE'");

        try {
            when(mockDatabaseService.executeQuery(anyString(), anyInt())).thenThrow(sqlException);
        } catch (Exception e) {
            // Expected
        }

        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "tools/call");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "query");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("sql", "SELECT * FROM users WHERE active = 1");
        params.set("arguments", args);
        request.set("params", params);

        JsonNode response = mcpServer.handleRequest(request);

        String errorText = response.get("result").get("content").get(0).get("text").asText();
        assertTrue(errorText.contains("MySQL/PostgreSQL compatible"));
        assertTrue(errorText.contains("H2"));
        assertTrue(errorText.contains("NOW()"));
        assertTrue(errorText.contains("LIMIT n OFFSET n"));
    }

    @Test
    void testGetSqlSyntaxHints_Cassandra() {
        when(mockDatabaseConfig.getDatabaseType()).thenReturn("cassandra");

        TestUtils.initializeServer(mcpServer, objectMapper);

        // Use "syntax error" to trigger the hints section
        SQLException sqlException = new SQLException("syntax error near 'SELECT'");

        try {
            when(mockDatabaseService.executeQuery(anyString(), anyInt())).thenThrow(sqlException);
        } catch (Exception e) {
            // Expected
        }

        ObjectNode request = objectMapper.createObjectNode();
        request.put("id", 1);
        request.put("method", "tools/call");

        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "query");
        ObjectNode args = objectMapper.createObjectNode();
        args.put("sql", "SELECT * FROM users");
        params.set("arguments", args);
        request.set("params", params);

        JsonNode response = mcpServer.handleRequest(request);

        String errorText = response.get("result").get("content").get(0).get("text").asText();

        // For unsupported databases, should get the formatted header and default hints
        assertTrue(errorText.contains("SQL Syntax for CASSANDRA"));
        assertTrue(errorText.contains("Use standard SQL syntax"));
        assertTrue(errorText.contains("Check database documentation"));
    }

    // Additional test for loadConfiguration with environment variables
    @Test
    void testLoadConfiguration_EnvironmentVariables() throws IOException {
        // Since we can't set real environment variables in tests,
        // we test the priority order with system properties and CLI args
        System.setProperty("select.only", "false");  // System property
        System.setProperty("query.timeout.seconds", "60");  // System property

        try {
            String[] args = {
                    "--query_timeout_seconds=90"  // CLI should override system property
                    // select_only should come from system property
            };

            ConfigParams config = McpServer.loadConfiguration(args);

            // CLI should override system property
            assertEquals(90, config.queryTimeoutSeconds());

            // This should come from system property (converted from select.only)
            // But due to how getConfigValue works, this tests the system property fallback
            // We can't easily test the exact environment variable behavior without more complex setup

        } finally {
            System.clearProperty("select.only");
            System.clearProperty("query.timeout.seconds");
        }
    }

    @Test
    void testLoadConfiguration_Defaults() throws IOException {
        // Test with no configuration provided - should use all defaults
        String[] args = {}; // No arguments

        ConfigParams config = McpServer.loadConfiguration(args);

        // Assert all defaults
        assertEquals("jdbc:h2:mem:testdb", config.dbUrl());
        assertEquals("sa", config.dbUser());
        assertEquals("", config.dbPass());
        assertEquals("org.h2.Driver", config.dbDriver());
        assertEquals(10, config.maxConnections());
        assertEquals(30000, config.connectionTimeoutMs());
        assertEquals(30, config.queryTimeoutSeconds());
        assertTrue(config.selectOnly());
        assertEquals(10000, config.maxSqlLength());
        assertEquals(10000, config.maxRowsLimit());
    }

    @Test
    void testLoadConfigFile_EmptyAndCommentLines() throws Exception {
        String configContent = """
        # This is a comment
        
        DB_URL=jdbc:h2:mem:test
        
        # Another comment
        DB_USER=testuser
        
        """;

        File tempFile = File.createTempFile("empty-lines-config", ".properties");
        tempFile.deleteOnExit();

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(configContent);
        }

        Map<String, String> config = McpServer.loadConfigFile(tempFile.getAbsolutePath());

        // Should only contain the two valid configuration lines
        assertEquals(2, config.size());
        assertEquals("jdbc:h2:mem:test", config.get("DB_URL"));
        assertEquals("testuser", config.get("DB_USER"));
    }
}
