package com.skanga.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class QueryResultTest {
    @Test
    @DisplayName("Should create query result with all properties")
    void shouldCreateQueryResultWithAllProperties() {
        // Given
        List<String> columns = Arrays.asList("id", "name", "email");
        List<List<Object>> rows = Arrays.asList(
            Arrays.asList(1, "John Doe", "john@example.com"),
            Arrays.asList(2, "Jane Smith", "jane@example.com")
        );
        int rowCount = 2;
        long executionTime = 150L;

        // When
        QueryResult result = new QueryResult(columns, rows, rowCount, executionTime);

        // Then
        assertThat(result.allColumns()).isEqualTo(columns);
        assertThat(result.allRows()).isEqualTo(rows);
        assertThat(result.rowCount()).isEqualTo(rowCount);
        assertThat(result.executionTimeMs()).isEqualTo(executionTime);
    }

    @Test
    @DisplayName("Should handle empty result set")
    void shouldHandleEmptyResultSet() {
        // Given
        List<String> columns = Arrays.asList("id", "name");
        List<List<Object>> rows = Collections.emptyList();
        int rowCount = 0;
        long executionTime = 50L;

        // When
        QueryResult result = new QueryResult(columns, rows, rowCount, executionTime);

        // Then
        assertThat(result.allColumns()).isEqualTo(columns);
        assertThat(result.allRows()).isEmpty();
        assertThat(result.rowCount()).isZero();
        assertThat(result.executionTimeMs()).isEqualTo(executionTime);
    }

    @Test
    @DisplayName("Should handle null values in rows")
    void shouldHandleNullValuesInRows() {
        // Given
        List<String> columns = Arrays.asList("id", "name", "optional_field");
        List<List<Object>> rows = Arrays.asList(
            Arrays.asList(1, "John", null),
            Arrays.asList(2, null, "some value")
        );
        int rowCount = 2;
        long executionTime = 75L;

        // When
        QueryResult result = new QueryResult(columns, rows, rowCount, executionTime);

        // Then
        assertThat(result.allRows()).hasSize(2);
        assertThat(result.allRows().get(0)).containsExactly(1, "John", null);
        assertThat(result.allRows().get(1)).containsExactly(2, null, "some value");
    }

    @Test
    @DisplayName("Should handle single column result")
    void shouldHandleSingleColumnResult() {
        // Given
        List<String> columns = Arrays.asList("count");
        List<List<Object>> rows = Arrays.asList(
            Arrays.asList(42)
        );
        int rowCount = 1;
        long executionTime = 25L;

        // When
        QueryResult result = new QueryResult(columns, rows, rowCount, executionTime);

        // Then
        assertThat(result.allColumns()).hasSize(1);
        assertThat(result.allColumns().get(0)).isEqualTo("count");
        assertThat(result.allRows()).hasSize(1);
        assertThat(result.allRows().get(0)).containsExactly(42);
    }

    @Test
    @DisplayName("Should handle large execution time")
    void shouldHandleLargeExecutionTime() {
        // Given
        List<String> columns = Arrays.asList("id");
        List<List<Object>> rows = Arrays.asList(Arrays.asList(1));
        int rowCount = 1;
        long executionTime = 5000L; // 5 seconds

        // When
        QueryResult result = new QueryResult(columns, rows, rowCount, executionTime);

        // Then
        assertThat(result.executionTimeMs()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("Should have meaningful toString representation")
    void shouldHaveMeaningfulToString() {
        // Given
        List<String> columns = Arrays.asList("id", "name");
        List<List<Object>> rows = Arrays.asList(
            Arrays.asList(1, "John"),
            Arrays.asList(2, "Jane")
        );
        int rowCount = 2;
        long executionTime = 150L;

        QueryResult result = new QueryResult(columns, rows, rowCount, executionTime);

        // When
        String toString = result.toString();

        //
        assertThat(toString)
                .contains("QueryResult")
                .contains("allColumns=[id, name]")
                .contains("allRows=[[1, John], [2, Jane]]")
                .contains("rowCount=2")
                .contains("executionTimeMs=150");
    }

    @Test
    @DisplayName("Should provide meaningful data access")
    void shouldProvideAccessToData() {
        // Given
        List<String> columns = Arrays.asList("id", "name");
        List<List<Object>> rows = Arrays.asList(
                Arrays.asList(1, "John"),
                Arrays.asList(2, "Jane")
        );
        QueryResult result = new QueryResult(columns, rows, 2, 150L);

        // Then - Test actual functionality, not string representation
        assertThat(result.allColumns()).containsExactly("id", "name");
        assertThat(result.allRows()).hasSize(2);
        assertThat(result.rowCount()).isEqualTo(2);
        assertThat(result.executionTimeMs()).isEqualTo(150L);
        assertThat(result.hasResults()).isTrue();
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("Should handle different data types in rows")
    void shouldHandleDifferentDataTypesInRows() {
        // Given
        List<String> columns = Arrays.asList("id", "name", "salary", "is_active", "hire_date");
        List<List<Object>> rows = Arrays.asList(
            Arrays.asList(1, "John", 50000.50, true, java.sql.Date.valueOf("2023-01-15")),
            Arrays.asList(2, "Jane", 75000.00, false, java.sql.Date.valueOf("2022-03-20"))
        );
        int rowCount = 2;
        long executionTime = 100L;

        // When
        QueryResult result = new QueryResult(columns, rows, rowCount, executionTime);

        // Then
        assertThat(result.allRows()).hasSize(2);
        assertThat(result.allRows().get(0).get(0)).isInstanceOf(Integer.class);
        assertThat(result.allRows().get(0).get(1)).isInstanceOf(String.class);
        assertThat(result.allRows().get(0).get(2)).isInstanceOf(Double.class);
        assertThat(result.allRows().get(0).get(3)).isInstanceOf(Boolean.class);
        assertThat(result.allRows().get(0).get(4)).isInstanceOf(java.sql.Date.class);
    }

    @Test
    @DisplayName("Should handle mismatched row count")
    void shouldHandleMismatchedRowCount() {
        // Given - rowCount parameter doesn't match actual rows size
        List<String> columns = Arrays.asList("id", "name");
        List<List<Object>> rows = Arrays.asList(
            Arrays.asList(1, "John"),
            Arrays.asList(2, "Jane")
        );
        int rowCount = 5; // Different from actual rows.size()
        long executionTime = 100L;

        // When
        QueryResult result = new QueryResult(columns, rows, rowCount, executionTime);

        // Then - Should store the provided rowCount, not calculate from rows
        assertThat(result.rowCount()).isEqualTo(5);
        assertThat(result.allRows()).hasSize(2);
    }
}