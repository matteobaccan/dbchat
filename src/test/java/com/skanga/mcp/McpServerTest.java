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
        // Arrange
        when(mockDatabaseService.getDatabaseConfig()).thenReturn(mockDatabaseConfig);
        when(mockDatabaseConfig.maxSqlLength()).thenReturn(10000);
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

        // Assert
        assertNotNull(response);
        assertTrue(response.has("error"));
        assertEquals(-32000, response.get("error").get("code").asInt());
        assertTrue(response.get("error").get("message").asText().contains("Database error"));
        assertEquals(1, response.get("id").asInt());
    }

    @Test
    void testHandleRequest_DatabaseError_Notification() throws Exception {
        // Arrange
        when(mockDatabaseService.getDatabaseConfig()).thenReturn(mockDatabaseConfig);
        lenient().when(mockDatabaseConfig.maxRowsLimit()).thenReturn(10000);
        when(mockDatabaseConfig.maxSqlLength()).thenReturn(10000);
        when(mockDatabaseService.executeQuery(anyString(), anyInt()))
            .thenThrow(new SQLException("Database connection failed"));

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
        // Arrange
        ObjectNode args = objectMapper.createObjectNode();
        args.put("sql", "");
        args.put("maxRows", 1000);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> mcpServer.executeQuery(args));
    }

    @Test
    void testExecuteQuery_NullSQL() {
        // Arrange
        ObjectNode args = objectMapper.createObjectNode();
        args.putNull("sql");
        args.put("maxRows", 1000);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> mcpServer.executeQuery(args));
    }

    @Test
    void testExecuteQuery_SQLTooLong() {
        // Arrange
        when(mockDatabaseService.getDatabaseConfig()).thenReturn(mockDatabaseConfig);
        when(mockDatabaseConfig.maxSqlLength()).thenReturn(100);

        ObjectNode args = objectMapper.createObjectNode();
        args.put("sql", "A".repeat(101)); // SQL longer than max allowed
        args.put("maxRows", 1000);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> mcpServer.executeQuery(args));
    }

    @Test
    void testHandleCallTool_UnknownTool() {
        // Arrange
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", "unknown_tool");
        params.set("arguments", objectMapper.createObjectNode());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> mcpServer.handleCallTool(params));
    }

    @Test
    void testHandleReadResource_NotFound() throws Exception {
        // Arrange
        when(mockDatabaseService.readResource(anyString())).thenReturn(null);

        ObjectNode params = objectMapper.createObjectNode();
        params.put("uri", "nonexistent://resource");

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> mcpServer.handleReadResource(params));
    }

    @Test
    void testHandleReadResource_SQLException() throws Exception {
        // Arrange
        when(mockDatabaseService.readResource(anyString()))
            .thenThrow(new SQLException("Failed to read resource"));

        ObjectNode params = objectMapper.createObjectNode();
        params.put("uri", "test://resource");

        // Act & Assert
        assertThrows(SQLException.class, () -> mcpServer.handleReadResource(params));
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
        assertEquals(-32600, response.get("error").get("code").asInt());
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
        assertEquals(-32000, response.get("error").get("code").asInt());
        assertTrue(response.get("error").get("message").asText().contains("Database error"));
    }
}
