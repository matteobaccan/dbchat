package com.skanga.mcp.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ResourceManager utility class.
 * Tests resource loading, caching, parameter formatting, and error handling.
 */
class ResourceManagerTest {

    private static final Logger logger = LoggerFactory.getLogger(ResourceManagerTest.class);

    @BeforeEach
    void setUp() throws Exception {
        // Clear the cache before each test to ensure isolation
        clearResourceManagerCache();
    }

    /**
     * Helper method to clear the ResourceManager cache using reflection
     */
    private void clearResourceManagerCache() throws Exception {
        ResourceManager.propertiesCache.clear();
    }

    @Nested
    @DisplayName("Database Help Content Tests")
    class DatabaseHelpTests {

        @Test
        @DisplayName("Should load MySQL dialect guidance successfully")
        void testGetDatabaseHelp_MySQL_DialectGuidance() {
            String result = ResourceManager.getDatabaseHelp("mysql", ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);

            assertNotNull(result);
            assertFalse(result.contains("mysql specific help not available"));
            assertTrue(result.contains("MySql Database"));
            assertTrue(result.contains("backticks"));
            assertTrue(result.contains("NOW()"));
            assertTrue(result.contains("AUTO_INCREMENT"));
        }

        @Test
        @DisplayName("Should load PostgreSQL query examples successfully")
        void testGetDatabaseHelp_PostgreSQL_QueryExamples() {
            String result = ResourceManager.getDatabaseHelp("postgresql", ResourceManager.DatabaseHelp.QUERY_EXAMPLES);

            assertNotNull(result);
            assertFalse(result.contains("postgresql specific help not available"));
            assertTrue(result.contains("CURRENT_DATE"));
            assertTrue(result.contains("INTERVAL"));
            assertTrue(result.contains("TO_CHAR"));
        }

        @Test
        @DisplayName("Should load SQL Server data type info successfully")
        void testGetDatabaseHelp_SQLServer_DataTypeInfo() {
            String result = ResourceManager.getDatabaseHelp("sqlserver", ResourceManager.DatabaseHelp.DATATYPE_INFO);

            assertNotNull(result);
            assertFalse(result.contains("sqlserver specific help not available"));
            assertTrue(result.contains("VARCHAR"));
            assertTrue(result.contains("UNIQUEIDENTIFIER"));
            assertTrue(result.contains("DATETIME2"));
        }

        @Test
        @DisplayName("Should load H2 content successfully")
        void testGetDatabaseHelp_H2_AllTypes() {
            String dialectGuidance = ResourceManager.getDatabaseHelp("h2", ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);
            String queryExamples = ResourceManager.getDatabaseHelp("h2", ResourceManager.DatabaseHelp.QUERY_EXAMPLES);
            String dataTypeInfo = ResourceManager.getDatabaseHelp("h2", ResourceManager.DatabaseHelp.DATATYPE_INFO);

            // Dialect guidance
            assertTrue(dialectGuidance.contains("H2 Database"));
            assertTrue(dialectGuidance.contains("MySQL/PostgreSQL compatible"));
            assertTrue(dialectGuidance.contains("LIMIT count OFFSET offset"));

            // Query examples
            assertTrue(queryExamples.contains("NOW()"));
            assertTrue(queryExamples.contains("FORMATDATETIME"));

            // Data types
            assertTrue(dataTypeInfo.contains("VARCHAR"));
            assertTrue(dataTypeInfo.contains("UUID"));
        }

        @Test
        @DisplayName("Should handle case insensitive database types")
        void testGetDatabaseHelp_CaseInsensitive() {
            String lowerCase = ResourceManager.getDatabaseHelp("mysql", ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);
            String upperCase = ResourceManager.getDatabaseHelp("MYSQL", ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);
            String mixedCase = ResourceManager.getDatabaseHelp("MySQL", ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);

            assertEquals(lowerCase, upperCase);
            assertEquals(lowerCase, mixedCase);
        }

