package com.skanga.mcp;

/**
 * Database configuration holder
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

    // Static factory methods to replace constructors
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

    public static ConfigParams customConfig(String dbUrl, String dbUser, String dbPass,
                                            String dbDriver, boolean selectOnly, int maxSqlLength) {
        return new ConfigParams(
                dbUrl, dbUser, dbPass, dbDriver,
                10, 30000, 30, selectOnly, maxSqlLength,
                10000, 600000, 1800000, 60000
        );
    }

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
     * Masks sensitive information in URLs and other configuration values
     * @param inputValue the value to mask
     * @return masked version of the value
     */
    private String maskSensitive(String inputValue) {
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