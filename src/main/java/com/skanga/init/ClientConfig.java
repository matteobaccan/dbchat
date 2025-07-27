package com.skanga.init;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/**
 * DBChat MCP Configuration Generator
 * <p>
 * This tool generates MCP (Model Context Protocol) configuration files for various AI clients,
 * allowing them to connect to database servers through the DBChat MCP server.
 * <p>
 * Features:
 * - Supports multiple MCP clients (Cursor, Windsurf, Claude Desktop, Continue, VS Code, Zed, etc.)
 * - Auto-detects Java and JAR paths
 * - Creates timestamped backups of existing configurations
 * - Merges new configurations with existing ones instead of overwriting
 * - Provides both safe and direct installation options
 */
public class ClientConfig {
    // Jackson ObjectMapper for JSON manipulation
    private static final ObjectMapper objectMapper = new ObjectMapper();
    static final String DEFAULT_DB_URL = "jdbc:h2:mem:test";
    static final String DEFAULT_DB_USER = "sa";
    static final String DEFAULT_DB_PASSWORD = "";
    static final String DEFAULT_DB_DRIVER = "org.h2.Driver";
    static final String DEST_DIR = "./conf";

    /**
     * Enum defining all supported MCP clients with their configuration properties.
     * This eliminates code duplication and makes adding new clients easier.
     */
    enum ClientType {
        CURSOR("cursor", "cursor_mcp.json", true),
        WINDSURF("windsurf", "windsurf_mcp.json", true),
        CLAUDE_DESKTOP("claude-desktop", "claude_desktop_config.json", true),
        CONTINUE("continue", "continue_config.json", false),
        VSCODE("vscode", "vscode_settings.json", false),
        CLAUDE_CODE("claude-code", "claude_code_command.sh", false),
        GEMINI_CLI("gemini-cli", "gemini_cli_settings.json", false),
        ZED("zed", "zed_settings.json", false);

        final String configKey;           // Key used for path resolution
        final String fileName;            // Generated filename
        final boolean useStandardMcpFormat;  // Whether to use standard mcpServers format

        ClientType(String configKey, String fileName, boolean useStandardMcpFormat) {
            this.configKey = configKey;
            this.fileName = fileName;
            this.useStandardMcpFormat = useStandardMcpFormat;
        }
    }

    /**
     * Configuration data class holding all database and server connection details.
     * This is immutable by design for thread safety.
     */
    static class McpClientConfig {
        final String dbUrl;
        final String dbDriver;
        final String dbUser;
        final String dbPassword;
        final String serverName;
        final String jarPath;
        final String javaPath;

