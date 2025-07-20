package com.skanga.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import java.io.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for configuration loading and edge cases in McpServer
 */
class McpServerConfigurationTest {
    private PrintStream originalErr;

    @BeforeEach
    void setUp() {
        originalErr = System.err;
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errorOutput));
    }

    @AfterEach
    void tearDown() {
        System.setErr(originalErr);
        // Clear any system properties set during tests
        System.clearProperty("db.url");
        System.clearProperty("http.mode");
        System.clearProperty("http.port");
    }

    @Test
    void testConfigurationPrecedence_CliOverridesEnv() throws IOException {
        // Test that CLI arguments override environment variables
        // This tests the getConfigValue method indirectly
        
        String[] args = {"--db_url=jdbc:test:cli"};
        
        // Set environment variable
        // Note: We can't actually set env vars in Java tests easily,
        // but we can test the system property precedence
        System.setProperty("db.url", "jdbc:test:sysprop");
        
        ConfigParams config = McpServer.loadConfiguration(args);
        assertEquals("jdbc:test:cli", config.dbUrl());
    }

    @Test
    void testConfigurationPrecedence_SystemProperty() throws IOException {
        String[] args = {}; // No CLI args
        
        System.setProperty("db.url", "jdbc:test:sysprop");
        
        ConfigParams config = McpServer.loadConfiguration(args);
        assertEquals("jdbc:test:sysprop", config.dbUrl());
    }

    @Test
    void testConfigurationPrecedence_DefaultValues() throws IOException {
        String[] args = {}; // No CLI args, no system properties
        
        ConfigParams config = McpServer.loadConfiguration(args);
        assertEquals("jdbc:h2:mem:testdb", config.dbUrl());
        assertEquals("sa", config.dbUser());
        assertEquals("", config.dbPass());
        assertEquals("org.h2.Driver", config.dbDriver());
        assertTrue(config.selectOnly());
    }

    @Test
    void testParseArgs_ValidArguments() {
        String[] args = {
            "--db_url=jdbc:test:url",
            "--max_connections=20",
            "--select_only=false",
            "--invalid-format", // Should be ignored
            "not-an-arg" // Should be ignored
        };
        
        Map<String, String> parsed = McpServer.parseArgs(args);
        assertEquals("jdbc:test:url", parsed.get("DB_URL"));
        assertEquals("20", parsed.get("MAX_CONNECTIONS"));
        assertEquals("false", parsed.get("SELECT_ONLY"));
        assertFalse(parsed.containsKey("INVALID-FORMAT"));
        assertFalse(parsed.containsKey("NOT-AN-ARG"));
    }

    @Test
    void testParseArgs_EmptyArguments() {
        String[] args = {};
        Map<String, String> parsed = McpServer.parseArgs(args);
        assertTrue(parsed.isEmpty());
    }

    @Test
    void testIsHttpMode_DefaultFalse() {
        String[] args = {};
        assertFalse(McpServer.isHttpMode(args));
    }

    @Test
    void testIsHttpMode_CliArgument() {
        String[] args = {"--http_mode=true"};
        assertTrue(McpServer.isHttpMode(args));
    }

    @Test
    void testIsHttpMode_SystemProperty() {
        String[] args = {};
        System.setProperty("http.mode", "true");
        assertTrue(McpServer.isHttpMode(args));
    }

    @Test
    void testGetHttpPort_Default() {
        String[] args = {};
        assertEquals(8080, McpServer.getHttpPort(args));
    }

    @Test
    void testGetHttpPort_CliArgument() {
        String[] args = {"--http_port=9090"};
        assertEquals(9090, McpServer.getHttpPort(args));
    }

    @Test
    void testMainMethod_InvalidConfiguration() {
        // Test main method with invalid numeric configuration
        String[] args = {"--max_connections=invalid"};
        
        assertThrows(NumberFormatException.class, () -> McpServer.main(args));
    }

    @Test
    void testMainMethod_StdioModeSuccess() {
        // Test successful stdio mode startup
        String[] args = {"--http_mode=false"};
        
        // Mock stdin to provide empty input (causes immediate exit)
        ByteArrayInputStream emptyInput = new ByteArrayInputStream(new byte[0]);
        InputStream originalIn = System.in;
        
        try {
            System.setIn(emptyInput);
            
            // This should not throw an exception and should exit gracefully
            assertDoesNotThrow(() -> McpServer.main(args));
            
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
    void testShutdownHook() {
        // Test that shutdown hook is properly registered
        ConfigParams config = new ConfigParams(
            "jdbc:h2:mem:testdb", "sa", "", "org.h2.Driver",
            10, 30000, 30, true, 10000, 10000, 600000, 1800000, 60000
        );
        
        McpServer server = new McpServer(config);
        
        // Create the shutdown hook manually to test it
        Thread shutdownHook = new Thread(server.databaseService::close);
        
        // Verify the hook runs without error
        assertDoesNotThrow(shutdownHook::run);
    }
}
