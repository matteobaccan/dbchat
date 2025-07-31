package com.skanga.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skanga.mcp.config.ConfigParams;
import com.skanga.mcp.db.DatabaseService;
import com.skanga.mcp.db.QueryResult;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Additional edge case tests for McpServer exception handling
 */
class McpServerEdgeCaseTest {
    @Test
    void testFormatResultsAsTable_EmptyResult() {
        QueryResult emptyResult = new QueryResult(Collections.emptyList(), Collections.emptyList(), 0, 100L);
        McpServer server = new McpServer(createTestConfig());
        String formatted = server.formatResultsAsTable(emptyResult);
        assertEquals("No data", formatted);
    }

    @Test
    void testFormatResultsAsTable_NullValues() {
        List<String> columns = Arrays.asList("id", "name", "value");
        List<List<Object>> rows = Arrays.asList(
                Arrays.asList(1, "test", null),
                Arrays.asList(null, null, "value"),
                Arrays.asList(2, "", null)
        );

        QueryResult result = new QueryResult(columns, rows, 3, 150L);
        McpServer server = new McpServer(createTestConfig());
        String formatted = server.formatResultsAsTable(result);

        assertTrue(formatted.contains("NULL"));
        assertTrue(formatted.contains("test"));
        assertTrue(formatted.contains("value"));
    }

    @Test
    void testFormatResultsAsTable_LongValues() {
        String longValue = "A".repeat(100);
        List<String> columns = Arrays.asList("short", "long");
        List<List<Object>> rows = List.of(
                Arrays.asList("a", longValue)
        );

        QueryResult result = new QueryResult(columns, rows, 1, 50L);
        McpServer server = new McpServer(createTestConfig());
        String formatted = server.formatResultsAsTable(result);

        assertTrue(formatted.contains(longValue));
        assertTrue(formatted.contains("short"));
    }

    @Test
    void testErrorCodeMapping_AllCodes() {
        McpServer server = new McpServer(createTestConfig());

        // Test all error code mappings through createErrorResponse
        JsonNode response1 = server.createErrorResponse("invalid_request", "test", 1);
        assertEquals(-32600, response1.get("error").get("code").asInt());

        JsonNode response2 = server.createErrorResponse("method_not_found", "test", 1);
        assertEquals(-32601, response2.get("error").get("code").asInt());

        JsonNode response3 = server.createErrorResponse("invalid_params", "test", 1);
        assertEquals(-32602, response3.get("error").get("code").asInt());

        JsonNode response4 = server.createErrorResponse("internal_error", "test", 1);
        assertEquals(-32603, response4.get("error").get("code").asInt());

        JsonNode response5 = server.createErrorResponse("database_error", "test", 1);
        assertEquals(-32000, response5.get("error").get("code").asInt());

        JsonNode response6 = server.createErrorResponse("unknown_error", "test", 1);
        assertEquals(-32603, response6.get("error").get("code").asInt()); // Default to internal_error
    }

    @Test
    void testSetRespId_ComplexObjects() {
        McpServer server = new McpServer(createTestConfig());

        // Test with complex object as ID
        Map<String, Object> complexId = new HashMap<>();
        complexId.put("type", "complex");
        complexId.put("value", 123);

        JsonNode response = server.createErrorResponse("test_error", "test message", complexId);

        assertNotNull(response.get("id"));
        assertTrue(response.get("id").isObject());
        assertEquals("complex", response.get("id").get("type").asText());
        assertEquals(123, response.get("id").get("value").asInt());
    }

    @Test
    void testExecuteQuery_MaxRowsValidation() throws SQLException {
        DatabaseService mockService = mock(DatabaseService.class);
        ConfigParams mockConfig = mock(ConfigParams.class);

        List<String> columns = Arrays.asList("id", "name", "email");
        List<List<Object>> rows = Arrays.asList(
                Arrays.asList(1, "John Doe", "john@example.com"),
                Arrays.asList(2, "Jane Smith", "jane@example.com")
        );
        int rowCount = 2;
        long executionTime = 150L;

        // When
        QueryResult result = new QueryResult(columns, rows, rowCount, executionTime);

        when(mockConfig.maxRowsLimit()).thenReturn(1000);
        when(mockConfig.maxSqlLength()).thenReturn(10000);
        when(mockConfig.getDatabaseType()).thenReturn("h2");
        when(mockService.getDatabaseConfig()).thenReturn(mockConfig);
        when(mockService.executeSql("SELECT * FROM test", 500, null)).thenReturn(result);
        McpServer server = createMcpServer(mockService);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode args = mapper.createObjectNode();
        args.put("sql", "SELECT * FROM test");
        args.put("maxRows", 500); // Valid value

        // Should not throw for valid maxRows
        assertDoesNotThrow(() -> {
            try {
                server.execToolRunSql(args);
            } catch (SQLException e) {
                // Expected since we're using a mock
            }
        });
    }

    private ConfigParams createTestConfig() {
        return new ConfigParams(
                "jdbc:h2:mem:testdb", "sa", "", "org.h2.Driver",
                10, 30000, 30, true, 10000, 10000, 600000, 1800000, 60000
        );
    }

    private McpServer createMcpServer (DatabaseService mockService) {
        return new McpServer(mockService);
    }
}