        McpClientConfig(String dbUrl, String dbDriver, String dbUser, String dbPassword,
                        String serverName, String jarPath, String javaPath) {
            this.dbUrl = dbUrl;
            this.dbDriver = dbDriver;
            this.dbUser = dbUser;
            this.dbPassword = dbPassword;
            this.serverName = serverName;
            this.jarPath = jarPath;
            this.javaPath = javaPath;
        }
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
     * Main entry point for the configuration generator.
     * Handles the complete workflow from input collection to file generation.
     */
    public static void main(String[] args) {
        System.out.println("=== DBChat MCP Configuration Generator ===\n");
        Scanner inputScanner = new Scanner(System.in);

        try {
            // Step 1: Collect database configuration from user
            McpClientConfig clientConfig = collectDatabaseInfo(inputScanner);

            // Step 2: Display available client options
            displayClientOptions();

            // Step 3: Get user's client choice
            int clientChoice = getClientChoice(inputScanner);

            // Step 4: Generate and save configuration
            generateConfiguration(clientConfig, clientChoice, inputScanner);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Collects database connection information from the user.
     * Also auto-detects Java and JAR paths with manual override options.
     *
     * @return McpClientConfig object containing all configuration data
     */
    static McpClientConfig collectDatabaseInfo(Scanner inputScanner) {
        System.out.println("Please provide your database connection details (press Enter to use default):\n");

        String dbUrl, dbDriver, dbUser, dbPassword;

        while (true) {
            System.out.print("Database JDBC URL [default: " + DEFAULT_DB_URL + "]: ");
            dbUrl = inputScanner.nextLine().trim();
            if (dbUrl.isEmpty()) dbUrl = DEFAULT_DB_URL;

            System.out.print("Database JDBC Driver [default: " + DEFAULT_DB_DRIVER + "]: ");
            dbDriver = inputScanner.nextLine().trim();
            if (dbDriver.isEmpty()) dbDriver = DEFAULT_DB_DRIVER;

            System.out.print("Database Username [default: " + DEFAULT_DB_USER + "]: ");
            dbUser = inputScanner.nextLine().trim();
            if (dbUser.isEmpty()) dbUser = DEFAULT_DB_USER;

            System.out.print("Database Password [default: " + (DEFAULT_DB_PASSWORD.isEmpty() ? "(empty)" : DEFAULT_DB_PASSWORD) + "]: ");
            dbPassword = inputScanner.nextLine();
            if (dbPassword.isEmpty()) dbPassword = DEFAULT_DB_PASSWORD;

            // Attempt DB connection
            try {
                Class.forName(dbDriver);
                try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword)) {
                    System.out.println("Successfully connected to database.");
                    break;
                }
            } catch (Exception e) {
                System.out.println("\nFailed to connect to the database:");
                System.out.println("   " + e.getMessage());
                System.out.print("\nWould you like to retry with different credentials? (Y/n): ");
                String choice = inputScanner.nextLine().trim().toLowerCase();
                if (choice.equals("n") || choice.equals("no")) {
                    System.out.println("Proceeding with provided configuration...");
                    break;
                }
            }
        }

        System.out.print("Server name (default: my-database): ");
        String serverName = inputScanner.nextLine().trim();
        if (serverName.isEmpty()) serverName = "my-database";

        String jarPath = getJar();
        String javaPath = getJava();

        if (jarPath.contains("CANNOT LOCATE")) {
            System.out.print("Please enter the absolute path to your DBChat JAR file: ");
            jarPath = inputScanner.nextLine().trim();
        }

        if (javaPath.contains("CANNOT LOCATE")) {
            System.out.print("Please enter the path to your Java executable (or 'java' if in PATH): ");
            javaPath = inputScanner.nextLine().trim();
            if (javaPath.isEmpty()) javaPath = "java";
        }

        return new McpClientConfig(dbUrl, dbDriver, dbUser, dbPassword, serverName, jarPath, javaPath);
    }

    /**
     * Displays all supported MCP clients to the user.
     * Updated to include all available options.
     */
    static void displayClientOptions() {
        System.out.println("\nSupported MCP Clients:");
        System.out.println("1. Cursor");
        System.out.println("2. Windsurf");
        System.out.println("3. Claude Desktop");
        System.out.println("4. Continue");
        System.out.println("5. VS Code");
        System.out.println("6. Claude Code (command line)");
        System.out.println("7. Gemini CLI (command line)");
        System.out.println("8. Zed Editor");
        System.out.println("9. Generate all configurations");
    }

    /**
     * Gets and validates the user's choice of MCP client.
     * Ensures the choice is within the valid range.
     *
     * @return Valid choice number (1-9)
     */
    static int getClientChoice(Scanner inputScanner) {
        while (true) {
            System.out.print("\nSelect your MCP client (1-9): ");
            try {
                int clientChoice = Integer.parseInt(inputScanner.nextLine().trim());
                if (clientChoice >= 1 && clientChoice <= 9) {
                    return clientChoice;
                }
                System.out.println("Please enter a number between 1 and 9.");
            } catch (NumberFormatException e) {
                System.out.println("Please enter a valid number.");
            }
        }
    }

