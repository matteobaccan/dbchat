package com.skanga.mcp.config;

import com.skanga.mcp.config.CliUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;

/**
 * Unit tests for CliUtils class.
 * Tests command line argument parsing, help display, version display, and JDBC driver detection.
 */
class CliUtilsTest {
    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    
    @BeforeEach
    void setUp() {
        // Redirect System.out to capture output
        System.setOut(new PrintStream(outputStream));
    }
    
    @AfterEach
    void tearDown() {
        // Restore original System.out
        System.setOut(originalOut);
    }

    @Test
    void testShortFormMapping() {
        Map<String, String> mapping = CliUtils.getShortFormMapping();
        
        // Test essential mappings
        assertEquals("help", mapping.get("h"));
        assertEquals("version", mapping.get("v"));
        assertEquals("db_url", mapping.get("u"));
        assertEquals("db_user", mapping.get("U"));
        assertEquals("db_password", mapping.get("P"));
        assertEquals("http_port", mapping.get("p"));
        assertEquals("config_file", mapping.get("c"));
        assertEquals("http_mode", mapping.get("m"));
        
        // Test that all keys are single characters
        for (String key : mapping.keySet()) {
            assertEquals(1, key.length(), "Short form key should be single character: " + key);
        }
        
        // Test that all values are valid parameter names
        for (String value : mapping.values()) {
            assertFalse(value.isEmpty(), "Parameter name should not be empty");
            assertTrue(value.matches("[a-z_]+"), "Parameter name should only contain lowercase letters and underscores: " + value);
        }
    }

    @Test
    void testParseArgsLongFormEquals() {
        String[] args = {"--db_url=jdbc:postgresql://localhost/test", "--http_port=9090", "--select_only=false"};
        Map<String, String> result = CliUtils.parseArgs(args);
        
        assertEquals("jdbc:postgresql://localhost/test", result.get("DB_URL"));
        assertEquals("9090", result.get("HTTP_PORT"));
        assertEquals("false", result.get("SELECT_ONLY"));
    }

    @Test
    void testParseArgsLongFormSpaceSeparated() {
        String[] args = {"--db_url", "jdbc:postgresql://localhost/test", "--http_port", "9090"};
        Map<String, String> result = CliUtils.parseArgs(args);
        
        assertEquals("jdbc:postgresql://localhost/test", result.get("DB_URL"));
        assertEquals("9090", result.get("HTTP_PORT"));
    }

    @Test
    void testParseArgsShortFormEquals() {
        String[] args = {"-u=jdbc:h2:mem:test", "-p=8080", "-s=true"};
        Map<String, String> result = CliUtils.parseArgs(args);
        
        assertEquals("jdbc:h2:mem:test", result.get("DB_URL"));
        assertEquals("8080", result.get("HTTP_PORT"));
        assertEquals("true", result.get("SELECT_ONLY"));
    }

    @Test
    void testParseArgsShortFormSpaceSeparated() {
        String[] args = {"-u", "jdbc:h2:mem:test", "-p", "8080"};
        Map<String, String> result = CliUtils.parseArgs(args);
        
        assertEquals("jdbc:h2:mem:test", result.get("DB_URL"));
        assertEquals("8080", result.get("HTTP_PORT"));
    }

    @Test
    void testParseArgsFlags() {
        String[] args = {"--help", "-v", "--http_mode", "-s"};
        Map<String, String> result = CliUtils.parseArgs(args);
        
        assertEquals("true", result.get("HELP"));
        assertEquals("true", result.get("VERSION"));
        assertEquals("true", result.get("HTTP_MODE"));
        assertEquals("true", result.get("SELECT_ONLY"));
    }

    @Test
    void testParseArgsMixedFormats() {
        String[] args = {"-u=jdbc:postgresql://localhost/db", "--db_user", "admin", "-p", "9090", "--select_only=false"};
        Map<String, String> result = CliUtils.parseArgs(args);
        
        assertEquals("jdbc:postgresql://localhost/db", result.get("DB_URL"));
        assertEquals("admin", result.get("DB_USER"));
        assertEquals("9090", result.get("HTTP_PORT"));
        assertEquals("false", result.get("SELECT_ONLY"));
    }

    @Test
    void testParseArgsWithQuotedValues() {
        String[] args = {"--db_password=\"secret with spaces\"", "-U=user"};
        Map<String, String> result = CliUtils.parseArgs(args);
        
        assertEquals("\"secret with spaces\"", result.get("DB_PASSWORD"));
        assertEquals("user", result.get("DB_USER"));
    }

    @Test
    void testParseArgsEmptyArray() {
        String[] args = {};
        Map<String, String> result = CliUtils.parseArgs(args);
        
        assertTrue(result.isEmpty());
    }

    @Test
    void testParseArgsInvalidShortForm() {
        String[] args = {"-z=unknown"};  // 'z' is not mapped
        Map<String, String> result = CliUtils.parseArgs(args);
        
        assertTrue(result.isEmpty()); // Should ignore unknown short forms
    }

    @Test
    void testParseArgsUnknownLongForm() {
        String[] args = {"--unknown_param=value"};
        Map<String, String> result = CliUtils.parseArgs(args);
        
        assertEquals("value", result.get("UNKNOWN_PARAM")); // Should accept any long form
    }

    @Test
    void testParseArgsValueStartingWithDash() {
        String[] args = {"--db_password", "--not-a-flag", "-u", "-also-not-a-flag"};
        Map<String, String> result = CliUtils.parseArgs(args);
        
        // Values starting with dash should be treated as flags, not values
        assertEquals("true", result.get("DB_PASSWORD"));
        assertEquals("true", result.get("DB_URL"));
        // The dash-prefixed arguments should be ignored as they're not valid parameter names
    }

