package com.skanga.mcp;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DatabaseServicePoolIntegrationTest {

    private DatabaseService service;
    private ConfigParams config;

    @BeforeAll
    void setUp() {
        // Use H2 in-memory database for testing
        config = new ConfigParams(
                "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
                "sa",
                "",
                "org.h2.Driver",
                5, // small pool for testing
                5000, 10, false, 10000, 10000, 60000, 1800000, 60000
        );

        service = new DatabaseService(config);

        // Set up test data
        try {
            service.executeQuery("CREATE TABLE IF NOT EXISTS users (id INT, name VARCHAR(50))", 1000);
            service.executeQuery("INSERT INTO users VALUES (1, 'Alice'), (2, 'Bob')", 1000);
        } catch (SQLException e) {
            // Initial setup, ignore validation errors
        }
    }

    @AfterAll
    void tearDown() {
        service.close();
    }

    @Test
    void testRealPoolBehavior() throws Exception {
        // Test that we can execute multiple queries concurrently
        List<CompletableFuture<QueryResult>> futures = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            CompletableFuture<QueryResult> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return service.executeQuery("SELECT COUNT(*) as count FROM users", 1000);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }

        // Wait for all queries to complete
        List<QueryResult> results = futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList());

        // All should succeed
        assertEquals(10, results.size());
        results.forEach(result -> {
            assertEquals(1, result.rowCount());
            assertEquals("2", result.allRows().get(0).get(0).toString());
        });
    }

    @Test
    void testConnectionPoolLimits() throws Exception {
        // This test would be more complex - could test pool exhaustion
        // by holding connections and then trying to exceed pool size
        assertDoesNotThrow(() -> {
            service.executeQuery("SELECT 1", 1000);
        });
    }
}