        @Test
        @DisplayName("Should return fallback message for unknown database type")
        void testGetDatabaseHelp_UnknownDatabase() {
            String result = ResourceManager.getDatabaseHelp("unknown", ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);

            assertNotNull(result);
            assertTrue(result.contains("unknown specific help not available"));
            assertTrue(result.contains("Use standard SQL syntax"));
        }

        @Test
        @DisplayName("Should return fallback message for unknown help type")
        void testGetDatabaseHelp_UnknownHelpType() {
            String result = ResourceManager.getDatabaseHelp("mysql", "unknown.help.type");

            assertNotNull(result);
            assertTrue(result.contains("mysql specific help not available"));
        }
    }

    @Nested
    @DisplayName("Security Warning Tests")
    class SecurityWarningTests {

        @Test
        @DisplayName("Should load and format security warnings with parameters")
        void testGetSecurityWarning_WithParameters() {
            // This test assumes you have a security-warning-template.properties file
            // You'll need to create it for this test to pass
            String result = ResourceManager.getSecurityWarning("test.warning", "MYSQL", "SELECT-ONLY");

            assertNotNull(result);
            // The exact assertion depends on your security-warning-template.properties content
            // For now, just verify it's not the fallback message
            assertFalse(result.contains("Security warning template not found"));
        }

        @Test
        @DisplayName("Should return fallback message for unknown warning type")
        void testGetSecurityWarning_UnknownWarningType() {
            String result = ResourceManager.getSecurityWarning("unknown.warning.type", "param1", "param2");

            assertNotNull(result);
            assertTrue(result.contains("Security warning template not found"));
        }

        @Test
        @DisplayName("Should handle empty parameters")
        void testGetSecurityWarning_EmptyParameters() {
            String result = ResourceManager.getSecurityWarning("test.warning");

            assertNotNull(result);
            // Should not throw exception even with no parameters
        }
    }

    @Nested
    @DisplayName("Error Message Tests")
    class ErrorMessageTests {

        @Test
        @DisplayName("Should load and format error messages with parameters")
        void testGetErrorMessage_WithParameters() {
            // This test assumes you have an error-messages.properties file
            String result = ResourceManager.getErrorMessage("test.error", "8080", "already in use");

            assertNotNull(result);
            // The exact assertion depends on your error-messages.properties content
            assertFalse(result.contains("Error message not found"));
        }

        @Test
        @DisplayName("Should return fallback message for unknown error key")
        void testGetErrorMessage_UnknownErrorKey() {
            String unknownKey = "completely.unknown.error.key";
            String result = ResourceManager.getErrorMessage(unknownKey, "param1");

            assertNotNull(result);
            assertTrue(result.contains("Error message not found: " + unknownKey));
        }

        @Test
        @DisplayName("Should handle special characters in parameters")
        void testGetErrorMessage_SpecialCharacters() {
            String result = ResourceManager.getErrorMessage("test.error", "param with spaces", "param'with'quotes");

            assertNotNull(result);
            // Should not throw exception with special characters
        }
    }

    @Nested
    @DisplayName("Caching Behavior Tests")
    class CachingTests {

        @Test
        @DisplayName("Should cache properties after first load")
        void testCaching_PropertiesAreCached() throws Exception {
            // Load the same resource twice
            String result1 = ResourceManager.getDatabaseHelp("mysql", ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);
            String result2 = ResourceManager.getDatabaseHelp("mysql", ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);

            // Results should be identical (same instance or equal content)
            assertEquals(result1, result2);

            // Verify cache contains the resource
            assertTrue(ResourceManager.propertiesCache.containsKey("db/mysql.properties"));
        }

        @Test
        @DisplayName("Should cache multiple different resources")
        void testCaching_MultipleDifferentResources() throws Exception {
            // Load different database types
            ResourceManager.getDatabaseHelp("mysql", ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);
            ResourceManager.getDatabaseHelp("postgresql", ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);
            ResourceManager.getDatabaseHelp("h2", ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);

            // Verify all are cached
            assertTrue(ResourceManager.propertiesCache.containsKey("db/mysql.properties"));
            assertTrue(ResourceManager.propertiesCache.containsKey("db/postgresql.properties"));
            assertTrue(ResourceManager.propertiesCache.containsKey("db/h2.properties"));
        }

