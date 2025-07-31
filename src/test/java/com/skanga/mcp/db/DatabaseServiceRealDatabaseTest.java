package com.skanga.mcp.db;

import com.skanga.mcp.config.ConfigParams;
import com.skanga.mcp.TestUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

import java.sql.SQLException;
import java.util.List;

/**
 * Tests using real H2 database (no mocking)
 * These tests verify actual database functionality
 */
class DatabaseServiceRealDatabaseTest {
    private ConfigParams config;
    private DatabaseService databaseService;

    @BeforeEach
    void setUp() throws SQLException {
        config = TestUtils.createTestH2Config();
        databaseService = new DatabaseService(config);
        TestUtils.setupTestDatabase(config);
    }

    @AfterEach
    void tearDown() {
        if (databaseService != null) {
            databaseService.close();
        }
        TestUtils.cleanupDatabase(config);
    }

    @Test
    @DisplayName("Should execute real SELECT query successfully")
    void shouldExecuteRealSelectQuerySuccessfully() throws SQLException {
        // Given
        String sql = "SELECT id, name FROM users ORDER BY id";
        int maxRows = 100;

        // When
        QueryResult result = databaseService.executeSql(sql, maxRows);

        // Then
        assertThat(result.allColumns()).containsExactly("ID", "NAME");
        assertThat(result.allRows()).hasSize(4); // From TestUtils.setupTestDatabase
        assertThat(result.rowCount()).isEqualTo(4);
        assertThat(result.executionTimeMs()).isGreaterThanOrEqualTo(0);
        
        // Check actual data
        assertThat(result.allRows().get(0).get(1)).isEqualTo("John Doe");
        assertThat(result.allRows().get(1).get(1)).isEqualTo("Jane Smith");
    }

    @Test
    @DisplayName("Should execute real UPDATE query successfully")
    void shouldExecuteRealUpdateQuerySuccessfully() throws SQLException {
        // Given
        String sql = "UPDATE users SET name = 'Updated Name' WHERE id = 1";

        // When
        QueryResult result = databaseService.executeSql(sql, 100);

        // Then
        assertThat(result.allColumns()).containsExactly("affected_rows");
        assertThat(result.allRows()).hasSize(1);
        assertThat(result.allRows().get(0)).containsExactly(1);
        assertThat(result.rowCount()).isEqualTo(1);
        
        // Verify the update actually happened
        QueryResult selectResult = databaseService.executeSql("SELECT name FROM users WHERE id = 1", 1);
        assertThat(selectResult.allRows().get(0).get(0)).isEqualTo("Updated Name");
    }

    @Test
    @DisplayName("Should handle real SQL error gracefully")
    void shouldHandleRealSQLErrorGracefully() {
        // Given
        String invalidSql = "SELECT * FROM non_existent_table";

        // When/Then
        assertThatThrownBy(() -> databaseService.executeSql(invalidSql, 100))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("not found"); // H2 specific error message
    }

    @Test
    @DisplayName("Should list real database resources")
    void shouldListRealDatabaseResources() throws SQLException {
        // When
        List<DatabaseResource> resources = databaseService.listResources();

        // Then
        assertThat(resources).isNotEmpty();
        
        // Should have database info
        boolean hasInfoResource = resources.stream()
            .anyMatch(r -> r.uri().equals("database://info"));
        assertThat(hasInfoResource).isTrue();
        
        // Should have users table
        boolean hasUsersTable = resources.stream()
            .anyMatch(r -> r.uri().equals("database://table/USERS"));
        assertThat(hasUsersTable).isTrue();
        
        // Should have orders table
        boolean hasOrdersTable = resources.stream()
            .anyMatch(r -> r.uri().equals("database://table/ORDERS"));
        assertThat(hasOrdersTable).isTrue();
        
        // Should have active_users view
        boolean hasActiveUsersView = resources.stream()
            .anyMatch(r -> r.uri().equals("database://table/ACTIVE_USERS"));
        assertThat(hasActiveUsersView).isTrue();
    }

