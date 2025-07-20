package com.skanga.mcp;

import java.util.List;

/**
 * Immutable record that holds the complete result of a SQL query execution.
 * Contains both the data (columns and rows) and metadata (row count and execution time).
 *
 * @param allColumns List of column names in the order they appear in the result set
 * @param allRows List of data rows, where each row is a list of column values
 * @param rowCount The number of rows returned (may differ from allRows.size() if limited)
 * @param executionTimeMs Time taken to execute the query in milliseconds
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

    /**
     * Checks if the query result contains no data rows.
     *
     * @return true if no rows were returned, false otherwise
     */
    public boolean isEmpty() {
        return allRows.isEmpty();
    }

    /**
     * Checks if the query result contains data rows.
     *
     * @return true if rows were returned, false otherwise
     */
    public boolean hasResults() {
        return !allRows.isEmpty();
    }
}