    @Test
    void testHandleHelpAndVersionWithHelp() {
        String[] args = {"--help"};
        boolean result = CliUtils.handleHelpAndVersion(args);
        
        assertTrue(result);
        String output = outputStream.toString();
        assertTrue(output.contains("DBChat"));
        assertTrue(output.contains("Usage:"));
        assertTrue(output.contains("OPTIONS:"));
    }

    @Test
    void testHandleHelpAndVersionWithShortHelp() {
        String[] args = {"-h"};
        boolean result = CliUtils.handleHelpAndVersion(args);
        
        assertTrue(result);
        String output = outputStream.toString();
        assertTrue(output.contains("DBChat"));
        assertTrue(output.contains("Usage:"));
    }

    @Test
    void testHandleHelpAndVersionWithVersion() {
        String[] args = {"--version"};
        boolean result = CliUtils.handleHelpAndVersion(args);
        
        assertTrue(result);
        String output = outputStream.toString();
        assertTrue(output.contains("DBChat"));
        assertTrue(output.contains("Secure MCP server"));
        assertTrue(output.contains("MCP Protocol Version:"));
        assertTrue(output.contains("JDBC Drivers:"));
    }

    @Test
    void testHandleHelpAndVersionWithShortVersion() {
        String[] args = {"-v"};
        boolean result = CliUtils.handleHelpAndVersion(args);
        
        assertTrue(result);
        String output = outputStream.toString();
        assertTrue(output.contains("Version:"));
    }

    @Test
    void testHandleHelpAndVersionWithNoHelpOrVersion() {
        String[] args = {"--db_url=test", "-p=8080"};
        boolean result = CliUtils.handleHelpAndVersion(args);
        
        assertFalse(result);
        String output = outputStream.toString();
        assertTrue(output.isEmpty());
    }

    @Test
    void testDisplayHelp() {
        CliUtils.displayHelp();
        String output = outputStream.toString();
        
        // Check for key sections
        assertTrue(output.contains("DBChat"));
        assertTrue(output.contains("Usage:"));
        assertTrue(output.contains("ARGUMENT FORMATS:"));
        assertTrue(output.contains("OPTIONS:"));
        assertTrue(output.contains("DATABASE CONFIGURATION:"));
        assertTrue(output.contains("CONNECTION POOL SETTINGS:"));
        assertTrue(output.contains("QUERY SETTINGS:"));
        assertTrue(output.contains("EXAMPLES:"));
        
        // Check for specific options
        assertTrue(output.contains("-h, --help"));
        assertTrue(output.contains("-v, --version"));
        assertTrue(output.contains("-u, --db_url"));
        assertTrue(output.contains("-p, --http_port"));
        
        // Check for examples
        assertTrue(output.contains("java -jar"));
        assertTrue(output.contains("-p=8080"));
        assertTrue(output.contains("--http_port=8080"));
    }

    @Test
    void testDisplayVersion() {
        CliUtils.displayVersion();
        String output = outputStream.toString();
        
        // Check for version information
        assertTrue(output.contains("DBChat"));
        assertTrue(output.contains("MCP Protocol Version:"));
        assertTrue(output.contains("Java Version:"));
        assertTrue(output.contains("Java Vendor:"));
        
        // Check for JDBC drivers section
        assertTrue(output.contains("JDBC Drivers:"));

        // Check for features
        assertTrue(output.contains("Features:"));
        assertTrue(output.contains("(MCP) compliance"));
        assertTrue(output.contains("Security-hardened"));
    }

    @Test
    void testGetDriverVersionInfo() {
        // Test with H2 driver (likely to be available in test environment)
        String versionInfo = CliUtils.getDriverVersionInfo("org.h2.Driver");
        
        // Should either return version info or empty string
        assertTrue(versionInfo.isEmpty() || versionInfo.matches(" v\\d+\\.\\d+"));
        
        // Test with non-existent driver
        String nonExistentVersion = CliUtils.getDriverVersionInfo("com.nonexistent.Driver");
        assertEquals("", nonExistentVersion);
    }

    @Test
    void testCaseInsensitiveArgs() {
        String[] args = {"--DB_URL=test", "--Http_Port=8080"};
        Map<String, String> result = CliUtils.parseArgs(args);
        
        assertEquals("test", result.get("DB_URL"));
        assertEquals("8080", result.get("HTTP_PORT"));
    }

    @Test
    void testComplexArgumentParsing() {
        String[] args = {
            "--config_file=/path/to/config.conf",
            "-u", "jdbc:postgresql://localhost:5432/mydb?ssl=true&sslmode=require",
            "--db_user=admin", 
            "-P", "complex!@#$%^&*()password",
            "-m", // flag
            "--http_port", "9090",
            "-q=60"
        };
        
        Map<String, String> result = CliUtils.parseArgs(args);
        
        assertEquals("/path/to/config.conf", result.get("CONFIG_FILE"));
        assertEquals("jdbc:postgresql://localhost:5432/mydb?ssl=true&sslmode=require", result.get("DB_URL"));
        assertEquals("admin", result.get("DB_USER"));
        assertEquals("complex!@#$%^&*()password", result.get("DB_PASSWORD"));
        assertEquals("true", result.get("HTTP_MODE"));
        assertEquals("9090", result.get("HTTP_PORT"));
        assertEquals("60", result.get("QUERY_TIMEOUT_SECONDS"));
    }
}