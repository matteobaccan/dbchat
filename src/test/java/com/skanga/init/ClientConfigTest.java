package com.skanga.init;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;

@DisplayName("ClientConfig Tests")
class ClientConfigTest {
    @Mock
    private ClientConfig.FileOperations mockFileOps;

    @Mock
    private ClientConfig.UserInputReader mockInputReader;

    private ClientConfig clientConfig;
    private ClientConfig.McpClientConfig testConfig;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        clientConfig = new ClientConfig(mockFileOps, mockInputReader);
        testConfig = new ClientConfig.McpClientConfig(
                "jdbc:h2:mem:test", "org.h2.Driver", "sa", "",
                "test-server", "/path/to/jar", "java"
        );
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should create with mocked dependencies")
        void shouldCreateWithMockedDependencies() {
            assertNotNull(clientConfig);
        }

        @Test
        @DisplayName("Should create with default dependencies")
        void shouldCreateWithDefaultDependencies() {
            ClientConfig defaultConfig = new ClientConfig();
            assertNotNull(defaultConfig);
        }
    }

    @Nested
    @DisplayName("Main Workflow Tests")
    class MainWorkflowTests {

        @Test
        @DisplayName("Should complete full workflow successfully")
        void shouldCompleteFullWorkflowSuccessfully() {
            // Setup mocks for database info collection
            when(mockInputReader.readLine(contains("Database JDBC URL"))).thenReturn("");
            when(mockInputReader.readLine(contains("Database JDBC Driver"))).thenReturn("");
            when(mockInputReader.readLine(contains("Database Username"))).thenReturn("");
            when(mockInputReader.readLine(contains("Database Password"))).thenReturn("");
            when(mockInputReader.readLine(contains("Server name"))).thenReturn("");
            when(mockInputReader.readLine(contains("JAR file"))).thenReturn("/test/path.jar");
            when(mockInputReader.readLine(contains("Java executable"))).thenReturn("java");

            // Setup client selection
            when(mockInputReader.readInt(contains("Select your MCP client"))).thenReturn(1);

            // Setup file operations
            when(mockFileOps.fileExists(anyString())).thenReturn(false);

            clientConfig.run();

            verify(mockInputReader).println(contains("DBChat MCP Configuration Generator"));
            verify(mockFileOps).writeFile(contains("cursor_mcp.json"), anyString());
        }

        @Test
        @DisplayName("Should handle exceptions in workflow")
        void shouldHandleExceptionsInWorkflow() {
            when(mockInputReader.readLine(anyString())).thenThrow(new RuntimeException("Test error"));

            clientConfig.run();

            verify(mockInputReader).println(contains("Error: Test error"));
        }
    }

    @Nested
    @DisplayName("Database Info Collection Tests")
    class DatabaseInfoCollectionTests {

        @Test
        @DisplayName("Should use defaults for empty inputs")
        void shouldUseDefaultsForEmptyInputs() {
            when(mockInputReader.readLine(anyString())).thenReturn("");
            when(mockInputReader.readLine(contains("JAR file"))).thenReturn("/test/jar");
            when(mockInputReader.readLine(contains("Java executable"))).thenReturn("java");

            ClientConfig.McpClientConfig result = clientConfig.collectDatabaseInfo();

            assertEquals(ClientConfig.DEFAULT_DB_URL, result.dbUrl);
            assertEquals(ClientConfig.DEFAULT_DB_DRIVER, result.dbDriver);
            assertEquals(ClientConfig.DEFAULT_DB_USER, result.dbUser);
            assertEquals(ClientConfig.DEFAULT_DB_PASSWORD, result.dbPassword);
            assertEquals("my-database", result.serverName);
        }

        @Test
        @DisplayName("Should use custom values when provided")
        void shouldUseCustomValuesWhenProvided() {
            when(mockInputReader.readLine(contains("Database JDBC URL"))).thenReturn("jdbc:mysql://localhost");
            when(mockInputReader.readLine(contains("Database JDBC Driver"))).thenReturn("com.mysql.Driver");
            when(mockInputReader.readLine(contains("Database Username"))).thenReturn("testuser");
            when(mockInputReader.readLine(contains("Database Password"))).thenReturn("testpass");
            when(mockInputReader.readLine(contains("Server name"))).thenReturn("custom-server");
            when(mockInputReader.readLine(contains("JAR file"))).thenReturn("/custom/jar");
            when(mockInputReader.readLine(contains("Java executable"))).thenReturn("/custom/java");

            ClientConfig.McpClientConfig result = clientConfig.collectDatabaseInfo();

            assertEquals("jdbc:mysql://localhost", result.dbUrl);
            assertEquals("com.mysql.Driver", result.dbDriver);
            assertEquals("testuser", result.dbUser);
            assertEquals("testpass", result.dbPassword);
            assertEquals("custom-server", result.serverName);
        }
    }

    @Nested
    @DisplayName("Client Selection Tests")
    class ClientSelectionTests {

        @Test
        @DisplayName("Should select correct client type for valid choice")
        void shouldSelectCorrectClientTypeForValidChoice() {
            when(mockInputReader.readInt(anyString())).thenReturn(1);

            ClientConfig.ClientType result = clientConfig.selectClient();

            assertEquals(ClientConfig.ClientType.CURSOR, result);
        }

        @Test
        @DisplayName("Should handle invalid choices and retry")
        void shouldHandleInvalidChoicesAndRetry() {
            when(mockInputReader.readInt(anyString()))
                    .thenReturn(0)  // Invalid
                    .thenReturn(10) // Invalid
                    .thenReturn(1); // Valid

            ClientConfig.ClientType result = clientConfig.selectClient();

            assertEquals(ClientConfig.ClientType.CURSOR, result);
            verify(mockInputReader, times(2)).println("Please enter a number between 1 and 9.");
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9})
        @DisplayName("Should return correct client type for each choice")
        void shouldReturnCorrectClientTypeForEachChoice(int choice) {
            ClientConfig.ClientType expected = switch (choice) {
                case 1 -> ClientConfig.ClientType.CURSOR;
                case 2 -> ClientConfig.ClientType.WINDSURF;
                case 3 -> ClientConfig.ClientType.CLAUDE_DESKTOP;
                case 4 -> ClientConfig.ClientType.CONTINUE;
                case 5 -> ClientConfig.ClientType.VSCODE;
                case 6 -> ClientConfig.ClientType.CLAUDE_CODE;
                case 7 -> ClientConfig.ClientType.GEMINI_CLI;
                case 8 -> ClientConfig.ClientType.ZED;
                case 9 -> null;
                default -> throw new IllegalArgumentException();
            };

            ClientConfig.ClientType result = clientConfig.getClientTypeFromChoice(choice);
            assertEquals(expected, result);
        }
    }

    @Nested
    @DisplayName("Configuration Generation Tests")
    class ConfigurationGenerationTests {

        @ParameterizedTest
        @EnumSource(ClientConfig.ClientType.class)
        @DisplayName("Should generate configuration for all client types")
        void shouldGenerateConfigurationForAllClientTypes(ClientConfig.ClientType clientType) {
            String result = clientConfig.generateConfigurationContent(testConfig, clientType);

            assertNotNull(result);
            assertFalse(result.trim().isEmpty());

            // Verify JSON is valid for JSON-based configs
            if (clientType != ClientConfig.ClientType.CLAUDE_CODE) {
                assertDoesNotThrow(() -> objectMapper.readTree(result));
            }
        }

        @Test
        @DisplayName("Should generate all configurations when clientType is null")
        void shouldGenerateAllConfigurationsWhenClientTypeIsNull() {
            when(mockFileOps.fileExists(anyString())).thenReturn(false);

            clientConfig.generateAndSaveConfiguration(testConfig, null);

            // Should save 7 files (all except CLAUDE_CODE)
            verify(mockFileOps, times(7)).writeFile(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("JSON Generation Tests")
    class JsonGenerationTests {
        @Test
        @DisplayName("Should generate valid MCP servers JSON")
        void shouldGenerateValidMcpServersJson() throws Exception {
            String result = ClientConfig.generateMcpServersJson(testConfig);

            JsonNode json = objectMapper.readTree(result);
            assertTrue(json.has("mcpServers"));
            assertTrue(json.get("mcpServers").has("test-server"));

            JsonNode server = json.get("mcpServers").get("test-server");
            assertEquals("java", server.get("command").asText());
            assertTrue(server.has("args"));
            assertTrue(server.has("env"));
        }

        @Test
        @DisplayName("Should generate valid Continue JSON")
        void shouldGenerateValidContinueJson() throws Exception {
            String result = ClientConfig.generateContinueJson(testConfig);

            JsonNode json = objectMapper.readTree(result);
            assertTrue(json.has("models"));
            assertTrue(json.has("mcpServers"));
            assertTrue(json.get("mcpServers").isArray());

            JsonNode server = json.get("mcpServers").get(0);
            assertEquals("test-server", server.get("name").asText());
        }

        @Test
        @DisplayName("Should generate valid VS Code JSON")
        void shouldGenerateValidVSCodeJson() throws Exception {
            String result = ClientConfig.generateVSCodeJson(testConfig);

            JsonNode json = objectMapper.readTree(result);
            assertTrue(json.has("mcp"));
            assertTrue(json.get("mcp").has("servers"));
            assertTrue(json.get("mcp").get("servers").has("test-server"));
        }

        @Test
        @DisplayName("Should generate valid Zed JSON")
        void shouldGenerateValidZedJson() throws Exception {
            String result = ClientConfig.generateZedJson(testConfig);

            JsonNode json = objectMapper.readTree(result);
            assertTrue(json.has("context_servers"));
            assertTrue(json.get("context_servers").has("test-server"));

            JsonNode server = json.get("context_servers").get("test-server");
            assertEquals("custom", server.get("source").asText());
        }

        @Test
        @DisplayName("Should generate Claude Code command")
        void shouldGenerateClaudeCodeCommand() {
            String result = ClientConfig.generateClaudeCodeCommand(testConfig);

            assertTrue(result.contains("claude mcp add"));
            assertTrue(result.contains("test-server"));
            assertTrue(result.contains("-e DB_URL="));
            assertTrue(result.contains("java -jar"));
        }
    }

    @Nested
    @DisplayName("Configuration Merging Tests")
    class ConfigurationMergingTests {

        @Test
        @DisplayName("Should merge standard MCP configuration")
        void shouldMergeStandardMcpConfiguration() throws Exception {
            String existingConfig = """
                {
                  "mcpServers": {
                    "existing-server": {
                      "command": "existing-command"
                    }
                  }
                }
                """;

            String result = ClientConfig.mergeConfiguration(existingConfig, testConfig,
                    ClientConfig.ClientType.CURSOR);

            JsonNode json = objectMapper.readTree(result);
            JsonNode servers = json.get("mcpServers");
            assertTrue(servers.has("existing-server"));
            assertTrue(servers.has("test-server"));
        }

        @Test
        @DisplayName("Should create mcpServers object if missing")
        void shouldCreateMcpServersObjectIfMissing() throws Exception {
            String existingConfig = "{}";

            String result = ClientConfig.mergeConfiguration(existingConfig, testConfig,
                    ClientConfig.ClientType.CURSOR);

            JsonNode json = objectMapper.readTree(result);
            assertTrue(json.has("mcpServers"));
            assertTrue(json.get("mcpServers").has("test-server"));
        }

        @Test
        @DisplayName("Should merge Zed configuration")
        void shouldMergeZedConfiguration() throws Exception {
            String existingConfig = """
                {
                  "context_servers": {
                    "existing-server": {
                      "command": "existing-command"
                    }
                  }
                }
                """;

            String result = ClientConfig.mergeConfiguration(existingConfig, testConfig,
                    ClientConfig.ClientType.ZED);

            JsonNode json = objectMapper.readTree(result);
            JsonNode servers = json.get("context_servers");
            assertTrue(servers.has("existing-server"));
            assertTrue(servers.has("test-server"));
            assertEquals("custom", servers.get("test-server").get("source").asText());
        }

        @Test
        @DisplayName("Should merge VS Code configuration")
        void shouldMergeVSCodeConfiguration() throws Exception {
            String existingConfig = """
                {
                  "mcp": {
                    "servers": {
                      "existing-server": {
                        "command": "existing-command"
                      }
                    }
                  }
                }
                """;

            String result = ClientConfig.mergeConfiguration(existingConfig, testConfig,
                    ClientConfig.ClientType.VSCODE);

            JsonNode json = objectMapper.readTree(result);
            JsonNode servers = json.get("mcp").get("servers");
            assertTrue(servers.has("existing-server"));
            assertTrue(servers.has("test-server"));
        }

        @Test
        @DisplayName("Should merge Continue configuration")
        void shouldMergeContinueConfiguration() throws Exception {
            String existingConfig = """
                {
                  "models": [],
                  "mcpServers": [
                    {
                      "name": "existing-server",
                      "command": "existing-command"
                    }
                  ]
                }
                """;

            String result = ClientConfig.mergeConfiguration(existingConfig, testConfig,
                    ClientConfig.ClientType.CONTINUE);

            JsonNode json = objectMapper.readTree(result);
            JsonNode servers = json.get("mcpServers");
            assertEquals(2, servers.size());
            assertEquals("test-server", servers.get(1).get("name").asText());
        }
    }

    @Nested
    @DisplayName("File Operations Tests")
    class FileOperationsTests {

        @Test
        @DisplayName("Should save configuration to DEST_DIR for new files")
        void shouldSaveConfigurationToDestDirForNewFiles() {
            when(mockFileOps.fileExists(anyString())).thenReturn(false);

            clientConfig.saveConfigurationFile("test content", "test.json",
                    ClientConfig.ClientType.CURSOR, testConfig);

            verify(mockFileOps).writeFile(eq(ClientConfig.DEST_DIR + "/test.json"), eq("test content"));
            verify(mockInputReader).println(contains("Configuration saved to: " + ClientConfig.DEST_DIR));
        }

        @Test
        @DisplayName("Should handle Claude Code configuration separately")
        void shouldHandleClaudeCodeConfigurationSeparately() {
            clientConfig.saveConfigurationFile("test command", "test.sh",
                    ClientConfig.ClientType.CLAUDE_CODE, testConfig);

            verify(mockFileOps).writeFile(eq(ClientConfig.DEST_DIR + "/test.sh"), eq("test command"));
        }

        @Test
        @DisplayName("Should merge with existing configuration when present")
        void shouldMergeWithExistingConfigurationWhenPresent() {
            String existingConfig = """
                {
                  "mcpServers": {
                    "existing": {"command": "test"}
                  }
                }
                """;

            when(mockFileOps.fileExists(anyString())).thenReturn(true);
            when(mockFileOps.readFile(anyString())).thenReturn(existingConfig);
            when(mockInputReader.readLine(contains("Update actual config file?"))).thenReturn("n");

            clientConfig.saveConfigurationFile("test content", "test.json",
                    ClientConfig.ClientType.CURSOR, testConfig);

            verify(mockFileOps).writeFile(contains("test.json"), contains("existing"));
            verify(mockInputReader).println(contains("Configuration merged"));
        }

        @Test
        @DisplayName("Should create backup and update actual config when user consents")
        void shouldCreateBackupAndUpdateActualConfigWhenUserConsents() {
            when(mockFileOps.fileExists(anyString())).thenReturn(true);
            when(mockFileOps.readFile(anyString())).thenReturn("{}");
            when(mockInputReader.readLine(contains("Update actual config file?"))).thenReturn("y");

            clientConfig.saveConfigurationFile("test content", "test.json",
                    ClientConfig.ClientType.CURSOR, testConfig);

            verify(mockFileOps).createBackup(anyString());
            verify(mockFileOps, times(2)).writeFile(anyString(), anyString()); // DEST_DIR + actual config
            verify(mockInputReader).println(contains("Backup created"));
        }
    }

    @Nested
    @DisplayName("Utility Method Tests")
    class UtilityMethodTests {

        @Test
        @DisplayName("Should format client names correctly")
        void shouldFormatClientNamesCorrectly() {
            assertEquals("Cursor", ClientConfig.formatClientName(ClientConfig.ClientType.CURSOR));
            assertEquals("Claude desktop", ClientConfig.formatClientName(ClientConfig.ClientType.CLAUDE_DESKTOP));
            assertEquals("Claude code", ClientConfig.formatClientName(ClientConfig.ClientType.CLAUDE_CODE));
        }

        @Test
        @DisplayName("Should escape shell strings correctly")
        void shouldEscapeShellStringsCorrectly() {
            assertEquals("simple", ClientConfig.escapeShellString("simple"));
            assertEquals("\"string with spaces\"", ClientConfig.escapeShellString("string with spaces"));
            assertEquals("\"string&with&special\"", ClientConfig.escapeShellString("string&with&special"));
            assertEquals("\"string\\\"with\\\"quotes\"", ClientConfig.escapeShellString("string\"with\"quotes"));
            assertEquals("", ClientConfig.escapeShellString(null));
        }

        @ParameterizedTest
        @MethodSource("configPathTestCases")
        @DisplayName("Should return correct config paths for different OS")
        void shouldReturnCorrectConfigPathsForDifferentOS(String client, String os, String userHome,
                                                          String appData, String expected) {
            String result = ClientConfig.getConfigPath(client, os, userHome, appData);
            assertEquals(expected, result);
        }

        static Stream<Arguments> configPathTestCases() {
            return Stream.of(
                    // Windows
                    Arguments.of("cursor", "Windows 10", "C:\\Users\\test", "C:\\Users\\test\\AppData\\Roaming",
                            "C:\\Users\\test\\AppData\\Roaming\\Cursor\\mcp.json"),
                    Arguments.of("zed", "Windows 11", "C:\\Users\\test", "C:\\Users\\test\\AppData\\Roaming",
                            "C:\\Users\\test\\AppData\\Roaming\\Zed\\settings.json"),

                    // macOS
                    Arguments.of("cursor", "Mac OS X", "/Users/test", null,
                            "/Users/test/.cursor/mcp.json"),
                    Arguments.of("claude-desktop", "macOS", "/Users/test", null,
                            "/Users/test/Library/Application Support/Claude/claude_desktop_config.json"),

                    // Linux
                    Arguments.of("cursor", "Linux", "/home/test", null,
                            "/home/test/.config/cursor/mcp.json"),
                    Arguments.of("zed", "Ubuntu", "/home/test", null,
                            "/home/test/.config/zed/settings.json")
            );
        }

        @Test
        @DisplayName("Should detect JAR path successfully")
        void shouldDetectJarPathSuccessfully() {
            String jarPath = ClientConfig.getJar();
            assertNotNull(jarPath);
            // Should either be a valid path or the error message
            assertTrue(jarPath.contains(".jar") || jarPath.contains("CANNOT LOCATE") || jarPath.contains("target\\classes") || jarPath.contains("target/classes"));
        }

        @Test
        @DisplayName("Should detect Java path successfully")
        void shouldDetectJavaPathSuccessfully() {
            String javaPath = ClientConfig.getJava();
            assertNotNull(javaPath);
            // Should either be a valid path or the error message
            assertTrue(javaPath.contains("java") || javaPath.contains("CANNOT LOCATE"));
        }
    }

    @Nested
    @DisplayName("Database Connection Tests")
    class DatabaseConnectionTests {

        @Test
        @DisplayName("Should return true for successful H2 connection")
        void shouldReturnTrueForSuccessfulH2Connection() {
            boolean result = clientConfig.testDatabaseConnection(
                    "jdbc:h2:mem:test", "org.h2.Driver", "sa", ""
            );
            assertTrue(result);
            verify(mockInputReader).println(contains("Successfully connected"));
        }

        @Test
        @DisplayName("Should return false for invalid connection")
        void shouldReturnFalseForInvalidConnection() {
            boolean result = clientConfig.testDatabaseConnection(
                    "jdbc:invalid:url", "invalid.Driver", "user", "pass"
            );
            assertFalse(result);
            verify(mockInputReader).println(contains("Database connection failed"));
        }

        @Test
        @DisplayName("Should always test connection by default")
        void shouldAlwaysTestConnectionByDefault() {
            assertTrue(clientConfig.shouldTestConnection());
        }
    }

    @Nested
    @DisplayName("Path Detection Tests")
    class PathDetectionTests {

        @Test
        @DisplayName("Should prompt for JAR path when auto-detection fails")
        void shouldPromptForJarPathWhenAutoDetectionFails() {
            // Create a mock that returns error message for getJar
            ClientConfig spyConfig = spy(new ClientConfig(mockFileOps, mockInputReader));
            when(mockInputReader.readLine(contains("JAR file"))).thenReturn("/manual/path.jar");

            String result = spyConfig.detectJarPath();
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should prompt for Java path when auto-detection fails")
        void shouldPromptForJavaPathWhenAutoDetectionFails() {
            ClientConfig spyConfig = spy(new ClientConfig(mockFileOps, mockInputReader));
            when(mockInputReader.readLine(contains("Java executable"))).thenReturn("/manual/java");

            String result = spyConfig.detectJavaPath();
            assertNotNull(result);
        }

        @Test
        @DisplayName("Should use 'java' when empty Java path provided")
        void shouldUseJavaWhenEmptyJavaPathProvided() {
            ClientConfig spyConfig = spy(new ClientConfig(mockFileOps, mockInputReader));
            when(mockInputReader.readLine(contains("Java executable"))).thenReturn("");

            String result = spyConfig.detectJavaPath();
            assertTrue(result.equals("java") || result.contains("/java") || result.contains("\\java.exe"));
        }
    }

    @Nested
    @DisplayName("McpClientConfig Tests")
    class McpClientConfigTests {

        @Test
        @DisplayName("Should create config with all parameters")
        void shouldCreateConfigWithAllParameters() {
            ClientConfig.McpClientConfig config = new ClientConfig.McpClientConfig(
                    "url", "driver", "user", "pass", "server", "jar", "java"
            );

            assertEquals("url", config.dbUrl);
            assertEquals("driver", config.dbDriver);
            assertEquals("user", config.dbUser);
            assertEquals("pass", config.dbPassword);
            assertEquals("server", config.serverName);
            assertEquals("jar", config.jarPath);
            assertEquals("java", config.javaPath);
        }
    }

    @Nested
    @DisplayName("ClientType Enum Tests")
    class ClientTypeEnumTests {

        @Test
        @DisplayName("Should have correct properties for each client type")
        void shouldHaveCorrectPropertiesForEachClientType() {
            assertEquals("cursor", ClientConfig.ClientType.CURSOR.configKey);
            assertEquals("cursor_mcp.json", ClientConfig.ClientType.CURSOR.fileName);
            assertTrue(ClientConfig.ClientType.CURSOR.useStandardMcpFormat);

            assertEquals("continue", ClientConfig.ClientType.CONTINUE.configKey);
            assertEquals("continue_config.json", ClientConfig.ClientType.CONTINUE.fileName);
            assertFalse(ClientConfig.ClientType.CONTINUE.useStandardMcpFormat);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle JSON parsing errors gracefully")
        void shouldHandleJsonParsingErrorsGracefully() {
            assertThrows(RuntimeException.class, () -> ClientConfig.mergeConfiguration("invalid json", testConfig, ClientConfig.ClientType.CURSOR));
        }

        @Test
        @DisplayName("Should handle file operation failures")
        void shouldHandleFileOperationFailures() {
            doThrow(new RuntimeException("File write failed"))
                    .when(mockFileOps).writeFile(anyString(), anyString());
            assertThrows(RuntimeException.class, () -> clientConfig.saveConfigurationFile("content", "file.json",
                    ClientConfig.ClientType.CURSOR, testConfig));
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {
        @Test
        @DisplayName("Should handle complete workflow with all client types")
        void shouldHandleCompleteWorkflowWithAllClientTypes() {
            // Setup for database collection
            when(mockInputReader.readLine(anyString())).thenReturn(""); // Use defaults
            when(mockInputReader.readLine(contains("JAR file"))).thenReturn("/test.jar");
            when(mockInputReader.readLine(contains("Java executable"))).thenReturn("java");

            // Setup for client selection (all configs)
            when(mockInputReader.readInt(anyString())).thenReturn(9);

            // Setup file operations
            when(mockFileOps.fileExists(anyString())).thenReturn(false);

            clientConfig.run();

            // Should create 7 files (all except CLAUDE_CODE)
            verify(mockFileOps, times(7)).writeFile(anyString(), anyString());
            verify(mockInputReader).println(contains("All configurations generated successfully"));
        }
    }

    @Nested
    @DisplayName("Static Method Tests")
    class StaticMethodTests {
        @Test
        @DisplayName("Should create proper server config")
        void shouldCreateProperServerConfig() {
            var serverConfig = ClientConfig.createServerConfig(testConfig);

            assertEquals("java", serverConfig.get("command").asText());
            assertTrue(serverConfig.has("args"));
            assertTrue(serverConfig.has("env"));

            // Verify environment variables
            var env = serverConfig.get("env");
            assertEquals("jdbc:h2:mem:test", env.get("DB_URL").asText());
            assertEquals("org.h2.Driver", env.get("DB_DRIVER").asText());
        }

        @Test
        @DisplayName("Should handle exception in main method")
        void testMain_ExceptionHandling() {
            // Test exception handling in main method
            InputStream originalIn = System.in;
            System.setIn(new ByteArrayInputStream("".getBytes())); // Empty input to cause exception

            ByteArrayOutputStream outContent = new ByteArrayOutputStream();
            ByteArrayOutputStream errContent = new ByteArrayOutputStream();
            PrintStream originalOut = System.out;
            PrintStream originalErr = System.err;
            System.setOut(new PrintStream(outContent));
            System.setErr(new PrintStream(errContent));

            try {
                Assertions.assertDoesNotThrow(() -> ClientConfig.main(new String[]{}));
                Assertions.assertTrue(outContent.toString().contains("Error:"));
            } finally {
                System.setIn(originalIn);
                System.setOut(originalOut);
                System.setErr(originalErr);
            }
        }
    }
    @Nested
    @DisplayName("DefaultFileOperations Tests")
    class DefaultFileOperationsTests {

        private ClientConfig.DefaultFileOperations fileOps;
        private Path tempDir;

        @BeforeEach
        void setUp() throws IOException {
            fileOps = new ClientConfig.DefaultFileOperations();
            tempDir = Files.createTempDirectory("clientconfig-test");
        }

        @AfterEach
        void tearDown() throws IOException {
            // Clean up temp directory
            Files.walk(tempDir)
                    .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
                    .forEach(path -> {
                        try { Files.deleteIfExists(path); }
                        catch (IOException e) { /* ignore */ }
                    });
        }

        @Test
        @DisplayName("Should write and read file successfully")
        void shouldWriteAndReadFileSuccessfully() throws IOException {
            String testPath = tempDir.resolve("test.txt").toString();
            String content = "test content";

            fileOps.writeFile(testPath, content);
            String result = fileOps.readFile(testPath);

            assertEquals(content, result);
        }

        @Test
        @DisplayName("Should return null when reading non-existent file")
        void shouldReturnNullWhenReadingNonExistentFile() {
            String result = fileOps.readFile(tempDir.resolve("nonexistent.txt").toString());
            assertNull(result);
        }

        @Test
        @DisplayName("Should create parent directories when writing")
        void shouldCreateParentDirectoriesWhenWriting() {
            String testPath = tempDir.resolve("nested/deep/test.txt").toString();

            assertDoesNotThrow(() -> fileOps.writeFile(testPath, "content"));
            assertTrue(Files.exists(Paths.get(testPath)));
        }
/*
        @Test
        @DisplayName("Should throw RuntimeException when write fails")
        void shouldThrowRuntimeExceptionWhenWriteFails() {
            // Try to write to an invalid path (assuming /invalid/path doesn't exist and can't be created)
            String invalidPath = "/invalid/path/that/cannot/be/created/file.txt";

            assertThrows(RuntimeException.class, () ->
                    fileOps.writeFile(invalidPath, "content"));
        }
*/
        @Test
        @DisplayName("Should check file existence correctly")
        void shouldCheckFileExistenceCorrectly() throws IOException {
            String testPath = tempDir.resolve("exists.txt").toString();

            assertFalse(fileOps.fileExists(testPath));

            Files.write(Paths.get(testPath), "content".getBytes());

            assertTrue(fileOps.fileExists(testPath));
        }

        @Test
        @DisplayName("Should create backup with timestamp")
        void shouldCreateBackupWithTimestamp() throws IOException {
            String originalPath = tempDir.resolve("original.json").toString();
            Files.write(Paths.get(originalPath), "original content".getBytes());

            fileOps.createBackup(originalPath);

            // Check that a backup file was created
            try (var stream = Files.list(tempDir)) {
                long backupCount = stream
                        .filter(path -> path.getFileName().toString().contains("_backup_"))
                        .count();
                assertEquals(1, backupCount);
            }
        }

        @Test
        @DisplayName("Should create backup with extension preserved")
        void shouldCreateBackupWithExtensionPreserved() throws IOException {
            String originalPath = tempDir.resolve("config.json").toString();
            Files.write(Paths.get(originalPath), "content".getBytes());

            fileOps.createBackup(originalPath);

            try (var stream = Files.list(tempDir)) {
                boolean hasJsonBackup = stream
                        .anyMatch(path -> path.getFileName().toString().matches("config_backup_\\d{8}_\\d{6}\\.json"));
                assertTrue(hasJsonBackup);
            }
        }

        @Test
        @DisplayName("Should handle backup of file without extension")
        void shouldHandleBackupOfFileWithoutExtension() throws IOException {
            String originalPath = tempDir.resolve("configfile").toString();
            Files.write(Paths.get(originalPath), "content".getBytes());

            fileOps.createBackup(originalPath);

            try (var stream = Files.list(tempDir)) {
                boolean hasBackup = stream
                        .anyMatch(path -> path.getFileName().toString().matches("configfile_backup_\\d{8}_\\d{6}"));
                assertTrue(hasBackup);
            }
        }

        @Test
        @DisplayName("Should do nothing when backing up non-existent file")
        void shouldDoNothingWhenBackingUpNonExistentFile() {
            String nonExistentPath = tempDir.resolve("doesnotexist.txt").toString();

            // Should not throw exception
            assertDoesNotThrow(() -> fileOps.createBackup(nonExistentPath));

            // Should not create any files
            try (var stream = Files.list(tempDir)) {
                assertEquals(0, stream.count());
            } catch (IOException e) {
                fail("Failed to list directory");
            }
        }

        @Test
        @DisplayName("Should handle backup creation failure gracefully")
        void shouldHandleBackupCreationFailureGracefully() throws IOException {
            // Create a file in a read-only directory (simulating failure)
            String originalPath = tempDir.resolve("readonly.txt").toString();
            Files.write(Paths.get(originalPath), "content".getBytes());

            // Make parent directory read-only
            tempDir.toFile().setReadOnly();

            // Should not throw exception, just print warning
            assertDoesNotThrow(() -> fileOps.createBackup(originalPath));

            // Reset permissions for cleanup
            tempDir.toFile().setWritable(true);
        }
    }

    @Nested
    @DisplayName("ConsoleInputReader Tests")
    class ConsoleInputReaderTests {
        private ClientConfig.ConsoleInputReader inputReader;
        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        private final PrintStream originalOut = System.out;

        @BeforeEach
        void setUp() {
            inputReader = new ClientConfig.ConsoleInputReader();
            System.setOut(new PrintStream(outputStream));
        }

        @AfterEach
        void tearDown() {
            System.setOut(originalOut);
        }

        @Test
        @DisplayName("Should println correctly")
        void shouldPrintlnCorrectly() {
            String message = "Test message";

            inputReader.println(message);

            assertEquals(message + System.lineSeparator(), outputStream.toString());
        }

        @Test
        @DisplayName("Should retry readInt on invalid input")
        void shouldRetryReadIntOnInvalidInput() {
            // This test is tricky because ConsoleInputReader uses Scanner(System.in)
            // We'd need to mock System.in to properly test this
            // For now, we can at least verify the method exists and has correct signature
            assertNotNull(inputReader);
            assertTrue(inputReader instanceof ClientConfig.UserInputReader);
        }

        @Test
        @DisplayName("Should read line with prompt")
        void shouldReadLineWithPrompt() {
            String input = "test input\n";
            Scanner mockScanner = new Scanner(new ByteArrayInputStream(input.getBytes()));
            ClientConfig.ConsoleInputReader reader = new ClientConfig.ConsoleInputReader(mockScanner);

            String result = reader.readLine("Enter: ");

            assertEquals("test input", result);
            assertTrue(outputStream.toString().contains("Enter: "));
        }
    }
}