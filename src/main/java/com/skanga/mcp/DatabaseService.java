package com.skanga.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.*;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Service class for database operations
 */
public class DatabaseService {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);
    private final ConfigParams configParams;
    private final HikariDataSource dataSource;

    public DatabaseService(ConfigParams configParams) {
        this.configParams = configParams;

        // Load the database driver
        try {
            Class.forName(configParams.dbDriver());
            logger.info("Database driver loaded: {}", configParams.dbDriver());
        } catch (ClassNotFoundException e) {
            logger.error("Failed to load database driver: {}", configParams.dbDriver(), e);
            throw new RuntimeException("Database driver not found", e);
        }

        // Initialize HikariCP connection pool
        HikariConfig poolConfig = new HikariConfig();
        poolConfig.setJdbcUrl(configParams.dbUrl());
        poolConfig.setUsername(configParams.dbUser());
        poolConfig.setPassword(configParams.dbPass());
        poolConfig.setDriverClassName(configParams.dbDriver());
        poolConfig.setMaximumPoolSize(configParams.maxConnections());
        poolConfig.setConnectionTimeout(configParams.connectionTimeoutMs());
        poolConfig.setIdleTimeout(configParams.idleTimeoutMs()); // 10 minutes default
        poolConfig.setMaxLifetime(configParams.maxLifetimeMs()); // 30 minutes
        poolConfig.setLeakDetectionThreshold(configParams.leakDetectionThresholdMs()); // 1 minute

        this.dataSource = new HikariDataSource(poolConfig);

        // Test connection
        try (Connection ignored = createConnection()) {
            logger.info("Database connection pool initialized for: {}", configParams.dbUrl());
        } catch (SQLException e) {
            logger.error("Failed to initialize connection pool: {}", configParams.dbUrl(), e);
            throw new RuntimeException("Database connection pool initialization failed", e);
        }
    }

    // Add this constructor to DatabaseService
    public DatabaseService(ConfigParams configParams, HikariDataSource dataSource) {
        this.configParams = configParams;
        this.dataSource = dataSource;

        // Load driver for validation
        try {
            Class.forName(configParams.dbDriver());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Database driver not found", e);
        }
    }

    /**
     * Execute a SQL query and return results
     */
    public QueryResult executeQuery(String sql, int maxRows) throws SQLException {
        long startTime = System.currentTimeMillis();

        // Add validation before executing
        if (configParams.selectOnly())
            validateSqlQuery(sql);

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setMaxRows(maxRows);
            stmt.setQueryTimeout(configParams.queryTimeoutSeconds());

            boolean isResultSet = stmt.execute();
            List<String> columns = new ArrayList<>();
            List<List<Object>> rows = new ArrayList<>();
            int rowCount = 0;

            if (isResultSet) {
                try (ResultSet rs = stmt.getResultSet()) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();

                    // Get column names
                    for (int i = 1; i <= columnCount; i++) {
                        columns.add(metaData.getColumnName(i));
                    }

                    // Get data rows
                    while (rs.next() && rowCount < maxRows) {
                        List<Object> row = new ArrayList<>();
                        for (int i = 1; i <= columnCount; i++) {
                            Object value = rs.getObject(i);
                            row.add(value);
                        }
                        rows.add(row);
                        rowCount++;
                    }
                }
            } else {
                // For INSERT, UPDATE, DELETE statements
                rowCount = stmt.getUpdateCount();
                columns.add("affected_rows");
                List<Object> row = new ArrayList<>();
                row.add(rowCount);
                rows.add(row);
            }

            long executionTime = System.currentTimeMillis() - startTime;

            return new QueryResult(columns, rows, rowCount, executionTime);

        } catch (SQLException e) {
            logger.error("Query execution failed: {}", sql, e);
            throw e;
        }
    }

    /**
     * List available database resources (tables, views, etc.)
     */
    public List<DatabaseResource> listResources() throws SQLException {
        List<DatabaseResource> databaseResources = new ArrayList<>();

        try (Connection conn = getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // Add database info resource
            databaseResources.add(new DatabaseResource(
                    "database://info",
                    "Database Information",
                    "General database metadata and connection information",
                    "text/plain",
                    getDatabaseInfo(metaData)
            ));

            // Add table resources
            String[] tableTypes = {"TABLE", "VIEW"};
            try (ResultSet tables = metaData.getTables(null, null, "%", tableTypes)) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");
                    String tableType = tables.getString("TABLE_TYPE");
                    String remarks = tables.getString("REMARKS");

                    String uri = String.format("database://table/%s", tableName);
                    String description = String.format("%s: %s", tableType,
                            remarks != null ? remarks : "No description");

                    databaseResources.add(new DatabaseResource(
                            uri,
                            tableName,
                            description,
                            "text/plain",
                            null // Content will be loaded on demand
                    ));
                }
            }

            // Add schema resources if supported
            try (ResultSet schemaResources = metaData.getSchemas()) {
                while (schemaResources.next()) {
                    String schemaName = schemaResources.getString("TABLE_SCHEM");
                    if (schemaName != null && !schemaName.trim().isEmpty()) {
                        String uri = String.format("database://schema/%s", schemaName);
                        databaseResources.add(new DatabaseResource(
                                uri,
                                schemaName,
                                "Database schema: " + schemaName,
                                "text/plain",
                                null
                        ));
                    }
                }
            } catch (SQLException e) {
                // Some databases don't support schemas
                logger.debug("Schemas not supported or accessible", e);
            }
        }

        return databaseResources;
    }

    /**
     * Read a specific database resource
     */
    public DatabaseResource readResource(String uri) throws SQLException {
        if (uri.equals("database://info")) {
            try (Connection conn = getConnection()) {
                DatabaseMetaData metaData = conn.getMetaData();
                String databaseInfo = getDatabaseInfo(metaData);
                return new DatabaseResource(uri, "Database Information",
                        "Database metadata", "text/plain", databaseInfo);
            }
        }

        if (uri.startsWith("database://table/")) {
            String tableName = uri.substring("database://table/".length());
            return getTableResource(tableName);
        }

        if (uri.startsWith("database://schema/")) {
            String schemaName = uri.substring("database://schema/".length());
            return getSchemaResource(schemaName);
        }

        return null;
    }

    private DatabaseResource getTableResource(String tableName) throws SQLException {
        try (Connection conn = getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // First check if the table actually exists
            boolean tableExists = false;
            try (ResultSet tables = metaData.getTables(null, null, tableName, new String[]{"TABLE", "VIEW"})) {
                if (tables.next()) {
                    tableExists = true;
                }
            }

            // If table doesn't exist, return null
            if (!tableExists) {
                return null;
            }

            StringBuilder content = new StringBuilder();
            content.append("Table: ").append(tableName).append("\n\n");

            // Table columns
            content.append("Columns:\n");
            try (ResultSet columns = metaData.getColumns(null, null, tableName, null)) {
                while (columns.next()) {
                    String columnName = columns.getString("COLUMN_NAME");
                    String dataType = columns.getString("TYPE_NAME");
                    int columnSize = columns.getInt("COLUMN_SIZE");
                    String nullable = columns.getString("IS_NULLABLE");
                    String defaultValue = columns.getString("COLUMN_DEF");

                    content.append(String.format("  - %s (%s", columnName, dataType));
                    if (columnSize > 0) {
                        content.append(String.format("(%d)", columnSize));
                    }
                    content.append(")");
                    if ("NO".equals(nullable)) {
                        content.append(" NOT NULL");
                    }
                    if (defaultValue != null) {
                        content.append(" DEFAULT ").append(defaultValue);
                    }
                    content.append("\n");
                }
            }

            // Primary keys
            content.append("\nPrimary Keys:\n");
            try (ResultSet primaryKeys = metaData.getPrimaryKeys(null, null, tableName)) {
                while (primaryKeys.next()) {
                    String columnName = primaryKeys.getString("COLUMN_NAME");
                    content.append("  - ").append(columnName).append("\n");
                }
            }

            // Foreign keys
            content.append("\nForeign Keys:\n");
            try (ResultSet foreignKeys = metaData.getImportedKeys(null, null, tableName)) {
                while (foreignKeys.next()) {
                    String fkColumnName = foreignKeys.getString("FKCOLUMN_NAME");
                    String pkTableName = foreignKeys.getString("PKTABLE_NAME");
                    String pkColumnName = foreignKeys.getString("PKCOLUMN_NAME");
                    content.append(String.format("  - %s -> %s.%s\n",
                            fkColumnName, pkTableName, pkColumnName));
                }
            }

            // Indexes
            content.append("\nIndexes:\n");
            try (ResultSet indexes = metaData.getIndexInfo(null, null, tableName, false, false)) {
                String currentIndexName = null;
                while (indexes.next()) {
                    String indexName = indexes.getString("INDEX_NAME");
                    if (indexName != null && !indexName.equals(currentIndexName)) {
                        boolean nonUnique = indexes.getBoolean("NON_UNIQUE");
                        content.append(String.format("  - %s (%s)\n",
                                indexName, nonUnique ? "NON-UNIQUE" : "UNIQUE"));
                        currentIndexName = indexName;
                    }
                }
            }

            String uri = "database://table/" + tableName;
            return new DatabaseResource(uri, tableName, "Table structure and metadata",
                    "text/plain", content.toString());
        }
    }

    private DatabaseResource getSchemaResource(String schemaName) throws SQLException {
        try (Connection conn = getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            // First check if the schema actually exists
            boolean schemaExists = false;
            try (ResultSet schemaResults = metaData.getSchemas()) {
                while (schemaResults.next()) {
                    String existingSchema = schemaResults.getString("TABLE_SCHEM");
                    if (schemaName.equals(existingSchema)) {
                        schemaExists = true;
                        break;
                    }
                }
            } catch (SQLException e) {
                // Some databases don't support schemas, so we'll assume it doesn't exist
                return null;
            }

            // If schema doesn't exist, return null
            if (!schemaExists) {
                return null;
            }

            StringBuilder schemaContent = new StringBuilder();
            schemaContent.append("Schema: ").append(schemaName).append("\n\n");
            schemaContent.append("Tables in this schema:\n");

            try (ResultSet tableMetadata = metaData.getTables(null, schemaName, "%", new String[]{"TABLE", "VIEW"})) {
                while (tableMetadata.next()) {
                    String tableName = tableMetadata.getString("TABLE_NAME");
                    String tableType = tableMetadata.getString("TABLE_TYPE");
                    schemaContent.append(String.format("  - %s (%s)\n", tableName, tableType));
                }
            }

            String uri = "database://schema/" + schemaName;
            return new DatabaseResource(uri, schemaName, "Schema information",
                    "text/plain", schemaContent.toString());
        }
    }

    private void validateSqlQuery(String sql) throws SQLException {
        if (sql == null || sql.trim().isEmpty()) {
            throw new SQLException("SQL query cannot be empty");
        }

        String normalizedSql = sql.trim().toLowerCase();

        // Block potentially dangerous SQL operations
        String[] dangerousOperations = {
                "drop", "truncate", "delete", "update", "insert", "create",
                "alter", "grant", "revoke", "exec", "execute", "call"
        };

        for (String operation : dangerousOperations) {
            if (normalizedSql.startsWith(operation + " ") || normalizedSql.equals(operation)) {
                throw new SQLException("Operation not allowed: " + operation.toUpperCase());
            }
        }

        // Additional check for common SQL injection patterns
        if (normalizedSql.contains(";") && !normalizedSql.endsWith(";")) {
            throw new SQLException("Multiple statements not allowed");
        }

        // Block comments that could be used for injection
        if (normalizedSql.contains("--") || normalizedSql.contains("/*")) {
            throw new SQLException("SQL comments not allowed");
        }
    }

    private String getDatabaseInfo(DatabaseMetaData metaData) throws SQLException {

        return "Database Information\n" +
                "===================\n\n" +
                "Product: " + metaData.getDatabaseProductName() + "\n" +
                "Version: " + metaData.getDatabaseProductVersion() + "\n" +
                "Driver: " + metaData.getDriverName() + "\n" +
                "Driver Version: " + metaData.getDriverVersion() + "\n" +
                "URL: " + configParams.dbUrl() + "\n" +
                "Username: " + configParams.dbUser() + "\n" +
                "Read Only: " + metaData.isReadOnly() + "\n" +
                "Auto Commit: " + metaData.getConnection().getAutoCommit() + "\n" +
                "Transaction Isolation: " + metaData.getDefaultTransactionIsolation() + "\n" +
                "\nSupported Features:\n" +
                "- Transactions: " + metaData.supportsTransactions() + "\n" +
                "- Stored Procedures: " + metaData.supportsStoredProcedures() + "\n" +
                "- Multiple ResultSets: " + metaData.supportsMultipleResultSets() + "\n" +
                "- Batch Updates: " + metaData.supportsBatchUpdates() + "\n";
    }

    public ConfigParams getDatabaseConfig() {
        return configParams;
    }

    /**
     * Get a database connection from the pool
     */
    protected Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    protected Connection createConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed");
        }
    }
}