    /**
     * Routes to the appropriate configuration generation method based on user choice.
     *
     * @param clientConfig The database configuration
     * @param clientChoice The user's selected client (1-9)
     */
    static void generateConfiguration(McpClientConfig clientConfig, int clientChoice, Scanner inputScanner) throws IOException {
        switch (clientChoice) {
            case 1 -> generateClientConfig(clientConfig, ClientType.CURSOR, inputScanner);
            case 2 -> generateClientConfig(clientConfig, ClientType.WINDSURF, inputScanner);
            case 3 -> generateClientConfig(clientConfig, ClientType.CLAUDE_DESKTOP, inputScanner);
            case 4 -> generateClientConfig(clientConfig, ClientType.CONTINUE, inputScanner);
            case 5 -> generateClientConfig(clientConfig, ClientType.VSCODE, inputScanner);
            case 6 -> generateClientConfig(clientConfig, ClientType.CLAUDE_CODE, inputScanner);
            case 7 -> generateClientConfig(clientConfig, ClientType.GEMINI_CLI, inputScanner);
            case 8 -> generateClientConfig(clientConfig, ClientType.ZED, inputScanner);
            case 9 -> generateAllConfigurations(clientConfig, inputScanner);
        }
    }

    /**
     * Unified method to generate configuration for any client type.
     * Uses the ClientType enum to eliminate code duplication.
     *
     * @param clientConfig The database configuration
     * @param clientType The specific client type to generate for
     */
    static void generateClientConfig(McpClientConfig clientConfig, ClientType clientType, Scanner inputScanner) throws IOException {
        // Generate the appropriate content based on client type
        String content = switch (clientType) {
            case CURSOR, WINDSURF, CLAUDE_DESKTOP -> generateMcpServersJson(clientConfig);
            case CONTINUE -> generateContinueJson(clientConfig);
            case VSCODE -> generateVSCodeJson(clientConfig);
            case CLAUDE_CODE -> generateClaudeCodeCommand(clientConfig);
            case GEMINI_CLI -> generateGeminiCliJson(clientConfig);
            case ZED -> generateZedJson(clientConfig);
        };

        // Save the configuration with backup and merge capabilities
        saveConfiguration(content, clientType.fileName, clientType, clientConfig);

        // Provide client-specific instructions
        if (clientType == ClientType.CLAUDE_CODE) {
            System.out.println("\nClaude Code command generated!");
            System.out.println("Run the command from: " + clientType.fileName);
            System.out.println("\nOr copy and paste this command:");
            System.out.println(content);
        } else {
            String clientName = formatClientName(clientType);
            System.out.println(clientName + " configuration generated!");

            // Provide specific installation instructions
            provideInstallationInstructions(clientType);

            // Offer direct installation for supported clients (excluding workspace-specific ones)
            if (clientType != ClientType.VSCODE) {
                offerDirectInstallation(clientConfig, clientType, inputScanner);
            }
        }
    }

    /**
     * Formats the client type enum name into a user-friendly display name.
     */
    static String formatClientName(ClientType clientType) {
        return clientType.name().charAt(0) +
               clientType.name().substring(1).toLowerCase().replace("_", " ");
    }

    /**
     * Provides client-specific installation instructions.
     */
    static void provideInstallationInstructions(ClientType clientType) {
        switch (clientType) {
            case CURSOR, WINDSURF, CLAUDE_DESKTOP, GEMINI_CLI ->
                    System.out.println("Save this to: " + getConfigPath(clientType.configKey));
            case CONTINUE ->
                    System.out.println("Save this to: ~/.continue/config.json");
            case VSCODE ->
                    System.out.println("Save this to: .vscode/settings.json in your workspace");
            case CLAUDE_CODE ->
                    System.out.println("Run the generated command directly in your terminal");
            case ZED -> {
                System.out.println("Configuration options:");
                System.out.println("1. Add to Zed settings via: Agent Panel > Settings > Add Custom Server");
                System.out.println("2. Or merge into your existing Zed settings.json");
                System.out.println("3. Access settings via: Command Palette > 'zed: open settings'");
            }
        }
    }

