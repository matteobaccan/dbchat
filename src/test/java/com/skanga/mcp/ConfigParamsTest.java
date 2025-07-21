package com.skanga.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigParamsTest {
    @Test
    @DisplayName("Should create config with all parameters")
    void shouldCreateConfigWithAllParameters() {
        // Given
        String url = "jdbc:mysql://localhost:3306/test";
        String username = "testuser";
        String password = "testpass";
        String driver = "com.mysql.cj.jdbc.Driver";
        int maxConnections = 20;
        int connectionTimeout = 60000;
        int queryTimeout = 60;
        boolean selectOnly = true;
        int maxSqlLength = 10000;
        int maxRowsLimit = 10000;
        int idleTimeoutMs = 600000;
        int maxLifetimeMs = 1800000;
        int leakDetectionThresholdMs = 60000;

        // When
        ConfigParams config = new ConfigParams(url, username, password, driver,
                maxConnections, connectionTimeout, queryTimeout, selectOnly,
                maxSqlLength, maxRowsLimit, idleTimeoutMs, maxLifetimeMs, leakDetectionThresholdMs);

        // Then
        assertThat(config.dbUrl()).isEqualTo(url);
        assertThat(config.dbUser()).isEqualTo(username);
        assertThat(config.dbPass()).isEqualTo(password);
        assertThat(config.dbDriver()).isEqualTo(driver);
        assertThat(config.maxConnections()).isEqualTo(maxConnections);
        assertThat(config.connectionTimeoutMs()).isEqualTo(connectionTimeout);
        assertThat(config.queryTimeoutSeconds()).isEqualTo(queryTimeout);
    }

    @Test
    @DisplayName("Should throw appropriate exceptions")
    void shouldThrow() {
        // Given
        String url = "jdbc:mysql://localhost:3306/test";
        String username = "testuser";
        String password = "testpass";
        String driver = "com.mysql.cj.jdbc.Driver";
        int maxConnections = 20;
        int connectionTimeout = 60000;
        int queryTimeout = 60;
        boolean selectOnly = true;
        int maxSqlLength = 10000;
        int maxRowsLimit = 10000;
        int idleTimeoutMs = 600000;
        int maxLifetimeMs = 1800000;
        int leakDetectionThresholdMs = 60000;

        assertThrows(IllegalArgumentException.class, () -> new ConfigParams(url, username, password, driver,
                -1, connectionTimeout, queryTimeout, selectOnly,
                maxSqlLength, maxRowsLimit, idleTimeoutMs, maxLifetimeMs, leakDetectionThresholdMs));
        assertThrows(IllegalArgumentException.class, () -> new ConfigParams(url, username, password, driver,
                maxConnections, -1, queryTimeout, selectOnly,
                maxSqlLength, maxRowsLimit, idleTimeoutMs, maxLifetimeMs, leakDetectionThresholdMs));
        assertThrows(IllegalArgumentException.class, () -> new ConfigParams(url, username, password, driver,
                maxConnections, connectionTimeout, -1, selectOnly,
                maxSqlLength, maxRowsLimit, idleTimeoutMs, maxLifetimeMs, leakDetectionThresholdMs));
        assertThrows(IllegalArgumentException.class, () -> new ConfigParams(url, username, password, driver,
                maxConnections, connectionTimeout, queryTimeout, selectOnly,
                -1, maxRowsLimit, idleTimeoutMs, maxLifetimeMs, leakDetectionThresholdMs));
    }

    @Test
    @DisplayName("Should create config with default values")
    void shouldCreateConfigWithDefaultValues() {
        // Given
        String url = "jdbc:mysql://localhost:3306/test";
        String username = "testuser";
        String password = "testpass";
        String driver = "com.mysql.cj.jdbc.Driver";

        // When
        ConfigParams config = ConfigParams.defaultConfig(url, username, password, driver);

        // Then
        assertThat(config.dbUrl()).isEqualTo(url);
        assertThat(config.dbUser()).isEqualTo(username);
        assertThat(config.dbPass()).isEqualTo(password);
        assertThat(config.dbDriver()).isEqualTo(driver);
        assertThat(config.maxConnections()).isEqualTo(10);
        assertThat(config.connectionTimeoutMs()).isEqualTo(30000);
        assertThat(config.queryTimeoutSeconds()).isEqualTo(30);
    }

    @ParameterizedTest
    @CsvSource({
        "jdbc:mysql://localhost:3306/test, mysql",
        "jdbc:oracle:thin:@localhost:1521:xe, oracle",
        "jdbc:sqlserver://localhost:1433;database=test, sqlserver",
        "jdbc:postgresql://localhost:5432/test, postgresql",
        "jdbc:h2:mem:test, h2",
        "jdbc:sqlite:/path/to/db, sqlite",
        "jdbc:unknown://test, unknown"
    })
    @DisplayName("Should detect database type from URL")
    void shouldDetectDatabaseTypeFromUrl(String url, String expectedType) {
        // Given
        ConfigParams config = ConfigParams.defaultConfig(url, "user", "pass", "driver");

        // When
        String actualType = config.getDatabaseType();

        // Then
        assertThat(actualType).isEqualTo(expectedType);
    }
    @Test
    @DisplayName("Should reject invalid configuration values")
    void shouldRejectInvalidConfigurationValues() {
        // Empty URL should be rejected
        assertThatThrownBy(() -> ConfigParams.defaultConfig("", "user", "pass", "driver"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Database URL cannot be null or empty");

        // Empty driver should be rejected
        assertThatThrownBy(() -> ConfigParams.defaultConfig("jdbc:h2:mem:test", "user", "pass", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Database driver cannot be null or empty");

        // Null values should be rejected
        assertThatThrownBy(() -> ConfigParams.defaultConfig(null, "user", "pass", "driver"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Database URL cannot be null or empty");
    }

    @Test
    @DisplayName("Should allow empty user and password")
    void shouldAllowEmptyUserAndPassword() {
        // Given - Some databases allow empty credentials
        ConfigParams config = ConfigParams.defaultConfig(
                "jdbc:h2:mem:testdb",
                "", // empty user is OK
                "", // empty password is OK
                "org.h2.Driver"
        );

        // Then
        assertThat(config.dbUrl()).isEqualTo("jdbc:h2:mem:testdb");
        assertThat(config.dbUser()).isEmpty();
        assertThat(config.dbPass()).isEmpty();
        assertThat(config.dbDriver()).isEqualTo("org.h2.Driver");
        assertThat(config.getDatabaseType()).isEqualTo("h2");
    }

    @Test
    @DisplayName("Should determine database type from URL")
    void shouldDetermineDatabaseTypeFromUrl() {
        // Test various database types
        assertThat(ConfigParams.defaultConfig("jdbc:mysql://localhost/test", "", "", "driver").getDatabaseType())
                .isEqualTo("mysql");

        assertThat(ConfigParams.defaultConfig("jdbc:postgresql://localhost/test", "", "", "driver").getDatabaseType())
                .isEqualTo("postgresql");

        assertThat(ConfigParams.defaultConfig("jdbc:oracle:thin:@localhost:1521:test", "", "", "driver").getDatabaseType())
                .isEqualTo("oracle");

        assertThat(ConfigParams.defaultConfig("jdbc:unknown://test", "", "", "driver").getDatabaseType())
                .isEqualTo("unknown");
    }

    @Test
    @DisplayName("Should have meaningful toString representation")
    void shouldHaveMeaningfulToString() {
        // Given
        ConfigParams config = ConfigParams.defaultConfig(
            "jdbc:mysql://localhost:3306/test",
            "testuser",
            "testpass",
            "com.mysql.cj.jdbc.Driver"
        );

        // When
        String toString = config.toString();

        // Then
        assertThat(toString)
            .contains("DatabaseConfig")
            .contains("jdbc:mysql://localhost:3306/test")
            .contains("testuser")
            .contains("com.mysql.cj.jdbc.Driver")
            .contains("mysql");
    }

    @Test
    @DisplayName("Should have meaningful toString representation")
    void testMaskSensitive() {
        ConfigParams config = ConfigParams.defaultConfig("a", "b", "c", "d");

        assertThat(config.maskSensitive("test1_password")).isEqualTo("te***rd");
        assertThat(config.maskSensitive(null)).isNull();
    }

    @Test
    @DisplayName("Should handle case insensitive database type detection")
    void shouldHandleCaseInsensitiveDatabaseTypeDetection() {
        // Given
        ConfigParams config1 = ConfigParams.defaultConfig("JDBC:MYSQL://localhost", "user", "pass", "driver");
        ConfigParams config2 = ConfigParams.defaultConfig("jdbc:MySQL://localhost", "user", "pass", "driver");

        // When/Then
        assertThat(config1.getDatabaseType()).isEqualTo("mysql");
        assertThat(config2.getDatabaseType()).isEqualTo("mysql");
    }
}