    @Test
    @DisplayName("Should read real database info resource")
    void shouldReadRealDatabaseInfoResource() throws SQLException {
        // When
        DatabaseResource resource = databaseService.readResource("database://info");

        // Then
        assertThat(resource).isNotNull();
        assertThat(resource.uri()).isEqualTo("database://info");
        assertThat(resource.name()).isEqualTo("Database Information");
        
        String content = resource.content();
        assertThat(content).contains("Database Information");
        assertThat(content).contains("Product: H2");
        assertThat(content).contains("Driver: H2 JDBC Driver");
        assertThat(content).contains("Supported Features");
    }

    @Test
    @DisplayName("Should read real table resource")
    void shouldReadRealTableResource() throws SQLException {
        // When
        DatabaseResource resource = databaseService.readResource("database://table/USERS");

        // Then
        assertThat(resource).isNotNull();
        assertThat(resource.uri()).isEqualTo("database://table/USERS");
        assertThat(resource.name()).isEqualTo("USERS");
        
        String content = resource.content();
        assertThat(content).contains("Table: USERS");
        assertThat(content).contains("Columns:");
        assertThat(content).contains("ID");
        assertThat(content).contains("NAME");
        assertThat(content).contains("EMAIL");
        assertThat(content).contains("Primary Keys:");
    }

    @Test
    @DisplayName("Should handle max rows limit with real data")
    void shouldHandleMaxRowsLimitWithRealData() throws SQLException {
        // Given
        String sql = "SELECT * FROM users ORDER BY id";
        int maxRows = 2;

        // When
        QueryResult result = databaseService.executeSql(sql, maxRows);

        // Then
        assertThat(result.allRows()).hasSize(2); // Limited by maxRows
        assertThat(result.rowCount()).isEqualTo(2);
        
        // Should get first 2 users
        assertThat(result.allRows().get(0).get(0)).isEqualTo(1); // First user ID
        assertThat(result.allRows().get(1).get(0)).isEqualTo(2); // Second user ID
    }

    @Test
    @DisplayName("Should handle complex query with joins")
    void shouldHandleComplexQueryWithJoins() throws SQLException {
        // Given
        String complexSql = """
            SELECT u.name, u.email, COUNT(o.id) as order_count
            FROM users u
            LEFT JOIN orders o ON u.id = o.user_id
            WHERE u.is_active = TRUE
            GROUP BY u.id, u.name, u.email
            ORDER BY u.name
            """;

        // When
        QueryResult result = databaseService.executeSql(complexSql, 100);

        // Then
        assertThat(result.allColumns()).contains("NAME", "EMAIL", "ORDER_COUNT");
        assertThat(result.rowCount()).isGreaterThan(0);
        
        // Verify we have active users with their order counts
        boolean foundUserWithOrders = result.allRows().stream()
            .anyMatch(row -> ((Number) row.get(2)).intValue() > 0);
        assertThat(foundUserWithOrders).isTrue();
    }

    @Test
    @DisplayName("Should handle null values in real data")
    void shouldHandleNullValuesInRealData() throws SQLException {
        // Given - Insert a record with null values
        databaseService.executeSql("INSERT INTO users (name, email, age, is_active) VALUES ('Test User', NULL, NULL, TRUE)", 1);
        
        String sql = "SELECT name, email, age FROM users WHERE email IS NULL";

        // When
        QueryResult result = databaseService.executeSql(sql, 10);

        // Then
        assertThat(result.allRows()).hasSize(1);
        List<Object> row = result.allRows().get(0);
        assertThat(row.get(0)).isEqualTo("Test User"); // name
        assertThat(row.get(1)).isNull(); // email
        assertThat(row.get(2)).isNull(); // age
    }

    @Test
    @DisplayName("Should return null for unknown real resource")
    void shouldReturnNullForUnknownRealResource() throws SQLException {
        // When/Then
        assertThat(databaseService.readResource("database://table/NONEXISTENT")).isNull();
        assertThat(databaseService.readResource("database://unknown/resource")).isNull();
    }
}