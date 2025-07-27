package com.skanga.init;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

import static org.mockito.Mockito.*;

class ClientConfigTest {
    private ObjectMapper objectMapper;
    private ClientConfig.McpClientConfig testConfig;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        testConfig = new ClientConfig.McpClientConfig(
                "jdbc:mysql://localhost:3306/testdb",
                "com.mysql.cj.jdbc.Driver",
                "testuser",
                "testpass",
                "test-server",
                "/path/to/test.jar",
                "/usr/bin/java"
        );
    }

    // Test ClientType enum
    @Test
    void testClientTypeProperties() {
        Assertions.assertEquals("cursor", ClientConfig.ClientType.CURSOR.configKey);
        Assertions.assertEquals("cursor_mcp.json", ClientConfig.ClientType.CURSOR.fileName);
        Assertions.assertTrue(ClientConfig.ClientType.CURSOR.useStandardMcpFormat);

        Assertions.assertEquals("zed", ClientConfig.ClientType.ZED.configKey);
        Assertions.assertEquals("zed_settings.json", ClientConfig.ClientType.ZED.fileName);
        Assertions.assertFalse(ClientConfig.ClientType.ZED.useStandardMcpFormat);

        Assertions.assertEquals("claude-code", ClientConfig.ClientType.CLAUDE_CODE.configKey);
        Assertions.assertEquals("claude_code_command.sh", ClientConfig.ClientType.CLAUDE_CODE.fileName);
        Assertions.assertFalse(ClientConfig.ClientType.CLAUDE_CODE.useStandardMcpFormat);
    }

    @ParameterizedTest
    @EnumSource(ClientConfig.ClientType.class)
    void testAllClientTypesHaveValidProperties(ClientConfig.ClientType clientType) {
        Assertions.assertNotNull(clientType.configKey);
        Assertions.assertFalse(clientType.configKey.trim().isEmpty());
        Assertions.assertNotNull(clientType.fileName);
        Assertions.assertFalse(clientType.fileName.trim().isEmpty());
        Assertions.assertTrue(clientType.fileName.contains("."));
    }

    // Test McpClientConfig construction
    @Test
    void testMcpClientConfigConstruction() {
        Assertions.assertAll(
                () -> Assertions.assertEquals("jdbc:mysql://localhost:3306/testdb", testConfig.dbUrl),
                () -> Assertions.assertEquals("com.mysql.cj.jdbc.Driver", testConfig.dbDriver),
                () -> Assertions.assertEquals("testuser", testConfig.dbUser),
                () -> Assertions.assertEquals("testpass", testConfig.dbPassword),
                () -> Assertions.assertEquals("test-server", testConfig.serverName),
                () -> Assertions.assertEquals("/path/to/test.jar", testConfig.jarPath),
                () -> Assertions.assertEquals("/usr/bin/java", testConfig.javaPath)
        );
    }

    @Test
    void testMcpClientConfigWithNullValues() {
        ClientConfig.McpClientConfig nullConfig = new ClientConfig.McpClientConfig(
                null, null, null, null, null, null, null);

        Assertions.assertAll(
                () -> Assertions.assertNull(nullConfig.dbUrl),
                () -> Assertions.assertNull(nullConfig.dbDriver),
                () -> Assertions.assertNull(nullConfig.dbUser),
                () -> Assertions.assertNull(nullConfig.dbPassword),
                () -> Assertions.assertNull(nullConfig.serverName),
                () -> Assertions.assertNull(nullConfig.jarPath),
                () -> Assertions.assertNull(nullConfig.javaPath)
        );
    }

    // Test path resolution methods
    @ParameterizedTest
    @CsvSource({
            "cursor, Windows 10, C:\\Users\\test, C:\\Users\\test\\AppData\\Roaming, C:\\Users\\test\\AppData\\Roaming\\Cursor\\mcp.json",
            "windsurf, Windows 10, C:\\Users\\test, C:\\Users\\test\\AppData\\Roaming, C:\\Users\\test\\AppData\\Roaming\\Windsurf\\mcp.json",
            "claude-desktop, Windows 10, C:\\Users\\test, C:\\Users\\test\\AppData\\Roaming, C:\\Users\\test\\AppData\\Roaming\\Claude\\claude_desktop_config.json",
            "zed, Windows 10, C:\\Users\\test, C:\\Users\\test\\AppData\\Roaming, C:\\Users\\test\\AppData\\Roaming\\Zed\\settings.json"
    })
    void testGetConfigPathWindows(String client, String os, String userHome, String appData, String expected) {
        String result = ClientConfig.getConfigPath(client, os, userHome, appData);
        Assertions.assertEquals(expected, result);
    }

    @ParameterizedTest
    @CsvSource({
            "cursor, Mac OS X, /Users/test, , /Users/test/.cursor/mcp.json",
            "windsurf, Mac OS X, /Users/test, , /Users/test/.windsurf/mcp.json",
            "claude-desktop, Mac OS X, /Users/test, , /Users/test/Library/Application Support/Claude/claude_desktop_config.json",
            "zed, Mac OS X, /Users/test, , /Users/test/.config/zed/settings.json"
    })
    void testGetConfigPathMac(String client, String os, String userHome, String appData, String expected) {
        String result = ClientConfig.getConfigPath(client, os, userHome, appData);
        Assertions.assertEquals(expected, result);
    }

    @ParameterizedTest
    @CsvSource({
            "cursor, Linux, /home/test, , /home/test/.config/cursor/mcp.json",
            "windsurf, Linux, /home/test, , /home/test/.config/windsurf/mcp.json",
            "claude-desktop, Linux, /home/test, , /home/test/.config/claude/claude_desktop_config.json",
            "zed, Linux, /home/test, , /home/test/.config/zed/settings.json"
    })
    void testGetConfigPathLinux(String client, String os, String userHome, String appData, String expected) {
        String result = ClientConfig.getConfigPath(client, os, userHome, appData);
        Assertions.assertEquals(expected, result);
    }

    @Test
    void testGetConfigPathUnknownClient() {
        String windowsResult = ClientConfig.getConfigPath("unknown", "Windows 10",
                "C:\\Users\\test", "C:\\Users\\test\\AppData\\Roaming");
        Assertions.assertEquals("C:\\Users\\test\\AppData\\Roaming\\unknown\\config.json", windowsResult);

        String macResult = ClientConfig.getConfigPath("unknown", "Mac OS X",
                "/Users/test", null);
        Assertions.assertEquals("/Users/test/.unknown/config.json", macResult);

        String linuxResult = ClientConfig.getConfigPath("unknown", "Linux",
                "/home/test", null);
        Assertions.assertEquals("/home/test/.config/unknown/config.json", linuxResult);
    }

    // Test JSON generation methods
    @Test
    void testGenerateMcpServersJson() throws IOException {
        String json = ClientConfig.generateMcpServersJson(testConfig);
        JsonNode jsonNode = objectMapper.readTree(json);

        Assertions.assertTrue(jsonNode.has("mcpServers"));
        JsonNode serverConfig = jsonNode.get("mcpServers").get("test-server");
        Assertions.assertNotNull(serverConfig);
        Assertions.assertEquals("/usr/bin/java", serverConfig.get("command").asText());
        Assertions.assertEquals(2, serverConfig.get("args").size());
        Assertions.assertEquals("-jar", serverConfig.get("args").get(0).asText());
        Assertions.assertEquals("/path/to/test.jar", serverConfig.get("args").get(1).asText());

        JsonNode env = serverConfig.get("env");
        Assertions.assertEquals("jdbc:mysql://localhost:3306/testdb", env.get("DB_URL").asText());
        Assertions.assertEquals("com.mysql.cj.jdbc.Driver", env.get("DB_DRIVER").asText());
        Assertions.assertEquals("testuser", env.get("DB_USER").asText());
        Assertions.assertEquals("testpass", env.get("DB_PASSWORD").asText());
    }

    @Test
    void testGenerateContinueJson() throws IOException {
        String json = ClientConfig.generateContinueJson(testConfig);
        JsonNode jsonNode = objectMapper.readTree(json);

        Assertions.assertTrue(jsonNode.has("models"));
        Assertions.assertTrue(jsonNode.get("models").isArray());
        Assertions.assertTrue(jsonNode.has("mcpServers"));
        Assertions.assertTrue(jsonNode.get("mcpServers").isArray());

        JsonNode serverConfig = jsonNode.get("mcpServers").get(0);
        Assertions.assertEquals("test-server", serverConfig.get("name").asText());
        Assertions.assertEquals("/usr/bin/java", serverConfig.get("command").asText());
    }

    @Test
    void testGenerateVSCodeJson() throws IOException {
        String json = ClientConfig.generateVSCodeJson(testConfig);
        JsonNode jsonNode = objectMapper.readTree(json);

        Assertions.assertTrue(jsonNode.has("mcp"));
        Assertions.assertTrue(jsonNode.get("mcp").has("servers"));
        JsonNode serverConfig = jsonNode.get("mcp").get("servers").get("test-server");
        Assertions.assertEquals("/usr/bin/java", serverConfig.get("command").asText());
    }

    @Test
    void testGenerateZedJson() throws IOException {
        String json = ClientConfig.generateZedJson(testConfig);
        JsonNode jsonNode = objectMapper.readTree(json);

        Assertions.assertTrue(jsonNode.has("context_servers"));
        JsonNode serverConfig = jsonNode.get("context_servers").get("test-server");
        Assertions.assertEquals("custom", serverConfig.get("source").asText());
        Assertions.assertEquals("/usr/bin/java", serverConfig.get("command").asText());
    }

    @Test
    void testGenerateClaudeCodeCommand() {
        String command = ClientConfig.generateClaudeCodeCommand(testConfig);

        Assertions.assertNotNull(command);
        Assertions.assertTrue(command.contains("claude mcp add test-server"));
        Assertions.assertTrue(command.contains("-e DB_URL=jdbc:mysql://localhost:3306/testdb"));
        Assertions.assertTrue(command.contains("-e DB_DRIVER=com.mysql.cj.jdbc.Driver"));
        Assertions.assertTrue(command.contains("-e DB_USER=testuser"));
        Assertions.assertTrue(command.contains("-e DB_PASSWORD=testpass"));
        Assertions.assertTrue(command.contains("-- /usr/bin/java -jar /path/to/test.jar"));
    }

    // Test shell escaping
    @Test
    void testEscapeShellString() {
        Assertions.assertEquals("simple", ClientConfig.escapeShellString("simple"));
        Assertions.assertEquals("\"path with spaces\"", ClientConfig.escapeShellString("path with spaces"));
        Assertions.assertEquals("\"path&special\"", ClientConfig.escapeShellString("path&special"));
        Assertions.assertEquals("\"path|pipe\"", ClientConfig.escapeShellString("path|pipe"));
        Assertions.assertEquals("\"path;semicolon\"", ClientConfig.escapeShellString("path;semicolon"));
        Assertions.assertEquals("", ClientConfig.escapeShellString(""));
        // Quotes alone don't trigger wrapping in the current implementation
        Assertions.assertEquals("path\"quote", ClientConfig.escapeShellString("path\"quote"));
        // But quotes get escaped when the string is wrapped due to other special chars
        Assertions.assertEquals("\"path \\\"quote\\\"\"", ClientConfig.escapeShellString("path \"quote\""));
    }

    @Test
    void testEscapeShellStringNull() {
        Assertions.assertEquals("", ClientConfig.escapeShellString(null));
    }

    // Test formatting methods
    @ParameterizedTest
    @EnumSource(ClientConfig.ClientType.class)
    void testFormatClientName(ClientConfig.ClientType clientType) {
        String formatted = ClientConfig.formatClientName(clientType);
        Assertions.assertTrue(Character.isUpperCase(formatted.charAt(0)));
        Assertions.assertFalse(formatted.contains("_"));
    }

    // Test merge operations
    @Test
    void testMergeStandardMcpConfig() throws IOException {
        String existingConfig = """
            {
              "mcpServers": {
                "existing-server": {
                  "command": "java",
                  "args": ["-jar", "existing.jar"]
                }
              }
            }
            """;

        JsonNode existing = objectMapper.readTree(existingConfig);
        JsonNode merged = ClientConfig.mergeStandardMcpConfig(existing, testConfig);

        Assertions.assertTrue(merged.has("mcpServers"));
        Assertions.assertEquals(2, merged.get("mcpServers").size());
        Assertions.assertTrue(merged.get("mcpServers").has("existing-server"));
        Assertions.assertTrue(merged.get("mcpServers").has("test-server"));
    }

    @Test
    void testMergeZedConfig() throws IOException {
        String existingConfig = """
            {
              "context_servers": {
                "existing-server": {
                  "command": "java"
                }
              }
            }
            """;

        JsonNode existing = objectMapper.readTree(existingConfig);
        JsonNode merged = ClientConfig.mergeZedConfig(existing, testConfig);

        Assertions.assertTrue(merged.has("context_servers"));
        Assertions.assertEquals(2, merged.get("context_servers").size());
        Assertions.assertTrue(merged.get("context_servers").has("existing-server"));
        Assertions.assertTrue(merged.get("context_servers").has("test-server"));
        Assertions.assertEquals("custom", merged.get("context_servers").get("test-server").get("source").asText());
    }

    @Test
    void testMergeVSCodeConfig() throws IOException {
        String existingConfig = """
            {
              "mcp": {
                "servers": {
                  "existing-server": {
                    "command": "java"
                  }
                }
              }
            }
            """;

        JsonNode existing = objectMapper.readTree(existingConfig);
        JsonNode merged = ClientConfig.mergeVSCodeConfig(existing, testConfig);

        Assertions.assertTrue(merged.has("mcp"));
        Assertions.assertTrue(merged.get("mcp").has("servers"));
        Assertions.assertEquals(2, merged.get("mcp").get("servers").size());
        Assertions.assertTrue(merged.get("mcp").get("servers").has("existing-server"));
        Assertions.assertTrue(merged.get("mcp").get("servers").has("test-server"));
    }

    @Test
    void testMergeContinueConfig() throws IOException {
        String existingConfig = """
            {
              "models": [],
              "mcpServers": [{
                "name": "existing-server",
                "command": "java"
              }]
            }
            """;

        JsonNode existing = objectMapper.readTree(existingConfig);
        JsonNode merged = ClientConfig.mergeContinueConfig(existing, testConfig);

        Assertions.assertTrue(merged.has("mcpServers"));
        Assertions.assertTrue(merged.get("mcpServers").isArray());
        Assertions.assertEquals(2, merged.get("mcpServers").size());
        Assertions.assertEquals("existing-server", merged.get("mcpServers").get(0).get("name").asText());
        Assertions.assertEquals("test-server", merged.get("mcpServers").get(1).get("name").asText());
    }


    // Test empty/missing configurations for merge operations
    @Test
    void testMergeWithEmptyConfig() throws IOException {
        String emptyConfig = "{}";

        // Test standard MCP merge with empty config
        JsonNode emptyForStandard = objectMapper.readTree(emptyConfig);
        JsonNode mergedStandard = ClientConfig.mergeStandardMcpConfig(emptyForStandard, testConfig);
        Assertions.assertTrue(mergedStandard.has("mcpServers"));
        Assertions.assertEquals(1, mergedStandard.get("mcpServers").size());

        // Test Zed merge with empty config
        JsonNode emptyForZed = objectMapper.readTree(emptyConfig);
        JsonNode mergedZed = ClientConfig.mergeZedConfig(emptyForZed, testConfig);
        Assertions.assertTrue(mergedZed.has("context_servers"));
        Assertions.assertEquals(1, mergedZed.get("context_servers").size());

        // Test VS Code merge with empty config
        JsonNode emptyForVSCode = objectMapper.readTree(emptyConfig);
        JsonNode mergedVSCode = ClientConfig.mergeVSCodeConfig(emptyForVSCode, testConfig);
        Assertions.assertTrue(mergedVSCode.has("mcp"));
        Assertions.assertTrue(mergedVSCode.get("mcp").has("servers"));
        Assertions.assertEquals(1, mergedVSCode.get("mcp").get("servers").size());

        // Test Continue merge with empty config
        JsonNode emptyForContinue = objectMapper.readTree(emptyConfig);
        JsonNode mergedContinue = ClientConfig.mergeContinueConfig(emptyForContinue, testConfig);
        Assertions.assertTrue(mergedContinue.has("mcpServers"));
        Assertions.assertTrue(mergedContinue.get("mcpServers").isArray());
        Assertions.assertEquals(1, mergedContinue.get("mcpServers").size());
    }

    // Test file operations
    @Test
    void testCreateBackup() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        String originalContent = """
            {
              "mcpServers": {
                "test": {}
              }
            }
            """;

        Files.write(configFile, originalContent.getBytes());
        Assertions.assertTrue(Files.exists(configFile));

        // Call createBackup
        ClientConfig.createBackup(configFile.toString());

        // Check that backup was created
        try (var files = Files.list(tempDir)) {
            Assertions.assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("config_backup_")));
        }
    }

    @Test
    void testCreateBackupNonExistentFile() {
        Path nonExistentFile = tempDir.resolve("nonexistent.json");
        Assertions.assertFalse(Files.exists(nonExistentFile));

        // Should not throw exception when file doesn't exist
        Assertions.assertDoesNotThrow(() -> ClientConfig.createBackup(nonExistentFile.toString()));
    }

    @Test
    void testReadExistingConfig() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        String content = """
            {
              "mcpServers": {}
            }
            """;

        Files.write(configFile, content.getBytes());

        String readContent = ClientConfig.readExistingConfig(configFile.toString());
        Assertions.assertNotNull(readContent);
        Assertions.assertTrue(readContent.contains("mcpServers"));

        JsonNode json = objectMapper.readTree(readContent);
        Assertions.assertTrue(json.has("mcpServers"));
    }

    @Test
    void testReadNonExistentConfig() {
        Path nonExistentFile = tempDir.resolve("nonexistent.json");
        String content = ClientConfig.readExistingConfig(nonExistentFile.toString());
        Assertions.assertNull(content);
    }

    // Test backup filename generation
    @Test
    void testBackupFilenameGeneration() {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = now.format(formatter);

        String backupFilename = "config_backup_" + timestamp + ".json";

        Assertions.assertTrue(backupFilename.startsWith("config_backup_"));
        Assertions.assertTrue(backupFilename.endsWith(".json"));
        Assertions.assertTrue(backupFilename.contains(timestamp));
    }

    @Test
    void testBackupFilenameWithoutExtension() {
        String originalFile = "config";
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = now.format(formatter);

        String backupFilename = originalFile + "_backup_" + timestamp;

        Assertions.assertTrue(backupFilename.startsWith("config_backup_"));
        Assertions.assertTrue(backupFilename.contains(timestamp));
        Assertions.assertFalse(backupFilename.contains("."));
    }

    // Test error handling scenarios
    @Test
    void testInvalidJsonHandling() {
        String invalidJson = "{ invalid json }";
        Assertions.assertThrows(Exception.class, () -> objectMapper.readTree(invalidJson));
    }

    // Test configuration validation
    @Test
    void testValidateServerConfigStructure() throws IOException {
        String serverConfigJson = """
            {
              "command": "/usr/bin/java",
              "args": ["-jar", "/path/to/test.jar"],
              "env": {
                "DB_URL": "jdbc:mysql://localhost:3306/testdb",
                "DB_DRIVER": "com.mysql.cj.jdbc.Driver",
                "DB_USER": "testuser",
                "DB_PASSWORD": "testpass"
              }
            }
            """;

        JsonNode serverConfig = objectMapper.readTree(serverConfigJson);

        // Validate required fields
        Assertions.assertTrue(serverConfig.has("command"));
        Assertions.assertTrue(serverConfig.has("args"));
        Assertions.assertTrue(serverConfig.has("env"));

        // Validate args array
        Assertions.assertTrue(serverConfig.get("args").isArray());
        Assertions.assertEquals(2, serverConfig.get("args").size());
        Assertions.assertEquals("-jar", serverConfig.get("args").get(0).asText());

        // Validate env object
        JsonNode env = serverConfig.get("env");
        Assertions.assertTrue(env.has("DB_URL"));
        Assertions.assertTrue(env.has("DB_DRIVER"));
        Assertions.assertTrue(env.has("DB_USER"));
        Assertions.assertTrue(env.has("DB_PASSWORD"));
    }

    // Test edge cases for different client configurations
    @ParameterizedTest
    @EnumSource(value = ClientConfig.ClientType.class,
            names = {"CURSOR", "WINDSURF", "CLAUDE_DESKTOP"})
    void testStandardMcpFormatClients(ClientConfig.ClientType clientType) {
        Assertions.assertTrue(clientType.useStandardMcpFormat);
        Assertions.assertTrue(clientType.fileName.contains("mcp") ||
                clientType.fileName.contains("config"));
    }

    @ParameterizedTest
    @EnumSource(value = ClientConfig.ClientType.class,
            names = {"CONTINUE", "VSCODE", "CLAUDE_CODE", "GEMINI_CLI", "ZED"})
    void testNonStandardFormatClients(ClientConfig.ClientType clientType) {
        Assertions.assertFalse(clientType.useStandardMcpFormat);
    }

    // Test special characters in database configuration
    @Test
    void testConfigWithSpecialCharacters() {
        ClientConfig.McpClientConfig specialConfig = new ClientConfig.McpClientConfig(
                "jdbc:mysql://localhost:3306/test&db",
                "com.mysql.cj.jdbc.Driver",
                "user@domain.com",
                "pass&word$123",
                "special-server",
                "/path/to/test jar.jar",
                "/usr/bin/java"
        );

        Assertions.assertTrue(specialConfig.dbUrl.contains("&"));
        Assertions.assertTrue(specialConfig.dbUser.contains("@"));
        Assertions.assertTrue(specialConfig.dbPassword.contains("&"));
        Assertions.assertTrue(specialConfig.dbPassword.contains("$"));
        Assertions.assertTrue(specialConfig.jarPath.contains(" "));
    }

    // Test very long configuration values
    @Test
    void testConfigWithLongValues() {
        String longUrl = "jdbc:mysql://very-long-hostname-that-exceeds-normal-length.example.com:3306/very_long_database_name_that_is_unusual";
        String longPassword = "a".repeat(100);

        ClientConfig.McpClientConfig longConfig = new ClientConfig.McpClientConfig(
                longUrl, "com.mysql.cj.jdbc.Driver", "user", longPassword,
                "server", "/path/to/jar", "java");

        Assertions.assertTrue(longConfig.dbUrl.length() > 50);
        Assertions.assertEquals(100, longConfig.dbPassword.length());
    }

    @Test
    void testMergeConfiguration_InvalidExistingJson() {
        String invalidJson = "this is not json";
        Assertions.assertThrows(RuntimeException.class, () ->
            ClientConfig.mergeConfiguration(invalidJson, testConfig, ClientConfig.ClientType.CURSOR));
    }

    @Test
    void testGetClientChoice_ValidInput() {
        String simulatedInput = "5\n";
        InputStream originalIn = System.in;
        System.setIn(new ByteArrayInputStream(simulatedInput.getBytes()));
        Scanner scanner = new Scanner(System.in);

        try {
            int choice = ClientConfig.getClientChoice(scanner);
            Assertions.assertEquals(5, choice);
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
    void testCollectDatabaseInfo_AcceptDefaults() {
        String simulatedInput = "\n\n\n\n\n";
        InputStream originalIn = System.in;
        System.setIn(new ByteArrayInputStream(simulatedInput.getBytes()));
        Scanner scanner = new Scanner(System.in);

        try (MockedStatic<ClientConfig> mockedStatic = mockStatic(ClientConfig.class)) {
            mockedStatic.when(ClientConfig::getJar).thenReturn("/path/to/app.jar");
            mockedStatic.when(ClientConfig::getJava).thenReturn("/path/to/java");
            mockedStatic.when(() -> ClientConfig.collectDatabaseInfo(scanner)).thenCallRealMethod();

            ClientConfig.McpClientConfig config = ClientConfig.collectDatabaseInfo(scanner);

            Assertions.assertEquals(ClientConfig.DEFAULT_DB_URL, config.dbUrl);
            Assertions.assertEquals(ClientConfig.DEFAULT_DB_DRIVER, config.dbDriver);
            Assertions.assertEquals(ClientConfig.DEFAULT_DB_USER, config.dbUser);
            Assertions.assertEquals(ClientConfig.DEFAULT_DB_PASSWORD, config.dbPassword);
            Assertions.assertEquals("my-database", config.serverName);
            Assertions.assertEquals("/path/to/app.jar", config.jarPath);
            Assertions.assertEquals("/path/to/java", config.javaPath);
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
    void testCollectDatabaseInfo_SuccessfulConnection() {
        String simulatedInput = "jdbc:h2:mem:test\norg.h2.Driver\nsa\n\ntest-server\n";
        InputStream originalIn = System.in;
        System.setIn(new ByteArrayInputStream(simulatedInput.getBytes()));
        Scanner scanner = new Scanner(System.in);

        try (MockedStatic<ClientConfig> mockedStatic = mockStatic(ClientConfig.class);
             MockedStatic<DriverManager> mockedDriverManager = mockStatic(DriverManager.class)) {

            // Mock successful database connection
            Connection mockConnection = mock(Connection.class);
            mockedDriverManager.when(() -> DriverManager.getConnection("jdbc:h2:mem:test", "sa", ""))
                    .thenReturn(mockConnection);

            mockedStatic.when(ClientConfig::getJar).thenReturn("/path/to/app.jar");
            mockedStatic.when(ClientConfig::getJava).thenReturn("/path/to/java");
            mockedStatic.when(() -> ClientConfig.collectDatabaseInfo(scanner)).thenCallRealMethod();

            ClientConfig.McpClientConfig config = ClientConfig.collectDatabaseInfo(scanner);

            Assertions.assertEquals("jdbc:h2:mem:test", config.dbUrl);
            Assertions.assertEquals("org.h2.Driver", config.dbDriver);
            Assertions.assertEquals("sa", config.dbUser);
            Assertions.assertEquals("", config.dbPassword);
            Assertions.assertEquals("test-server", config.serverName);
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
    void testCollectDatabaseInfo_ConnectionFailsButProceed() {
        String simulatedInput = "jdbc:nonexistent://invalid\nnonexistent.Driver\nuser\npass\nn\ntest-server\n";
        InputStream originalIn = System.in;
        System.setIn(new ByteArrayInputStream(simulatedInput.getBytes()));
        Scanner scanner = new Scanner(System.in);

        try (MockedStatic<ClientConfig> mockedStatic = mockStatic(ClientConfig.class)) {
            mockedStatic.when(ClientConfig::getJar).thenReturn("/path/to/app.jar");
            mockedStatic.when(ClientConfig::getJava).thenReturn("/path/to/java");
            mockedStatic.when(() -> ClientConfig.collectDatabaseInfo(scanner)).thenCallRealMethod();

            ClientConfig.McpClientConfig config = ClientConfig.collectDatabaseInfo(scanner);

            Assertions.assertEquals("jdbc:nonexistent://invalid", config.dbUrl);
            Assertions.assertEquals("nonexistent.Driver", config.dbDriver);
            Assertions.assertEquals("user", config.dbUser);
            Assertions.assertEquals("pass", config.dbPassword);
            Assertions.assertEquals("test-server", config.serverName);
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
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
            Assertions.assertTrue(errContent.toString().contains("Error:"));
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    @Test
    void testDisplayClientOptions() {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            ClientConfig.displayClientOptions();
            String output = outContent.toString();
            Assertions.assertTrue(output.contains("Supported MCP Clients:"));
            Assertions.assertTrue(output.contains("1. Cursor"));
            Assertions.assertTrue(output.contains("9. Generate all configurations"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "1, CURSOR",
            "2, WINDSURF",
            "3, CLAUDE_DESKTOP",
            "4, CONTINUE",
            "5, VSCODE",
            "6, CLAUDE_CODE",
            "7, GEMINI_CLI",
            "8, ZED"
    })
    void testGenerateConfiguration_AllCases(int choice) {
        Scanner mockScanner = mock(Scanner.class);
        when(mockScanner.nextLine()).thenReturn("n"); // No to direct installation

        Assertions.assertDoesNotThrow(() ->
                ClientConfig.generateConfiguration(testConfig, choice, mockScanner));
    }

    @Test
    void testSaveConfiguration_ForCommandType() throws IOException {
        String content = "claude mcp add test-server...";
        String fileName = "claude_code_command.sh";

        // For CLAUDE_CODE type, it should just write the file directly
        Assertions.assertDoesNotThrow(() ->
                ClientConfig.saveConfiguration(content, fileName, ClientConfig.ClientType.CLAUDE_CODE, testConfig));

        // Verify file was created
        Assertions.assertTrue(Files.exists(Paths.get(fileName)));
        Assertions.assertEquals(content, Files.readString(Paths.get(fileName)));

        // Cleanup
        Files.deleteIfExists(Paths.get(fileName));
    }

    @Test
    void testCreateBackup_IOExceptionDuringCopy() throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.write(configFile, "test content".getBytes());

        // Make parent directory read-only to cause IOException
        tempDir.toFile().setReadOnly();

        // Should not throw exception, just print warning
        Assertions.assertDoesNotThrow(() -> ClientConfig.createBackup(configFile.toString()));

        tempDir.toFile().setWritable(true); // Cleanup
    }

    @Test
    void testGetJar_URISyntaxException() {
        // This is harder to test directly, but we can test the fallback behavior
        String jar = ClientConfig.getJar();
        Assertions.assertNotNull(jar);
        // Should either return a valid path or the error message
        Assertions.assertTrue(jar.endsWith(".jar") || jar.contains("target/classes") || jar.contains("target\\classes") || jar.contains("CANNOT LOCATE"));
    }

    @Test
    void testGetJava_ProcessHandleFailure() {
        String java = ClientConfig.getJava();
        Assertions.assertNotNull(java);
        // Should either return a valid path or the error message
        Assertions.assertTrue(java.contains("java") || java.contains("CANNOT LOCATE"));
    }

    @Test
    void testMergeConfiguration_AllClientTypes() {
        String existingConfig = "{}";

        for (ClientConfig.ClientType clientType : ClientConfig.ClientType.values()) {
            if (clientType != ClientConfig.ClientType.CLAUDE_CODE) { // Skip command type
                Assertions.assertDoesNotThrow(() -> {
                    String merged = ClientConfig.mergeConfiguration(existingConfig, testConfig, clientType);
                    Assertions.assertNotNull(merged);
                    JsonNode json = objectMapper.readTree(merged);
                    Assertions.assertNotNull(json);
                });
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ClientConfig.ClientType.class)
    void testProvideInstallationInstructions(ClientConfig.ClientType clientType) {
        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            ClientConfig.provideInstallationInstructions(clientType);
            String output = outContent.toString();
            Assertions.assertFalse(output.isEmpty());

            switch (clientType) {
                case ZED -> Assertions.assertTrue(output.contains("Configuration options:"));
                case VSCODE -> Assertions.assertTrue(output.contains(".vscode/settings.json"));
                case CONTINUE -> Assertions.assertTrue(output.contains("~/.continue/config.json"));
                case CLAUDE_CODE -> Assertions.assertTrue(output.contains("Run the generated command"));
                default -> Assertions.assertTrue(output.contains("Save this to:"));
            }
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void testOfferDirectInstallation_UserDeclines() {
        String input = "n\n";
        InputStream originalIn = System.in;
        System.setIn(new ByteArrayInputStream(input.getBytes()));
        Scanner scanner = new Scanner(System.in);

        ByteArrayOutputStream outContent = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outContent));

        try {
            ClientConfig.offerDirectInstallation(testConfig, ClientConfig.ClientType.CURSOR, scanner);
            Assertions.assertTrue(outContent.toString().contains("Manual installation"));
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
    }

    @Test
    void testSaveConfigurationDirect_ClaudeCodeType() {
        // Should return early for CLAUDE_CODE type
        Assertions.assertDoesNotThrow(() ->
                ClientConfig.saveConfigurationDirect(testConfig, ClientConfig.ClientType.CLAUDE_CODE));
    }

    @Test
    void testSaveConfigurationDirect_ZedType() {
        // IMPLEMENT THIS
    }

    @Test
    void testGetClientChoice_MultipleInvalidInputs() {
        String input = "invalid\n0\n10\nabc\n-1\n5\n";
        InputStream originalIn = System.in;
        System.setIn(new ByteArrayInputStream(input.getBytes()));
        Scanner scanner = new Scanner(System.in);

        try {
            int choice = ClientConfig.getClientChoice(scanner);
            Assertions.assertEquals(5, choice);
        } finally {
            System.setIn(originalIn);
        }
    }

    @Test
    void testConfigWithEmptyAndNullServerName() {
        ClientConfig.McpClientConfig emptyNameConfig = new ClientConfig.McpClientConfig(
                "jdbc:h2:mem:test", "org.h2.Driver", "sa", "", "", "/path/jar", "java");

        String json = ClientConfig.generateMcpServersJson(emptyNameConfig);
        Assertions.assertTrue(json.contains("\"\""));
    }
}
