package com.skanga.init;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.Scanner;

public class ClientConfig {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    static final String DEFAULT_DB_URL = "jdbc:h2:mem:test";
    static final String DEFAULT_DB_USER = "sa";
    static final String DEFAULT_DB_PASSWORD = "";
    static final String DEFAULT_DB_DRIVER = "org.h2.Driver";
    static final String DEST_DIR = "./conf";

    private final FileOperations fileOps;
    private final UserInputReader inputReader;

    // Constructor for dependency injection (tests can pass mocks)
    public ClientConfig(FileOperations fileOps, UserInputReader inputReader) {
        this.fileOps = fileOps;
        this.inputReader = inputReader;
    }

    // Default constructor for normal usage
    public ClientConfig() {
        this(new DefaultFileOperations(), new ConsoleInputReader());
    }

    // Extract file operations interface - only what we actually need
    interface FileOperations {
        String readFile(String path);
        void writeFile(String path, String content);
        boolean fileExists(String path);
        void createBackup(String originalPath);
    }

    // Extract user input interface
    interface UserInputReader {
        String readLine(String prompt);
        int readInt(String prompt);
        void println(String message);
    }

    // Simple implementations
    static class DefaultFileOperations implements FileOperations {
        public String readFile(String path) {
            try {
                return Files.readString(Paths.get(path));
            } catch (IOException e) {
                return null;
            }
        }

        public void writeFile(String path, String content) {
            try {
                Files.createDirectories(Paths.get(path).getParent());
                Files.write(Paths.get(path), content.getBytes());
            } catch (IOException e) {
                throw new RuntimeException("Failed to write file: " + e.getMessage(), e);
            }
        }

        public boolean fileExists(String path) {
            return Files.exists(Paths.get(path));
        }

        public void createBackup(String originalPath) {
            try {
                Path original = Paths.get(originalPath);
                if (!Files.exists(original)) {
                    return; // No file to backup
                }

                // Create timestamp for backup filename
                LocalDateTime now = LocalDateTime.now();
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
                String timestamp = now.format(formatter);

                // Create backup filename
                String originalFilename = original.getFileName().toString();
                String backupFilename;
                int dotIndex = originalFilename.lastIndexOf('.');
                if (dotIndex > 0) {
                    String name = originalFilename.substring(0, dotIndex);
                    String extension = originalFilename.substring(dotIndex);
                    backupFilename = name + "_backup_" + timestamp + extension;
                } else {
                    backupFilename = originalFilename + "_backup_" + timestamp;
                }

                Path backupPath = original.getParent().resolve(backupFilename);
                Files.copy(original, backupPath);
                System.out.println("Backup created: " + backupPath.toAbsolutePath());
            } catch (IOException e) {
                System.out.println("Warning: Could not create backup: " + e.getMessage());
            }
        }
    }

    static class ConsoleInputReader implements UserInputReader {
        private final Scanner scanner;

        public ConsoleInputReader() {
            this(new Scanner(System.in));
        }

        ConsoleInputReader(Scanner scanner) {
            this.scanner = scanner;
        }

        public String readLine(String prompt) {
            System.out.print(prompt);
            return scanner.nextLine().trim();
        }

        public int readInt(String prompt) {
            while (true) {
                try {
                    return Integer.parseInt(readLine(prompt));
                } catch (NumberFormatException e) {
                    println("Please enter a valid number.");
                }
            }
        }

        public void println(String message) {
            System.out.println(message);
        }
    }

    // Keep existing static entry point for backwards compatibility
    public static void main(String[] args) {
        new ClientConfig().run();
    }

    // Main workflow - now testable
    public void run() {
        inputReader.println("=== DBChat MCP Configuration Generator ===\n");

        try {
            McpClientConfig clientConfig = collectDatabaseInfo();
            ClientType clientType = selectClient();
            generateAndSaveConfiguration(clientConfig, clientType);
        } catch (Exception e) {
            inputReader.println("Error: " + e.getMessage());
        }
    }

