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
        ResourceManager.yamlCache.clear();
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
            // Test with actual security warning that has parameters
            String result = ResourceManager.getSecurityWarning("tool.run_sql.description", 
                "MYSQL", "restriction text", "SELECT-ONLY", "mysql", "30", "10000", "1000", "MySQL");

            assertNotNull(result);
            assertFalse(result.contains("Security warning template not found"));
            
            // Verify that parameters were substituted
            assertTrue(result.contains("MYSQL"), "Should contain database type parameter");
            assertTrue(result.contains("SELECT-ONLY"), "Should contain mode parameter");
            assertTrue(result.contains("30"), "Should contain timeout parameter");
            assertTrue(result.contains("CRITICAL SECURITY WARNING"), "Should contain warning header");
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
            // Test with a simple security warning that doesn't require parameters
            String result = ResourceManager.getSecurityWarning("restriction.select_only");

            assertNotNull(result);
            assertFalse(result.contains("Security warning template not found"));
            assertTrue(result.contains("RESTRICTED MODE"), "Should contain the restriction description");
        }
    }

    @Nested
    @DisplayName("Error Message Tests")
    class ErrorMessageTests {

        @Test
        @DisplayName("Should load and format error messages with parameters")
        void testGetErrorMessage_WithParameters() {
            // Test with actual error message that has parameters
            String result = ResourceManager.getErrorMessage("startup.generic.error.reason", "Connection refused");

            assertNotNull(result);
            assertFalse(result.contains("Error message not found"));
            assertTrue(result.contains("Connection refused"), "Should contain the formatted parameter");
            assertTrue(result.contains("Reason:"), "Should contain the error message template text");
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
            String result = ResourceManager.getErrorMessage("startup.generic.error.reason", "param with spaces & param'with'quotes");

            assertNotNull(result);
            assertFalse(result.contains("Error message not found"));
            assertTrue(result.contains("param with spaces"), "Should handle spaces in parameters");
            assertTrue(result.contains("param'with'quotes"), "Should handle quotes in parameters");
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
            assertTrue(ResourceManager.yamlCache.containsKey("db/mysql.yaml"));
        }

        @Test
        @DisplayName("Should cache multiple different resources")
        void testCaching_MultipleDifferentResources() throws Exception {
            // Load different database types
            ResourceManager.getDatabaseHelp("mysql", ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);
            ResourceManager.getDatabaseHelp("postgresql", ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);
            ResourceManager.getDatabaseHelp("h2", ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);

            // Verify all are cached
            assertTrue(ResourceManager.yamlCache.containsKey("db/mysql.yaml"));
            assertTrue(ResourceManager.yamlCache.containsKey("db/postgresql.yaml"));
            assertTrue(ResourceManager.yamlCache.containsKey("db/h2.yaml"));
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
            // http.server.generic.error expects 2 parameters but we're only providing 1
            String result = ResourceManager.getErrorMessage("http.server.generic.error", "8080"); // Missing second parameter

            assertNotNull(result);
            // Should not throw exception and should return the unformatted template since formatting fails
            assertFalse(result.trim().isEmpty());
            assertTrue(result.contains("8080"), "Should contain the provided parameter");
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
            // tool.run_sql.description expects 8 parameters, but we're providing none
            String result = ResourceManager.getSecurityWarning("tool.run_sql.description"); // No parameters for a template that expects them

            assertNotNull(result);
            // Should return the template even if formatting fails - the unformatted template with {0}, {1}, etc.
            assertTrue(result.contains("CRITICAL SECURITY WARNING"), "Should contain the template content");
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
            assertNotNull(ResourceManager.SecurityWarnings.TOOL_RUN_SQL_DESCRIPTION);
            assertNotNull(ResourceManager.SecurityWarnings.RESULT_HEADER);
            assertNotNull(ResourceManager.SecurityWarnings.RESULT_FOOTER);
            assertNotNull(ResourceManager.SecurityWarnings.RESOURCE_WRAPPER);

            assertEquals("tool.run_sql.description", ResourceManager.SecurityWarnings.TOOL_RUN_SQL_DESCRIPTION);
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

    @Nested
    @DisplayName("Property File Validation Tests")
    class PropertyValidationTests {

        @Test
        @DisplayName("Should verify all error message keys have non-empty values")
        void testErrorMessagesHaveValues() {
            String[] errorMessageKeys = {
                "startup.config.error.title",
                "startup.config.error.format",
                "startup.port.error.title", 
                "startup.port.inuse",
                "startup.port.solutions",
                "startup.generic.error.title",
                "startup.generic.error.reason",
                "startup.unexpected.error",
                "http.server.port.inuse",
                "http.server.port.suggestion",
                "http.server.port.help",
                "http.server.generic.error",
                "lifecycle.not.initialized",
                "lifecycle.initializing",
                "lifecycle.shutdown",
                "protocol.unsupported.version",
                "protocol.method.not.found",
                "protocol.tool.unknown",
                "protocol.resource.not.found",
                "query.null",
                "query.empty",
                "query.too.long",
                "query.row.limit.exceeded",
                "sql.validation.empty",
                "sql.validation.operation.denied",
                "sql.validation.multiple.statements",
                "sql.validation.comments.denied",
                "database.driver.not.found",
                "database.pool.init.failed"
            };

            for (String key : errorMessageKeys) {
                String value = ResourceManager.getErrorMessage(key);
                assertNotNull(value, "Error message key '" + key + "' returned null");
                assertFalse(value.trim().isEmpty(), "Error message key '" + key + "' has empty value");
                assertFalse(value.contains("Error message not found"), 
                    "Error message key '" + key + "' not found in properties file");
            }
        }

        @Test
        @DisplayName("Should verify all security warning keys have non-empty values")
        void testSecurityWarningsHaveValues() {
            String[] securityWarningKeys = {
                "tool.run_sql.description",
                "sql.property.description",
                "maxrows.property.description",
                "result.header",
                "result.footer", 
                "resource.wrapper",
                "restriction.select_only",
                "restriction.unrestricted"
            };

            for (String key : securityWarningKeys) {
                String value = ResourceManager.getSecurityWarning(key);
                
                assertNotNull(value, "Security warning key '" + key + "' returned null");
                assertFalse(value.trim().isEmpty(), "Security warning key '" + key + "' has empty value");
                
                // Check if it's the fallback message - but be more specific about detection
                // The actual content should not be exactly the fallback message
                assertNotEquals("Security warning template not found", value,
                    "Security warning key '" + key + "' returned fallback message");
                
                // Also check it doesn't start with the fallback message text  
                assertFalse(value.startsWith("Security warning template not found"), 
                    "Security warning key '" + key + "' appears to be missing from properties file. Actual value: " + value);
            }
        }

        @Test
        @DisplayName("Should verify all database help keys have non-empty values for all supported databases")
        void testDatabaseHelpKeysHaveValues() {
            String[] databases = {"mysql", "postgresql", "sqlserver", "h2", "oracle", "sqlite", "mariadb"};
            String[] helpTypes = {
                ResourceManager.DatabaseHelp.DIALECT_GUIDANCE,
                ResourceManager.DatabaseHelp.QUERY_EXAMPLES,
                ResourceManager.DatabaseHelp.DATATYPE_INFO
            };

            for (String database : databases) {
                for (String helpType : helpTypes) {
                    String value = ResourceManager.getDatabaseHelp(database, helpType);
                    assertNotNull(value, 
                        String.format("Database help for '%s.%s' returned null", database, helpType));
                    assertFalse(value.trim().isEmpty(), 
                        String.format("Database help for '%s.%s' has empty value", database, helpType));
                    assertFalse(value.contains("specific help not available"), 
                        String.format("Database help for '%s.%s' not found in properties file", database, helpType));
                }
            }
        }

        @Test
        @DisplayName("Should verify property files are properly formatted and parseable")
        void testPropertyFilesAreWellFormed() {
            String[] resourceFiles = {
                "error-messages.yaml",
                "security-warning-template.yaml",
                "db/mysql.yaml",
                "db/postgresql.yaml", 
                "db/sqlserver.yaml",
                "db/h2.yaml",
                "db/oracle.yaml",
                "db/sqlite.yaml",
                "db/mariadb.yaml"
            };

            for (String resourceFile : resourceFiles) {
                // Access the load methods indirectly by accessing keys
                assertDoesNotThrow(() -> {
                    // Force loading of each resource file by accessing a key
                    if (resourceFile.equals("error-messages.yaml")) {
                        ResourceManager.getErrorMessage("startup.config.error.title");
                    } else if (resourceFile.equals("security-warning-template.yaml")) {
                        ResourceManager.getSecurityWarning("tool.run_sql.description");
                    } else if (resourceFile.startsWith("db/")) {
                        String dbType = resourceFile.substring(3, resourceFile.lastIndexOf('.'));
                        ResourceManager.getDatabaseHelp(dbType, ResourceManager.DatabaseHelp.DIALECT_GUIDANCE);
                    }
                }, "Resource file '" + resourceFile + "' should be properly formatted and parseable");
            }
        }

        @Test
        @DisplayName("Should verify no property values contain placeholder errors")
        void testNoPlaceholderErrors() {
            // Test a few key properties that use MessageFormat placeholders
            String queryDescription = ResourceManager.getSecurityWarning("tool.run_sql.description", 
                "MYSQL", "restriction text", "SELECT-ONLY", "mysql", "30", "10000", "1000", "MySQL");
            
            assertFalse(queryDescription.contains("{0}"), "Query description still contains unresolved placeholder {0}");
            assertFalse(queryDescription.contains("{1}"), "Query description still contains unresolved placeholder {1}");
            assertTrue(queryDescription.contains("MYSQL"), "Query description should contain formatted database type");
            assertTrue(queryDescription.contains("SELECT-ONLY"), "Query description should contain formatted mode");

            String genericError = ResourceManager.getErrorMessage("startup.generic.error.reason", "Test error message");
            assertFalse(genericError.contains("{0}"), "Generic error message still contains unresolved placeholder {0}");
            assertTrue(genericError.contains("Test error message"), "Generic error should contain formatted reason");
        }
    }
}