    /**
     * Generates all configurations at once (excluding commands).
     * Useful for users who want to try multiple clients.
     */
    static void generateAllConfigurations(McpClientConfig clientConfig, Scanner inputScanner) throws IOException {
        for (ClientType clientType : ClientType.values()) {
            if (clientType != ClientType.CLAUDE_CODE) { // Skip command generation in bulk
                generateClientConfig(clientConfig, clientType, inputScanner);
            }
        }
        System.out.println("\nAll configurations generated successfully!");
        System.out.println("Check the current directory for the generated files.");
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
            ((ObjectNode) serverConfig).put("source", "custom");

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

    // ============================================================================
    // FILE HANDLING AND BACKUP METHODS
    // ============================================================================

    /**
     * Creates a timestamped backup of an existing configuration file.
     * Uses format: filename_backup_yyyyMMdd_HHmmss.ext
     *
     * @param configPath Path to the configuration file to backup
     */
    static void createBackup(String configPath) {
        try {
            Path originalPath = Paths.get(configPath);
            if (!Files.exists(originalPath)) {
                return; // No existing file to backup
            }

            // Create timestamp for backup filename
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
            String timestamp = now.format(formatter);

            // Create backup filename
            String originalFilename = originalPath.getFileName().toString();
            String backupFilename;
            int dotIndex = originalFilename.lastIndexOf('.');
            if (dotIndex > 0) {
                String name = originalFilename.substring(0, dotIndex);
                String extension = originalFilename.substring(dotIndex);
                backupFilename = name + "_backup_" + timestamp + extension;
            } else {
                backupFilename = originalFilename + "_backup_" + timestamp;
            }

            Path backupPath = originalPath.getParent().resolve(backupFilename);

            // Copy the file
            Files.copy(originalPath, backupPath);
            System.out.println("Backup '" + backupPath.toAbsolutePath() + "' created for original config file '" + originalPath.toAbsolutePath() + "'");

        } catch (IOException e) {
            System.out.println("Warning: Could not create backup of existing config: " + e.getMessage());
            System.out.println("  Proceeding with configuration generation...");
        }
    }

    /**
     * Reads existing configuration file content.
     * Returns null if file doesn't exist or can't be read.
     *
     * @param filePath Path to the configuration file
     * @return File content as string, or null if not readable
     */
    static String readExistingConfig(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                return Files.readString(path);
            }
        } catch (IOException e) {
            System.out.println("Note: Could not read existing config file: " + e.getMessage());
        }
        return null;
    }

    /**
     * Enhanced saveConfiguration method that creates backups and merges with existing configs.
     *
     * @param content The configuration content to save
     * @param fileName The filename to save to
     * @param clientType The client type (determines merge strategy)
     * @param clientConfig The database configuration
     */
    static void saveConfiguration(String content, String fileName, ClientType clientType, McpClientConfig clientConfig) throws IOException {
        // For command files, just save directly (no config to backup)
        if (clientType == ClientType.CLAUDE_CODE) {
            Path path = Paths.get(fileName);
            Files.write(path, content.getBytes());
            System.out.println("Configuration saved to: " + path.toAbsolutePath());
            return;
        }

        // Get the actual config path and create backup if file exists
        String configPath = getConfigPath(clientType.configKey);
        createBackup(configPath);

        // Try to merge with existing config for JSON files
        String existingConfig = readExistingConfig(configPath);

        if (existingConfig != null && !existingConfig.trim().isEmpty()) {
            try {
                String mergedConfig = mergeConfiguration(existingConfig, clientConfig, clientType);
                Path path = Paths.get(fileName);
                Files.write(path, mergedConfig.getBytes());
                System.out.println("Final configuration saved to: " + path.toAbsolutePath());
                System.out.println("NEXT STEPS:");
                System.out.println("  1. Review the merged configuration file");
                System.out.println("  2. Replace your config at: " + configPath);
                System.out.println("  3. If there are issues, restore from backup");

            } catch (Exception e) {
                // Fallback to original behavior if merge fails
                System.out.println("Warning: Could not add to existing config: " + e.getMessage());
                System.out.println("Generating standalone config instead.");
                Path path = Paths.get(fileName);
                Files.write(path, content.getBytes());
                System.out.println("Standalone configuration saved to: " + path.toAbsolutePath());
            }
        } else {
            // No existing config, save new one
            Path path = Paths.get(DEST_DIR, fileName);
            Files.write(path, content.getBytes());
            System.out.println("New configuration saved to: " + path.toAbsolutePath());
            System.out.println("Copy this file to: " + configPath);
        }
    }

