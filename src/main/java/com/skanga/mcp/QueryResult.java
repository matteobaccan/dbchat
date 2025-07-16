package com.skanga.mcp;

import java.util.List;

/**
 * Holds the result of a SQL query execution
 */
public record QueryResult(List<String> allColumns, List<List<Object>> allRows, int rowCount, long executionTimeMs) {
    public QueryResult {
        if (allColumns == null) {
            throw new IllegalArgumentException("Columns cannot be null");
        }
        if (allRows == null) {
            throw new IllegalArgumentException("Rows cannot be null");
        }
        if (rowCount < 0) {
            throw new IllegalArgumentException("Row count cannot be negative");
        }
        if (executionTimeMs < 0) {
            throw new IllegalArgumentException("Execution time cannot be negative");
        }
    }

    public boolean isEmpty() {
        return allRows.isEmpty();
    }

    public boolean hasResults() {
        return !allRows.isEmpty();
    }
}