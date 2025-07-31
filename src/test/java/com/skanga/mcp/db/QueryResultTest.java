package com.skanga.mcp.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        List<String> columns = List.of("count");
        List<List<Object>> rows = List.of(
                List.of(42)
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
        List<String> columns = List.of("id");
        List<List<Object>> rows = List.of(List.of(1));
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

    @Test
    @DisplayName("Should throw IllegalArgumentException when columns is null")
    void shouldThrowExceptionWhenColumnsIsNull() {
        // Given
        List<String> columns = null;
        List<List<Object>> rows = List.of(Arrays.asList(1, "John"));
        int rowCount = 1;
        long executionTime = 100L;

        // When & Then
        assertThatThrownBy(() -> new QueryResult(columns, rows, rowCount, executionTime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Columns cannot be null");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when rows is null")
    void shouldThrowExceptionWhenRowsIsNull() {
        // Given
        List<String> columns = Arrays.asList("id", "name");
        List<List<Object>> rows = null;
        int rowCount = 1;
        long executionTime = 100L;

        // When & Then
        assertThatThrownBy(() -> new QueryResult(columns, rows, rowCount, executionTime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Rows cannot be null");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when row count is negative")
    void shouldThrowExceptionWhenRowCountIsNegative() {
        // Given
        List<String> columns = Arrays.asList("id", "name");
        List<List<Object>> rows = List.of(Arrays.asList(1, "John"));
        int rowCount = -1;
        long executionTime = 100L;

        // When & Then
        assertThatThrownBy(() -> new QueryResult(columns, rows, rowCount, executionTime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Row count cannot be negative");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when execution time is negative")
    void shouldThrowExceptionWhenExecutionTimeIsNegative() {
        // Given
        List<String> columns = Arrays.asList("id", "name");
        List<List<Object>> rows = List.of(Arrays.asList(1, "John"));
        int rowCount = 1;
        long executionTime = -1L;

        // When & Then
        assertThatThrownBy(() -> new QueryResult(columns, rows, rowCount, executionTime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Execution time cannot be negative");
    }

    @Test
    @DisplayName("Should accept zero row count")
    void shouldAcceptZeroRowCount() {
        // Given
        List<String> columns = Arrays.asList("id", "name");
        List<List<Object>> rows = Collections.emptyList();
        int rowCount = 0;
        long executionTime = 50L;

        // When
        QueryResult result = new QueryResult(columns, rows, rowCount, executionTime);

        // Then
        assertThat(result.rowCount()).isZero();
        assertThat(result.isEmpty()).isTrue();
        assertThat(result.hasResults()).isFalse();
    }

    @Test
    @DisplayName("Should accept zero execution time")
    void shouldAcceptZeroExecutionTime() {
        // Given
        List<String> columns = Arrays.asList("id", "name");
        List<List<Object>> rows = List.of(Arrays.asList(1, "John"));
        int rowCount = 1;
        long executionTime = 0L;

        // When
        QueryResult result = new QueryResult(columns, rows, rowCount, executionTime);

        // Then
        assertThat(result.executionTimeMs()).isZero();
    }

    @Test
    @DisplayName("Should return true for isEmpty when rows is empty")
    void shouldReturnTrueForIsEmptyWhenRowsIsEmpty() {
        // Given
        List<String> columns = Arrays.asList("id", "name");
        List<List<Object>> rows = Collections.emptyList();
        QueryResult result = new QueryResult(columns, rows, 0, 100L);

        // When & Then
        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Should return false for isEmpty when rows contains data")
    void shouldReturnFalseForIsEmptyWhenRowsContainsData() {
        // Given
        List<String> columns = Arrays.asList("id", "name");
        List<List<Object>> rows = List.of(Arrays.asList(1, "John"));
        QueryResult result = new QueryResult(columns, rows, 1, 100L);

        // When & Then
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("Should return false for hasResults when rows is empty")
    void shouldReturnFalseForHasResultsWhenRowsIsEmpty() {
        // Given
        List<String> columns = Arrays.asList("id", "name");
        List<List<Object>> rows = Collections.emptyList();
        QueryResult result = new QueryResult(columns, rows, 0, 100L);

        // When & Then
        assertThat(result.hasResults()).isFalse();
    }

    @Test
    @DisplayName("Should return true for hasResults when rows contains data")
    void shouldReturnTrueForHasResultsWhenRowsContainsData() {
        // Given
        List<String> columns = Arrays.asList("id", "name");
        List<List<Object>> rows = List.of(Arrays.asList(1, "John"));
        QueryResult result = new QueryResult(columns, rows, 1, 100L);

        // When & Then
        assertThat(result.hasResults()).isTrue();
    }

    @Test
    @DisplayName("Should handle empty columns list")
    void shouldHandleEmptyColumnsList() {
        // Given
        List<String> columns = Collections.emptyList();
        List<List<Object>> rows = Collections.emptyList();
        int rowCount = 0;
        long executionTime = 25L;

        // When
        QueryResult result = new QueryResult(columns, rows, rowCount, executionTime);

        // Then
        assertThat(result.allColumns()).isEmpty();
        assertThat(result.allRows()).isEmpty();
        assertThat(result.isEmpty()).isTrue();
        assertThat(result.hasResults()).isFalse();
    }

    @Test
    @DisplayName("Should handle rows with empty lists")
    void shouldHandleRowsWithEmptyLists() {
        // Given
        List<String> columns = Arrays.asList("id", "name");
        List<List<Object>> rows = Arrays.asList(
                Collections.emptyList(),
                Collections.emptyList()
        );
        int rowCount = 2;
        long executionTime = 75L;

        // When
        QueryResult result = new QueryResult(columns, rows, rowCount, executionTime);

        // Then
        assertThat(result.allRows()).hasSize(2);
        assertThat(result.allRows().get(0)).isEmpty();
        assertThat(result.allRows().get(1)).isEmpty();
        assertThat(result.hasResults()).isTrue(); // Non-empty rows list, even if rows are empty
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("Should handle columns with null values")
    void shouldHandleColumnsWithNullValues() {
        // Given
        List<String> columns = Arrays.asList("id", null, "name");
        List<List<Object>> rows = List.of(Arrays.asList(1, "data", "John"));
        int rowCount = 1;
        long executionTime = 50L;

        // When
        QueryResult result = new QueryResult(columns, rows, rowCount, executionTime);

        // Then
        assertThat(result.allColumns()).hasSize(3);
        assertThat(result.allColumns().get(1)).isNull();
    }

    @Test
    @DisplayName("Should handle very large datasets")
    void shouldHandleVeryLargeDatasets() {
        // Given
        List<String> columns = Arrays.asList("id", "data");
        List<List<Object>> rows = new java.util.ArrayList<>();

        // Create 1000 rows
        for (int i = 0; i < 1000; i++) {
            rows.add(Arrays.asList(i, "data_" + i));
        }

        int rowCount = 1000;
        long executionTime = 2500L;

        // When
        QueryResult result = new QueryResult(columns, rows, rowCount, executionTime);

        // Then
        assertThat(result.allRows()).hasSize(1000);
        assertThat(result.rowCount()).isEqualTo(1000);
        assertThat(result.hasResults()).isTrue();
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("Should handle maximum possible execution time")
    void shouldHandleMaximumExecutionTime() {
        // Given
        List<String> columns = List.of("id");
        List<List<Object>> rows = List.of(List.of(1));
        int rowCount = 1;
        long executionTime = Long.MAX_VALUE;

        // When
        QueryResult result = new QueryResult(columns, rows, rowCount, executionTime);

        // Then
        assertThat(result.executionTimeMs()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    @DisplayName("Should handle maximum possible row count")
    void shouldHandleMaximumRowCount() {
        // Given
        List<String> columns = List.of("id");
        List<List<Object>> rows = List.of(List.of(1));
        int rowCount = Integer.MAX_VALUE;
        long executionTime = 100L;

        // When
        QueryResult result = new QueryResult(columns, rows, rowCount, executionTime);

        // Then
        assertThat(result.rowCount()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("Should store references to collections - not defensive copies")
    void shouldStoreReferencesToCollections() {
        // Given
        List<String> originalColumns = new java.util.ArrayList<>(Arrays.asList("id", "name"));
        List<List<Object>> originalRows = new java.util.ArrayList<>(
                List.of(new ArrayList<>(Arrays.asList(1, "John")))
        );

        QueryResult result = new QueryResult(originalColumns, originalRows, 1, 100L);

        // When - modify original collections
        originalColumns.add("email");
        originalRows.add(Arrays.asList(2, "Jane"));
        originalRows.get(0).add("extra");

        // Then - QueryResult IS affected because it stores references
        assertThat(result.allColumns()).hasSize(3); // Now includes "email"
        assertThat(result.allRows()).hasSize(2); // Now includes Jane
        assertThat(result.allRows().get(0)).hasSize(3); // Now includes "extra"
    }

    @Test
    @DisplayName("Should be safe when using immutable collections")
    void shouldBeSafeWhenUsingImmutableCollections() {
        // Given - Use immutable collections
        List<String> columns = List.of("id", "name"); // Immutable
        List<List<Object>> rows = List.of(List.of(1, "John")); // Immutable

        QueryResult result = new QueryResult(columns, rows, 1, 100L);

        // When - try to modify (this would throw UnsupportedOperationException)
        // columns.add("email"); // Would throw exception
        // rows.add(List.of(2, "Jane")); // Would throw exception

        // Then - QueryResult data remains unchanged
        assertThat(result.allColumns()).hasSize(2);
        assertThat(result.allRows()).hasSize(1);
        assertThat(result.allRows().get(0)).hasSize(2);
    }

    @Test
    @DisplayName("Should demonstrate proper usage with defensive copying")
    void shouldDemonstrateProperUsageWithDefensiveCopying() {
        // Given - Create defensive copies when creating the record
        List<String> originalColumns = new java.util.ArrayList<>(Arrays.asList("id", "name"));
        List<List<Object>> originalRows = new java.util.ArrayList<>(
                List.of(new ArrayList<>(Arrays.asList(1, "John")))
        );

        // Create defensive copies
        List<String> columnsCopy = new java.util.ArrayList<>(originalColumns);
        List<List<Object>> rowsCopy = originalRows.stream()
                .map(java.util.ArrayList::new)
                .collect(java.util.stream.Collectors.toList());

        QueryResult result = new QueryResult(columnsCopy, rowsCopy, 1, 100L);

        // When - modify original collections
        originalColumns.add("email");
        originalRows.add(Arrays.asList(2, "Jane"));
        originalRows.get(0).add("extra");

        // Then - QueryResult is not affected because we used copies
        assertThat(result.allColumns()).hasSize(2);
        assertThat(result.allRows()).hasSize(1);
        assertThat(result.allRows().get(0)).hasSize(2);
    }

    @Test
    @DisplayName("Should handle SQL-specific data types")
    void shouldHandleSqlSpecificDataTypes() {
        // Given
        List<String> columns = Arrays.asList("id", "timestamp", "decimal", "blob_size");
        List<List<Object>> rows = List.of(
                Arrays.asList(
                        1L,
                        Timestamp.valueOf("2023-01-15 10:30:45"),
                        BigDecimal.valueOf(123.45),
                        null
                )
        );
        int rowCount = 1;
        long executionTime = 85L;

        // When
        QueryResult result = new QueryResult(columns, rows, rowCount, executionTime);

        // Then
        assertThat(result.allRows().get(0).get(0)).isInstanceOf(Long.class);
        assertThat(result.allRows().get(0).get(1)).isInstanceOf(java.sql.Timestamp.class);
        assertThat(result.allRows().get(0).get(2)).isInstanceOf(java.math.BigDecimal.class);
        assertThat(result.allRows().get(0).get(3)).isNull();
    }

    @Test
    @DisplayName("Should handle edge case where rowCount is zero but rows exist")
    void shouldHandleEdgeCaseWhereRowCountIsZeroButRowsExist() {
        // Given - This might happen in some database scenarios
        List<String> columns = Arrays.asList("id", "name");
        List<List<Object>> rows = List.of(Arrays.asList(1, "John"));
        int rowCount = 0; // Inconsistent with rows.size()
        long executionTime = 50L;

        // When
        QueryResult result = new QueryResult(columns, rows, rowCount, executionTime);

        // Then - Should honor the provided rowCount parameter
        assertThat(result.rowCount()).isZero();
        assertThat(result.allRows()).hasSize(1);
        assertThat(result.hasResults()).isTrue(); // Based on actual rows
        assertThat(result.isEmpty()).isFalse(); // Based on actual rows
    }

    @Test
    @DisplayName("Should support equals and hashCode for records")
    void shouldSupportEqualsAndHashCodeForRecords() {
        // Given
        List<String> columns = Arrays.asList("id", "name");
        List<List<Object>> rows = List.of(Arrays.asList(1, "John"));
        int rowCount = 1;
        long executionTime = 100L;

        QueryResult result1 = new QueryResult(columns, rows, rowCount, executionTime);
        QueryResult result2 = new QueryResult(columns, rows, rowCount, executionTime);
        QueryResult result3 = new QueryResult(columns, rows, 2, executionTime); // Different rowCount

        // When & Then
        assertThat(result1).isEqualTo(result2);
        assertThat(result1).isNotEqualTo(result3);
        assertThat(result1.hashCode()).isEqualTo(result2.hashCode());
        assertThat(result1.hashCode()).isNotEqualTo(result3.hashCode());
    }

    @Test
    @DisplayName("Should handle UPDATE/INSERT/DELETE result format")
    void shouldHandleUpdateInsertDeleteResultFormat() {
        // Given - Typical format for non-SELECT queries
        List<String> columns = List.of("affected_rows");
        List<List<Object>> rows = List.of(List.of(5));
        int rowCount = 5;
        long executionTime = 75L;

        // When
        QueryResult result = new QueryResult(columns, rows, rowCount, executionTime);

        // Then
        assertThat(result.allColumns()).containsExactly("affected_rows");
        assertThat(result.allRows()).hasSize(1);
        assertThat(result.allRows().get(0)).containsExactly(5);
        assertThat(result.rowCount()).isEqualTo(5);
        assertThat(result.hasResults()).isTrue();
    }
}