    /**
     * Offers the user the option to install configuration directly to the target location.
     * This bypasses the manual copy step but requires more caution.
     *
     * @param config The database configuration
     * @param clientType The client type
     */
    static void offerDirectInstallation(McpClientConfig config, ClientType clientType, Scanner inputScanner) {
        if (clientType == ClientType.CLAUDE_CODE) {
            return; // Not applicable for command files
        }

        System.out.print("\nWould you like to install the configuration directly? (y/N): ");
        String response = inputScanner.nextLine().trim().toLowerCase();

        if (response.equals("y") || response.equals("yes")) {
            try {
                saveConfigurationDirect(config, clientType);
            } catch (Exception e) {
                System.out.println("Direct installation failed. The generated file is still available for manual copying.");
            }
        } else {
            System.out.println("Manual installation: Copy the generated file to the location shown above");
        }
    }

    /**
     * Directly installs configuration to the target location with backup.
     * More convenient but requires elevated caution.
     *
     * @param config The database configuration
     * @param clientType The client type
     */
    static void saveConfigurationDirect(McpClientConfig config, ClientType clientType) throws IOException {
        if (clientType == ClientType.CLAUDE_CODE) {
            System.out.println("Direct save not applicable for command files.");
            return;
        }

        String configPath = getConfigPath(clientType.configKey);

        // Always create backup before direct replacement
        createBackup(configPath);

        String existingConfig = readExistingConfig(configPath);

        try {
            String finalConfig;
            if (existingConfig != null && !existingConfig.trim().isEmpty()) {
                finalConfig = mergeConfiguration(existingConfig, config, clientType);
                System.out.println("Added with existing configuration");
            } else {
                finalConfig = switch (clientType) {
                    case CURSOR, WINDSURF, CLAUDE_DESKTOP -> generateMcpServersJson(config);
                    case CONTINUE -> generateContinueJson(config);
                    case VSCODE -> generateVSCodeJson(config);
                    case GEMINI_CLI -> generateGeminiCliJson(config);
                    case ZED -> generateZedJson(config);
                    default -> throw new IllegalArgumentException("Unsupported client type for direct save");
                };
                System.out.println("Created new configuration");
            }

            // Write directly to the config location
            Path configFilePath = Paths.get(configPath);

            // Ensure parent directory exists
            Files.createDirectories(configFilePath.getParent());

            Files.write(configFilePath, finalConfig.getBytes());
            System.out.println("Configuration installed to: " + configPath);
            System.out.println("Restart " + formatClientName(clientType) + " to use the new configuration");
        } catch (Exception e) {
            System.err.println("ERROR: Failed to save configuration directly: " + e.getMessage());
            System.out.println("Try the safe mode (generate file first, then copy manually)");
            throw e;
        }
    }

    // ============================================================================
    // UTILITY METHODS
    // ============================================================================

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

    /**
     * Escapes special characters in shell strings.
     * Basic shell escaping for command generation.
     *
     * @param str The string to escape
     * @return Shell-escaped string
     */
    static String escapeShellString(String str) {
        if (str == null) return "";
        // Basic shell escaping - wrap in quotes if contains spaces or special chars
        if (str.contains(" ") || str.contains("&") || str.contains("|") || str.contains(";")) {
            return "\"" + str.replace("\"", "\\\"") + "\"";
        }
        return str;
    }
}