    // Break down collectDatabaseInfo - now testable
    McpClientConfig collectDatabaseInfo() {
        inputReader.println("Please provide your database connection details (press Enter to use default):\n");

        String dbUrl = inputReader.readLine("Database JDBC URL [default: " + DEFAULT_DB_URL + "]: ");
        if (dbUrl.isEmpty()) dbUrl = DEFAULT_DB_URL;

        String dbDriver = inputReader.readLine("Database JDBC Driver [default: " + DEFAULT_DB_DRIVER + "]: ");
        if (dbDriver.isEmpty()) dbDriver = DEFAULT_DB_DRIVER;

        String dbUser = inputReader.readLine("Database Username [default: " + DEFAULT_DB_USER + "]: ");
        if (dbUser.isEmpty()) dbUser = DEFAULT_DB_USER;

        String dbPassword = inputReader.readLine("Database Password [default: " + (DEFAULT_DB_PASSWORD.isEmpty() ? "(empty)" : DEFAULT_DB_PASSWORD) + "]: ");
        if (dbPassword.isEmpty()) dbPassword = DEFAULT_DB_PASSWORD;

        // Test connection if possible
        if (shouldTestConnection() && !testDatabaseConnection(dbUrl, dbDriver, dbUser, dbPassword)) {
            inputReader.println("Failed to connect to database, but proceeding with configuration...");
        }

        String serverName = inputReader.readLine("Server name (default: my-database): ");
        if (serverName.isEmpty()) serverName = "my-database";

        String jarPath = detectJarPath();
        String javaPath = detectJavaPath();

        return new McpClientConfig(dbUrl, dbDriver, dbUser, dbPassword, serverName, jarPath, javaPath);
    }

    // Separate client selection logic - now testable
    ClientType selectClient() {
        displayClientOptions();

        int choice = inputReader.readInt("Select your MCP client (1-9): ");
        while (choice < 1 || choice > 9) {
            inputReader.println("Please enter a number between 1 and 9.");
            choice = inputReader.readInt("Select your MCP client (1-9): ");
        }

        return getClientTypeFromChoice(choice);
    }

    // Pure function - easily testable
    ClientType getClientTypeFromChoice(int choice) {
        return switch (choice) {
            case 1 -> ClientType.CURSOR;
            case 2 -> ClientType.WINDSURF;
            case 3 -> ClientType.CLAUDE_DESKTOP;
            case 4 -> ClientType.CONTINUE;
            case 5 -> ClientType.VSCODE;
            case 6 -> ClientType.CLAUDE_CODE;
            case 7 -> ClientType.GEMINI_CLI;
            case 8 -> ClientType.ZED;
            case 9 -> null; // Special case for "all"
            default -> throw new IllegalArgumentException("Invalid choice: " + choice);
        };
    }

    // Configuration generation
    public void generateAndSaveConfiguration(McpClientConfig clientConfig, ClientType clientType) {
        if (clientType == null) {
            generateAllConfigurations(clientConfig);
            return;
        }

        String configContent = generateConfigurationContent(clientConfig, clientType);
        String fileName = clientType.fileName;

        saveConfigurationFile(configContent, fileName, clientType, clientConfig);
        inputReader.println(formatClientName(clientType) + " configuration generated!");
    }


    /**
     * Creates a standard server configuration object.
     * Used by multiple client types with slight variations.
     */
    static ObjectNode createServerConfig(McpClientConfig clientConfig) {
        ObjectNode serverConfig = objectMapper.createObjectNode();
        serverConfig.put("command", clientConfig.javaPath);

        ArrayNode args = objectMapper.createArrayNode();
        args.add("-jar");
        args.add(clientConfig.jarPath);
        serverConfig.set("args", args);

        ObjectNode env = objectMapper.createObjectNode();
        env.put("DB_URL", clientConfig.dbUrl);
        env.put("DB_DRIVER", clientConfig.dbDriver);
        env.put("DB_USER", clientConfig.dbUser);
        env.put("DB_PASSWORD", clientConfig.dbPassword);
        serverConfig.set("env", env);

        return serverConfig;
    }

    // ============================================================================
    // CONFIGURATION MERGING METHODS USING JACKSON
    // ============================================================================