        @Test
        @DisplayName("Should handle concurrent access to cache")
        void testCaching_ConcurrentAccess() throws Exception {
            // Simulate concurrent access
            Thread[] threads = new Thread[5];
            String[] results = new String[5];

            for (int i = 0; i < 5; i++) {
                final int index = i;
                threads[i] = new Thread(() -> {
                    results[index] = ResourceManager.getDatabaseHelp("mysql", ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);
                });
            }

            // Start all threads
            for (Thread thread : threads) {
                thread.start();
            }

            // Wait for all to complete
            for (Thread thread : threads) {
                thread.join();
            }

            // All results should be the same
            for (int i = 1; i < 5; i++) {
                assertEquals(results[0], results[i]);
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle null database type gracefully")
        void testGetDatabaseHelp_NullDatabaseType() {
            String result = ResourceManager.getDatabaseHelp(null, ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);

            assertNotNull(result);
            assertTrue(result.contains("null specific help not available"));
            assertTrue(result.contains("Use standard SQL syntax"));
        }

        @Test
        @DisplayName("Should handle null help type gracefully")
        void testGetDatabaseHelp_NullHelpType() {
            String result = ResourceManager.getDatabaseHelp("mysql", null);

            assertNotNull(result);
            assertTrue(result.contains("mysql specific help not available"));
            assertTrue(result.contains("Use standard SQL syntax"));
        }

        @Test
        @DisplayName("Should handle empty string parameters")
        void testGetDatabaseHelp_EmptyStrings() {
            String result1 = ResourceManager.getDatabaseHelp("", ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);
            String result2 = ResourceManager.getDatabaseHelp("mysql", "");

            assertNotNull(result1);
            assertNotNull(result2);

            // Empty database type should try to load "db/.properties" which won't exist
            assertTrue(result1.contains("specific help not available"));

            // Empty help type should return fallback message
            assertTrue(result2.contains("mysql specific help not available"));
        }

        @Test
        @DisplayName("Should handle parameter formatting errors gracefully")
        void testMessageFormatting_InvalidParameters() {
            // Test with a template that has placeholders but insufficient parameters
            // This assumes your error-messages.properties has a template with placeholders
            String result = ResourceManager.getErrorMessage("test.error.with.placeholders", "param1"); // Missing param2 and param3

            assertNotNull(result);
            // Should not throw exception and should either format partially or return unformatted template
            assertFalse(result.trim().isEmpty());
        }

        @Test
        @DisplayName("Should handle null parameters in error messages gracefully")
        void testGetErrorMessage_NullMessageKey() {
            String result = ResourceManager.getErrorMessage(null, "param1", "param2");

            assertNotNull(result);
            assertTrue(result.contains("Error message not found: null"));
        }

        @Test
        @DisplayName("Should handle null parameters in security warnings gracefully")
        void testGetSecurityWarning_NullWarningType() {
            String result = ResourceManager.getSecurityWarning(null, "param1", "param2");

            assertNotNull(result);
            assertTrue(result.contains("Security warning template not found"));
        }

        @Test
        @DisplayName("Should handle MessageFormat errors in security warnings")
        void testGetSecurityWarning_FormatError() {
            // Test with mismatched parameters - this should not throw an exception
            String result = ResourceManager.getSecurityWarning("test.warning"); // No parameters for a template that might expect them

            assertNotNull(result);
            // Should return the template even if formatting fails
        }
    }

    @Nested
    @DisplayName("Constants Validation Tests")
    class ConstantsTests {

        @Test
        @DisplayName("Should have all required DatabaseHelp constants")
        void testDatabaseHelpConstants() {
            assertNotNull(ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);
            assertNotNull(ResourceManager.DatabaseHelp.QUERY_EXAMPLES);
            assertNotNull(ResourceManager.DatabaseHelp.DATATYPE_INFO);

            assertEquals("dialect.guidance", ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);
            assertEquals("query.examples", ResourceManager.DatabaseHelp.QUERY_EXAMPLES);
            assertEquals("datatype.info", ResourceManager.DatabaseHelp.DATATYPE_INFO);
        }

        @Test
        @DisplayName("Should have all required SecurityWarnings constants")
        void testSecurityWarningsConstants() {
            assertNotNull(ResourceManager.SecurityWarnings.TOOL_QUERY_DESCRIPTION);
            assertNotNull(ResourceManager.SecurityWarnings.RESULT_HEADER);
            assertNotNull(ResourceManager.SecurityWarnings.RESULT_FOOTER);
            assertNotNull(ResourceManager.SecurityWarnings.RESOURCE_WRAPPER);

            assertEquals("tool.query.description", ResourceManager.SecurityWarnings.TOOL_QUERY_DESCRIPTION);
            assertEquals("result.header", ResourceManager.SecurityWarnings.RESULT_HEADER);
            assertEquals("result.footer", ResourceManager.SecurityWarnings.RESULT_FOOTER);
            assertEquals("resource.wrapper", ResourceManager.SecurityWarnings.RESOURCE_WRAPPER);
        }

        @Test
        @DisplayName("Should have all required ErrorMessages constants")
        void testErrorMessagesConstants() {
            assertNotNull(ResourceManager.ErrorMessages.CONFIG_ERROR_TITLE);
            assertNotNull(ResourceManager.ErrorMessages.STARTUP_PORT_INUSE);
            assertNotNull(ResourceManager.ErrorMessages.STARTUP_SOLUTIONS);

            assertEquals("config.error.title", ResourceManager.ErrorMessages.CONFIG_ERROR_TITLE);
            assertEquals("startup.port.inuse", ResourceManager.ErrorMessages.STARTUP_PORT_INUSE);
            assertEquals("startup.solutions", ResourceManager.ErrorMessages.STARTUP_SOLUTIONS);
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Should work end-to-end with all supported databases")
        void testEndToEnd_AllSupportedDatabases() {
            String[] databases = {"mysql", "postgresql", "sqlserver", "h2", "oracle", "sqlite"};
            String[] helpTypes = {
                    ResourceManager.DatabaseHelp.DIALECT_GUIDANCE,
                    ResourceManager.DatabaseHelp.QUERY_EXAMPLES,
                    ResourceManager.DatabaseHelp.DATATYPE_INFO
            };

            for (String db : databases) {
                for (String helpType : helpTypes) {
                    String result = ResourceManager.getDatabaseHelp(db, helpType);
                    assertNotNull(result, String.format("Failed for %s - %s", db, helpType));
                    assertFalse(result.trim().isEmpty(), String.format("Empty result for %s - %s", db, helpType));
                }
            }
        }

        @Test
        @DisplayName("Should demonstrate typical usage patterns")
        void testTypicalUsagePatterns() {
            // Pattern 1: Getting SQL syntax help for error messages
            String mysqlHelp = ResourceManager.getDatabaseHelp("mysql", ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);
            assertTrue(mysqlHelp.contains("backticks"));

            // Pattern 2: Getting examples for user documentation
            String pgExamples = ResourceManager.getDatabaseHelp("postgresql", ResourceManager.DatabaseHelp.QUERY_EXAMPLES);
            assertTrue(pgExamples.contains("SELECT"));

            // Pattern 3: Getting data type info for validation
            String h2Types = ResourceManager.getDatabaseHelp("h2", ResourceManager.DatabaseHelp.DATATYPE_INFO);
            assertTrue(h2Types.contains("VARCHAR"));

            // All should be non-null and meaningful
            assertAll(
                    () -> assertFalse(mysqlHelp.contains("specific help not available")),
                    () -> assertFalse(pgExamples.contains("specific help not available")),
                    () -> assertFalse(h2Types.contains("specific help not available"))
            );
        }
    }
}