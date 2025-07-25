package com.skanga.mcp;

/**
 * Database configuration holder that encapsulates all database connection and operational parameters.
 * This record provides immutable configuration with validation and utility methods for database operations.
 *
 * @param dbUrl The JDBC database URL (e.g., "jdbc:mysql://localhost:3306/database")
 * @param dbUser The database username for authentication
 * @param dbPass The database password for authentication
 * @param dbDriver The fully qualified JDBC driver class name (e.g., "com.mysql.cj.jdbc.Driver")
 * @param maxConnections Maximum number of connections in the connection pool (must be positive)
 * @param connectionTimeoutMs Timeout in milliseconds for obtaining a connection from the pool
 * @param queryTimeoutSeconds Timeout in seconds for individual SQL query execution
 * @param selectOnly Whether to restrict operations to SELECT queries only for security
 * @param maxSqlLength Maximum allowed length for SQL queries to prevent abuse
 * @param maxRowsLimit Maximum number of rows that can be returned from a query
 * @param idleTimeoutMs Timeout in milliseconds before idle connections are closed
 * @param maxLifetimeMs Maximum lifetime in milliseconds for connections in the pool
 * @param leakDetectionThresholdMs Threshold in milliseconds for detecting connection leaks
 */
public record ConfigParams(
        String dbUrl,
        String dbUser,
        String dbPass,
        String dbDriver,
        int maxConnections,
        int connectionTimeoutMs,
        int queryTimeoutSeconds,
        boolean selectOnly,
        int maxSqlLength,
        int maxRowsLimit,
        int idleTimeoutMs,
        int maxLifetimeMs,
        int leakDetectionThresholdMs
) {
    // Compact constructor with validation
    public ConfigParams {
        if (dbUrl == null || dbUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Database URL cannot be null or empty");
        }
        if (dbDriver == null || dbDriver.trim().isEmpty()) {
            throw new IllegalArgumentException("Database driver cannot be null or empty");
        }
        if (maxConnections <= 0) {
            throw new IllegalArgumentException("Max connections must be positive");
        }
        if (connectionTimeoutMs <= 0) {
            throw new IllegalArgumentException("Connection timeout must be positive");
        }
        if (queryTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("Query timeout must be positive");
        }
        if (maxSqlLength <= 0) {
            throw new IllegalArgumentException("Max SQL length must be positive");
        }
    }

    /**
     * Creates a default configuration with recommended settings for most use cases.
     * Uses conservative security settings with selectOnly=true and reasonable timeouts.
     *
     * @param dbUrl The JDBC database URL
     * @param dbUser The database username
     * @param dbPass The database password
     * @param dbDriver The JDBC driver class name
     * @return A ConfigParams instance with default settings
     * @throws IllegalArgumentException if any required parameter is null or invalid
     */
    public static ConfigParams defaultConfig(String dbUrl, String dbUser, String dbPass, String dbDriver) {
        return new ConfigParams(
                dbUrl, dbUser, dbPass, dbDriver,
                10,      // maxConnections
                30000,   // connectionTimeoutMs
                30,      // queryTimeoutSeconds
                true,    // selectOnly
                10000,   // maxSqlLength
                10000,   // maxRowsLimit
                600000,  // idleTimeoutMs (10 minutes)
                1800000, // maxLifetimeMs (30 minutes)
                60000    // leakDetectionThresholdMs (1 minute)
        );
    }

    /**
     * Creates a custom configuration allowing specification of security and query limits.
     * Useful when you need to override the default selectOnly behavior or SQL length limits.
     *
     * @param dbUrl The JDBC database URL
     * @param dbUser The database username
     * @param dbPass The database password
     * @param dbDriver The JDBC driver class name
     * @param selectOnly Whether to restrict to SELECT queries only
     * @param maxSqlLength Maximum allowed SQL query length
     * @return A ConfigParams instance with custom settings
     * @throws IllegalArgumentException if any required parameter is null or invalid
     */
    public static ConfigParams customConfig(String dbUrl, String dbUser, String dbPass,
                                            String dbDriver, boolean selectOnly, int maxSqlLength) {
        return new ConfigParams(
                dbUrl, dbUser, dbPass, dbDriver,
                10, 30000, 30, selectOnly, maxSqlLength,
                10000, 600000, 1800000, 60000
        );
    }

    /**
     * Determines the database type based on the JDBC URL pattern.
     * Used for providing database-specific functionality and SQL dialect guidance.
     *
     * @return A lowercase string identifying the database type (e.g., "mysql", "postgresql", "oracle")
     *         Returns "unknown" if the database type cannot be determined from the URL
     */
    public String getDatabaseType() {
        String lowerUrl = dbUrl.toLowerCase();

        // Standard databases
        if (lowerUrl.contains("mysql")) return "mysql";
        if (lowerUrl.contains("mariadb")) return "mariadb";
        if (lowerUrl.contains("postgresql")) return "postgresql";
        if (lowerUrl.contains("h2")) return "h2";
        if (lowerUrl.contains("sqlite")) return "sqlite";

        // Enterprise databases
        if (lowerUrl.contains("oracle")) return "oracle";
        if (lowerUrl.contains("db2")) return "db2";
        if (lowerUrl.contains("sqlserver")) return "sqlserver";

        // Cloud analytics
        if (lowerUrl.contains("redshift")) return "redshift";
        if (lowerUrl.contains("snowflake")) return "snowflake";
        if (lowerUrl.contains("bigquery")) return "bigquery";

        // Analytics databases
        if (lowerUrl.contains("clickhouse")) return "clickhouse";

        // Big data / NoSQL
        if (lowerUrl.contains("hive")) return "hive";
        if (lowerUrl.contains("spark")) return "spark";
        if (lowerUrl.contains("cassandra")) return "cassandra";
        if (lowerUrl.contains("mongodb")) return "mongodb";

        return "unknown";
    }

    /**
     * Masks sensitive information in configuration values for safe logging and display.
     * Protects passwords in JDBC URLs, wallet passwords, and other credential information.
     *
     * @param inputValue The configuration value that may contain sensitive information
     * @return A masked version of the input value with sensitive parts replaced by "***"
     */
    String maskSensitive(String inputValue) {
        if (inputValue == null || inputValue.trim().isEmpty()) {
            return inputValue;
        }

        String maskedValue = inputValue;

        // Mask password in JDBC URLs (common patterns)
        // Pattern: password=value or pwd=value (case-insensitive)
        maskedValue = maskedValue.replaceAll("(?i)(password|pwd)=([^;&]+)", "$1=***");

        // Mask user credentials in URLs like jdbc:mysql://user:pass@host:port/db
        maskedValue = maskedValue.replaceAll("://([^:]+):([^@]+)@", "://$1:***@");

        // Mask Oracle wallet passwords and similar
        maskedValue = maskedValue.replaceAll("(?i)(wallet_password|truststore_password|keystore_password)=([^;&]+)", "$1=***");

        // If the entire value looks like a password (common patterns), mask most of it
        if (inputValue.length() > 8 && !inputValue.contains("://") && !inputValue.contains("=")) {
            // Show first 2 and last 2 characters, mask the middle
            maskedValue = inputValue.substring(0, 2) + "***" + inputValue.substring(inputValue.length() - 2);
        }

        return maskedValue;
    }

    @Override
    public String toString() {
        return String.format("DatabaseConfig{url='%s', username='%s', driverClassName='%s', type='%s'}",
                maskSensitive(dbUrl), dbUser, dbDriver, getDatabaseType());
    }
}