    /**
     * Merges new MCP server configuration with existing configuration file.
     * Uses Jackson for safe JSON manipulation instead of regex.
     *
     * @param existingConfig The existing configuration JSON as string
     * @param config The new server configuration to merge
     * @param clientType The type of client (determines merge strategy)
     * @return Merged configuration as JSON string
     */
    static String mergeConfiguration(String existingConfig, McpClientConfig config, ClientType clientType) {
        try {
            JsonNode existingJson = objectMapper.readTree(existingConfig);
            JsonNode mergedJson = switch (clientType) {
                case CURSOR, WINDSURF, CLAUDE_DESKTOP, GEMINI_CLI ->
                        mergeStandardMcpConfig(existingJson, config);
                case ZED ->
                        mergeZedConfig(existingJson, config);
                case VSCODE ->
                        mergeVSCodeConfig(existingJson, config);
                case CONTINUE ->
                        mergeContinueConfig(existingJson, config);
                default -> existingJson;
            };

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mergedJson);
        } catch (Exception e) {
            throw new RuntimeException("Failed to merge JSON configuration: " + e.getMessage(), e);
        }
    }

    /**
     * Merges configuration for standard MCP format clients.
     * Adds to or creates the mcpServers object.
     */
    static JsonNode mergeStandardMcpConfig(JsonNode existingJson, McpClientConfig config) {
        ObjectNode root = (ObjectNode) existingJson;

        // Get or create mcpServers object
        ObjectNode mcpServers;
        if (root.has("mcpServers")) {
            mcpServers = (ObjectNode) root.get("mcpServers");
        } else {
            mcpServers = objectMapper.createObjectNode();
            root.set("mcpServers", mcpServers);
        }

        // Create and add server configuration
        ObjectNode serverConfig = createServerConfig(config);
        mcpServers.set(config.serverName, serverConfig);

        return root;
    }

    /**
     * Merges configuration for Zed editor format.
     * Adds to or creates the context_servers object.
     */
    static JsonNode mergeZedConfig(JsonNode existingJson, McpClientConfig config) {
        ObjectNode root = (ObjectNode) existingJson;

        // Get or create context_servers object
        ObjectNode contextServers;
        if (root.has("context_servers")) {
            contextServers = (ObjectNode) root.get("context_servers");
        } else {
            contextServers = objectMapper.createObjectNode();
            root.set("context_servers", contextServers);
        }

        // Create server configuration with Zed-specific properties
        ObjectNode serverConfig = createServerConfig(config);
        serverConfig.put("source", "custom");

        contextServers.set(config.serverName, serverConfig);

        return root;
    }

    /**
     * Merges configuration for VS Code format.
     * Adds to or creates the mcp.servers object hierarchy.
     */
    static JsonNode mergeVSCodeConfig(JsonNode existingJson, McpClientConfig config) {
        ObjectNode root = (ObjectNode) existingJson;

        // Get or create mcp object
        ObjectNode mcp;
        if (root.has("mcp")) {
            mcp = (ObjectNode) root.get("mcp");
        } else {
            mcp = objectMapper.createObjectNode();
            root.set("mcp", mcp);
        }

        // Get or create servers object
        ObjectNode servers;
        if (mcp.has("servers")) {
            servers = (ObjectNode) mcp.get("servers");
        } else {
            servers = objectMapper.createObjectNode();
            mcp.set("servers", servers);
        }

        // Create and add server configuration
        ObjectNode serverConfig = createServerConfig(config);
        servers.set(config.serverName, serverConfig);

        return root;
    }

    /**
     * Merges configuration for Continue format.
     * Adds to the mcpServers array.
     */
    static JsonNode mergeContinueConfig(JsonNode existingJson, McpClientConfig config) {
        ObjectNode root = (ObjectNode) existingJson;

        // Get or create mcpServers array
        ArrayNode mcpServers;
        if (root.has("mcpServers")) {
            mcpServers = (ArrayNode) root.get("mcpServers");
        } else {
            mcpServers = objectMapper.createArrayNode();
            root.set("mcpServers", mcpServers);
        }

        // Create server configuration
        ObjectNode serverConfig = objectMapper.createObjectNode();
        serverConfig.put("name", config.serverName);
        serverConfig.put("command", config.javaPath);

        ArrayNode args = objectMapper.createArrayNode();
        args.add("-jar");
        args.add(config.jarPath);
        serverConfig.set("args", args);

        ObjectNode env = objectMapper.createObjectNode();
        env.put("DB_URL", config.dbUrl);
        env.put("DB_DRIVER", config.dbDriver);
        env.put("DB_USER", config.dbUser);
        env.put("DB_PASSWORD", config.dbPassword);
        serverConfig.set("env", env);

        // Add server to array
        mcpServers.add(serverConfig);

        return root;
    }

    public String generateConfigurationContent(McpClientConfig config, ClientType clientType) {
        return switch (clientType) {
            case CURSOR, WINDSURF, CLAUDE_DESKTOP -> generateMcpServersJson(config);
            case CONTINUE -> generateContinueJson(config);
            case VSCODE -> generateVSCodeJson(config);
            case CLAUDE_CODE -> generateClaudeCodeCommand(config);
            case GEMINI_CLI -> generateGeminiCliJson(config);
            case ZED -> generateZedJson(config);
        };
    }

    // ============================================================================
    // JSON GENERATION METHODS USING JACKSON
    // ============================================================================

    /**
     * Generates standard MCP servers JSON format used by Cursor, Windsurf, Claude Desktop.
     * Uses Jackson for proper JSON generation.
     */
    static String generateMcpServersJson(McpClientConfig clientConfig) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode mcpServers = objectMapper.createObjectNode();

            ObjectNode serverConfig = createServerConfig(clientConfig);
            mcpServers.set(clientConfig.serverName, serverConfig);
            root.set("mcpServers", mcpServers);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate MCP servers JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Generates Continue-specific JSON format (uses array structure).
     */
    static String generateContinueJson(McpClientConfig clientConfig) {
        try {
            ObjectNode root = objectMapper.createObjectNode();

            // Continue requires models array
            ArrayNode models = objectMapper.createArrayNode();
            root.set("models", models);

            // Create mcpServers array
            ArrayNode mcpServers = objectMapper.createArrayNode();
            ObjectNode serverConfig = objectMapper.createObjectNode();

            serverConfig.put("name", clientConfig.serverName);
            serverConfig.put("command", clientConfig.javaPath);

            ArrayNode args = objectMapper.createArrayNode();
            args.add("-jar");
            args.add(clientConfig.jarPath);
            serverConfig.set("args", args);

            ObjectNode env = objectMapper.createObjectNode();
            env.put("DB_URL", clientConfig.dbUrl);
            env.put("DB_DRIVER", clientConfig.dbDriver);
            env.put("DB_USER", clientConfig.dbUser);
            env.put("DB_PASSWORD", clientConfig.dbPassword);
            serverConfig.set("env", env);

            mcpServers.add(serverConfig);
            root.set("mcpServers", mcpServers);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Continue JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Generates VS Code-specific JSON format (nested under mcp.servers).
     */
    static String generateVSCodeJson(McpClientConfig config) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode mcp = objectMapper.createObjectNode();
            ObjectNode servers = objectMapper.createObjectNode();

            ObjectNode serverConfig = createServerConfig(config);
            servers.set(config.serverName, serverConfig);
            mcp.set("servers", servers);
            root.set("mcp", mcp);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate VS Code JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Generates Zed-specific JSON format (uses context_servers with source: custom).
     */
    static String generateZedJson(McpClientConfig config) {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ObjectNode contextServers = objectMapper.createObjectNode();

            ObjectNode serverConfig = createServerConfig(config);
            // Zed requires "source": "custom" for custom servers
            serverConfig.put("source", "custom");

            contextServers.set(config.serverName, serverConfig);
            root.set("context_servers", contextServers);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Zed JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Generates Gemini CLI JSON format (standard mcpServers format).
     */
    static String generateGeminiCliJson(McpClientConfig config) {
        // Same as standard MCP format
        return generateMcpServersJson(config);
    }

    /**
     * Generates Claude Code shell command.
     */
    static String generateClaudeCodeCommand(McpClientConfig config) {
        return String.format("""
               claude mcp add %s -e DB_URL=%s -e DB_DRIVER=%s -e DB_USER=%s -e DB_PASSWORD=%s -- %s -jar %s""",
                config.serverName,
                escapeShellString(config.dbUrl),
                escapeShellString(config.dbDriver),
                escapeShellString(config.dbUser),
                escapeShellString(config.dbPassword),
                escapeShellString(config.javaPath),
                escapeShellString(config.jarPath)
        );
    }

    void saveConfigurationFile(String content, String fileName, ClientType clientType, McpClientConfig config) {
        // Always write to DEST_DIR first
        String destPath = DEST_DIR + "/" + fileName;

        if (clientType == ClientType.CLAUDE_CODE) {
            fileOps.writeFile(destPath, content);
            inputReader.println("Configuration saved to: " + destPath);
            return;
        }

        String configPath = getConfigPath(clientType.configKey);
        boolean hasExistingConfig = fileOps.fileExists(configPath);

        // Try to merge with existing config if it exists
        if (hasExistingConfig) {
            String existingConfig = fileOps.readFile(configPath);
            if (existingConfig != null && !existingConfig.trim().isEmpty()) {
                try {
                    String mergedConfig = mergeConfiguration(existingConfig, config, clientType);
                    // Always write merged config to DEST_DIR first
                    fileOps.writeFile(destPath, mergedConfig);
                    inputReader.println("Configuration merged and saved to: " + destPath);

                    // Ask user if they want to overwrite the actual config file
                    if (shouldOverwriteActualConfig(configPath)) {
                        // Create backup before overwriting
                        fileOps.createBackup(configPath);
                        fileOps.writeFile(configPath, mergedConfig);
                        inputReader.println("Actual configuration file updated: " + configPath);
                        inputReader.println("Backup created for safety.");
                    }
                    return;
                } catch (Exception e) {
                    inputReader.println("Warning: Could not merge with existing config. Creating standalone version.");
                }
            }
        }

        // Write standalone config to DEST_DIR
        fileOps.writeFile(destPath, content);
        inputReader.println("Configuration saved to: " + destPath);

        // If there was an existing config, ask user about overwriting
        if (hasExistingConfig && shouldOverwriteActualConfig(configPath)) {
            fileOps.createBackup(configPath);
            fileOps.writeFile(configPath, content);
            inputReader.println("Actual configuration file updated: " + configPath);
            inputReader.println("Backup created for safety.");
        }
    }

    /**
     * Asks user if they want to overwrite the actual configuration file.
     * Only called AFTER backup is made.
     */
    private boolean shouldOverwriteActualConfig(String configPath) {
        inputReader.println("\nFound existing configuration file at: " + configPath);
        inputReader.println("Do you want to update the actual configuration file?");
        inputReader.println("(A backup will be created automatically)");
        String response = inputReader.readLine("Update actual config file? (y/n): ");
        return response.toLowerCase().startsWith("y");
    }

    // Helper methods that can now be tested
    boolean shouldTestConnection() {
        return true; // Could be configurable
    }

    boolean testDatabaseConnection(String url, String driver, String user, String password) {
        try {
            Class.forName(driver);
            try (Connection ignored = DriverManager.getConnection(url, user, password)) {
                inputReader.println("Successfully connected to database.");
                return true;
            }
        } catch (Exception e) {
            inputReader.println("Database connection failed: " + e.getMessage());
            return false;
        }
    }

    String detectJarPath() {
        String jarPath = getJar();
        if (jarPath.contains("CANNOT LOCATE")) {
            jarPath = inputReader.readLine("Please enter the absolute path to your DBChat JAR file: ");
        }
        return jarPath;
    }

    String detectJavaPath() {
        String javaPath = getJava();
        if (javaPath.contains("CANNOT LOCATE")) {
            javaPath = inputReader.readLine("Please enter the path to your Java executable (or 'java' if in PATH): ");
            if (javaPath.isEmpty()) javaPath = "java";
        }
        return javaPath;
    }

    void displayClientOptions() {
        inputReader.println("\nSupported MCP Clients:");
        inputReader.println("1. Cursor");
        inputReader.println("2. Windsurf");
        inputReader.println("3. Claude Desktop");
        inputReader.println("4. Continue");
        inputReader.println("5. VS Code");
        inputReader.println("6. Claude Code (command line)");
        inputReader.println("7. Gemini CLI (command line)");
        inputReader.println("8. Zed Editor");
        inputReader.println("9. Generate all configurations");
    }

    void generateAllConfigurations(McpClientConfig config) {
        for (ClientType clientType : ClientType.values()) {
            if (clientType != ClientType.CLAUDE_CODE) {
                generateAndSaveConfiguration(config, clientType);
            }
        }
        inputReader.println("\nAll configurations generated successfully!");
    }

    /**
     * Attempts to auto-detect the JAR file location using CodeSource.
     * Falls back to manual entry if detection fails.
     *
     * @return Absolute path to the JAR file or error message
     */
    static String getJar() {
        try {
            CodeSource codeSource = ClientConfig.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                File jarFile = new File(codeSource.getLocation().toURI().getPath());
                return jarFile.getAbsolutePath();
            } else {
                return "CANNOT LOCATE JAR FILE - PLEASE LOCATE MANUALLY";
            }
        } catch (URISyntaxException e) {
            System.out.println("Warning: " + e.getMessage());
            return "CANNOT LOCATE JAR FILE - PLEASE LOCATE MANUALLY";
        }
    }

    /**
     * Attempts to auto-detect the Java executable path.
     * Uses ProcessHandle to find the current Java process command.
     *
     * @return Path to Java executable or error message
     */
    static String getJava() {
        Optional<String> javaCommand = ProcessHandle.current().info().command();
        return javaCommand.orElse("CANNOT LOCATE JAVA - PLEASE LOCATE MANUALLY");
    }

    /**
     * Formats the client type enum name into a user-friendly display name.
     */
    static String formatClientName(ClientType clientType) {
        return clientType.name().charAt(0) +
               clientType.name().substring(1).toLowerCase().replace("_", " ");
    }

    /**
     * Determines the standard configuration file path for each client on different operating systems.
     *
     * @param client The client identifier (cursor, zed, etc.)
     * @return Full path to the configuration file
     */
    static String getConfigPath(String client) {
        String os = System.getProperty("os.name");
        String userHome = System.getProperty("user.home");
        String appData = System.getenv("APPDATA");
        return getConfigPath(client, os, userHome, appData);
    }

    /**
     * The version of getConfigPath that accepts OS name and paths as parameters.
     * This allows testing without mocking System methods.
     */
    static String getConfigPath(String client, String osName, String userHome, String appData) {
        if (osName.toLowerCase().contains("win")) {
            return switch (client) {
                case "cursor" -> appData + "\\Cursor\\mcp.json";
                case "windsurf" -> appData + "\\Windsurf\\mcp.json";
                case "claude-desktop" -> appData + "\\Claude\\claude_desktop_config.json";
                case "zed" -> appData + "\\Zed\\settings.json";
                default -> appData + "\\" + client + "\\config.json";
            };
        } else if (osName.toLowerCase().contains("mac")) {
            return switch (client) {
                case "cursor" -> userHome + "/.cursor/mcp.json";
                case "windsurf" -> userHome + "/.windsurf/mcp.json";
                case "claude-desktop" -> userHome + "/Library/Application Support/Claude/claude_desktop_config.json";
                case "zed" -> userHome + "/.config/zed/settings.json";
                default -> userHome + "/." + client + "/config.json";
            };
        } else {
            // Linux and other Unix-like systems
            return switch (client) {
                case "cursor" -> userHome + "/.config/cursor/mcp.json";
                case "windsurf" -> userHome + "/.config/windsurf/mcp.json";
                case "claude-desktop" -> userHome + "/.config/claude/claude_desktop_config.json";
                case "zed" -> userHome + "/.config/zed/settings.json";
                default -> userHome + "/.config/" + client + "/config.json";
            };
        }
    }

    static class McpClientConfig {
        final String dbUrl, dbDriver, dbUser, dbPassword, serverName, jarPath, javaPath;

        McpClientConfig(String dbUrl, String dbDriver, String dbUser, String dbPassword,
                        String serverName, String jarPath, String javaPath) {
            this.dbUrl = dbUrl; this.dbDriver = dbDriver; this.dbUser = dbUser;
            this.dbPassword = dbPassword; this.serverName = serverName;
            this.jarPath = jarPath; this.javaPath = javaPath;
        }
    }

    enum ClientType {
        CURSOR("cursor", "cursor_mcp.json", true),
        WINDSURF("windsurf", "windsurf_mcp.json", true),
        CLAUDE_DESKTOP("claude-desktop", "claude_desktop_config.json", true),
        CONTINUE("continue", "continue_config.json", false),
        VSCODE("vscode", "vscode_settings.json", false),
        CLAUDE_CODE("claude-code", "claude_code_command.sh", false),
        GEMINI_CLI("gemini-cli", "gemini_cli_settings.json", false),
        ZED("zed", "zed_settings.json", false);

        final String configKey, fileName;
        final boolean useStandardMcpFormat;

        ClientType(String configKey, String fileName, boolean useStandardMcpFormat) {
            this.configKey = configKey; this.fileName = fileName;
            this.useStandardMcpFormat = useStandardMcpFormat;
        }
    }

    /**
     * Escapes special characters in shell strings.
     * Basic shell escaping for command generation.
     *
     * @param inString The string to escape
     * @return Shell-escaped string
     */
    static String escapeShellString(String inString) {
        if (inString == null) return "";
        // Basic shell escaping - wrap in quotes if contains spaces or special chars
        if (inString.contains(" ") || inString.contains("&") || inString.contains("|") || inString.contains(";") || inString.contains("\"")) {
            return "\"" + inString.replace("\"", "\\\"") + "\"";
        }
        return inString;
    }
}
