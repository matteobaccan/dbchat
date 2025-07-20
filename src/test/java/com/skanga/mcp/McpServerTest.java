package com.skanga.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
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
        initParams.put("protocolVersion", "2025-03-26");
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

    @Disabled
    @Test
    void testStartHttpMode_PortAlreadyInUse() {
        // This test would require more complex mocking of HttpServer creation
        // For now, we'll test the error handling logic indirectly
        
        // Create a new McpServer instance for this test
        ConfigParams realConfigParams = new ConfigParams(
            "jdbc:h2:mem:test", "sa", "", "org.h2.Driver",
            10, 30000, 30, true, 10000, 10000, 600000, 1800000, 60000
        );
        
        McpServer realMcpServer = new McpServer(realConfigParams);
        
        // Try to start on a port that's likely to be in use (like port 80)
        // or create two servers on the same port
        assertThrows(IOException.class, () -> {
            realMcpServer.startHttpMode(0); // Port 0 might cause issues
        });
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
}
