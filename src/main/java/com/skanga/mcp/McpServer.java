package com.skanga.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic MCP Server for Database Operations
 * Supports multiple database types through JDBC drivers
 * Supports both stdio and HTTP mode MCP servers
 */
public class McpServer {
    private static final Logger logger = LoggerFactory.getLogger(McpServer.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    final DatabaseService databaseService;
    private final Map<String, Object> serverInfo;

    public McpServer(ConfigParams configParams) {
        this.databaseService = createDatabaseService(configParams);
        this.serverInfo = createServerInfo();
    }

    public McpServer(DatabaseService databaseService) {
        this.databaseService = databaseService;
        this.serverInfo = createServerInfo();
    }

    protected DatabaseService createDatabaseService(ConfigParams configParams) {
        return new DatabaseService(configParams);
    }

    /**
     * Start the server in HTTP mode
     */
    public void startHttpMode(int port) throws IOException {
        logger.info("Starting Database MCP Server in HTTP mode on port {}...", port);

        HttpServer server = null;
        try {
            // Try to create the server - this will fail immediately if port is in use
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/mcp", new MCPHttpHandler());
            server.createContext("/health", new HealthCheckHandler());
            server.setExecutor(null); // Use default executor

            // Start the server
            server.start();

            logger.info("Database MCP Server HTTP mode started successfully on port {}", port);
            logger.info("MCP endpoint: http://localhost:{}/mcp", port);
            logger.info("Health check: http://localhost:{}/health", port);
            logger.info("Press Ctrl+C to stop the server");

            // Keep the main thread alive
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                logger.info("Server interrupted, shutting down...");
            }
        } catch (java.net.BindException e) {
            logger.error("ERROR: Failed to start HTTP server: Port {} is already in use", port);
            logger.error("Please try a different port or stop the service using port {}", port);
            logger.error("You can specify a different port with: --http_port=<port>");
            throw new IOException("Port " + port + " is already in use", e);
        } catch (IOException e) {
            logger.error("ERROR: Failed to start HTTP server on port {}: {}", port, e.getMessage());
            throw new IOException("Failed to start HTTP server on port " + port, e);
        } finally {
            // Always try to stop the server if it was created
            if (server != null) {
                try {
                    server.stop(5);
                    logger.info("HTTP server stopped");
                } catch (Exception e) {
                    logger.warn("Error stopping HTTP server: {}", e.getMessage());
                }
            }
        }
    }

    /**
     * HTTP handler for MCP requests
     */
    private class MCPHttpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Set CORS headers for browser clients
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

            // Handle preflight OPTIONS request
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().close();
                return;
            }

            if (!"POST".equals(exchange.getRequestMethod())) {
                sendErrorResponse(exchange, 405, "Method not allowed. Use POST.");
                return;
            }

            try {
                // Read request body
                String requestBody = readRequestBody(exchange);
                logger.debug("Received HTTP request: {}", requestBody);

                // Parse and handle the MCP request
                JsonNode requestNode = objectMapper.readTree(requestBody);
                JsonNode responseNode = handleRequest(requestNode);

                // Send response (only if not a notification)
                if (responseNode != null) {
                    String responseJson = objectMapper.writeValueAsString(responseNode);
                    logger.debug("Sending HTTP response: {}", responseJson);

                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    byte[] responseBytes = responseJson.getBytes();
                    exchange.sendResponseHeaders(200, responseBytes.length);

                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(responseBytes);
                    }
                } else {
                    // Notification - send empty 204 response
                    exchange.sendResponseHeaders(204, 0);
                    exchange.getResponseBody().close();
                }

            } catch (Exception e) {
                logger.error("Error handling HTTP request", e);
                sendErrorResponse(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }

        private String readRequestBody(HttpExchange exchange) throws IOException {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(exchange.getRequestBody()))) {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
                return body.toString();
            }
        }

        private void sendErrorResponse(HttpExchange exchange, int statusCode, String message) throws IOException {
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("error", message);

            String responseJson = objectMapper.writeValueAsString(errorResponse);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] responseBytes = responseJson.getBytes();

            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    /**
     * Health check handler
     */
    private class HealthCheckHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            ObjectNode healthResponse = objectMapper.createObjectNode();
            healthResponse.put("status", "healthy");
            healthResponse.put("server", "Database MCP Server");
            healthResponse.put("timestamp", System.currentTimeMillis());

            // Test database connection
            try {
                databaseService.getConnection().close(); // Just test the connection
                healthResponse.put("database", "connected");
            } catch (Exception e) {
                healthResponse.put("database", "error: " + e.getMessage());
            }

            String responseJson = objectMapper.writeValueAsString(healthResponse);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] responseBytes = responseJson.getBytes();

            exchange.sendResponseHeaders(200, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }

    protected JsonNode handleRequest(JsonNode requestNode) {
        String requestMethod = requestNode.path("method").asText();
        JsonNode requestParams = requestNode.path("params");
        
        // Check if this is a notification (no id field at all)
        boolean isNotification = !requestNode.has("id");
        Object requestId = isNotification ? null : requestNode.get("id");

        logger.debug("Handling request: method={}, id={}, isNotification={}",
                     requestMethod, requestId, isNotification);
        
        try {
            JsonNode resultNode = switch (requestMethod) {
                case "initialize" -> handleInitialize();
                case "tools/list" -> handleListTools();
                case "tools/call" -> handleCallTool(requestParams);
                case "resources/list" -> handleListResources();
                case "resources/read" -> handleReadResource(requestParams);
                default -> throw new IllegalArgumentException("Method not found: " + requestMethod);
            };

            // For notifications, return null to indicate no response should be sent
            if (isNotification) {
                return null;
            }

            return createSuccessResponse(resultNode, requestId);

        } catch (IllegalArgumentException e) {
            if (e.getMessage().startsWith("Method not found:")) {
                logger.warn("Method not found: {}", e.getMessage());
                if (isNotification) {
                    return null;
                }
                return createErrorResponse("method_not_found", e.getMessage(), requestId);
            } else {
                logger.warn("Invalid request: {}", e.getMessage());
                if (isNotification) {
                    return null;
                }
                return createErrorResponse("invalid_request", e.getMessage(), requestId);
            }
        } catch (SQLException e) {
            logger.error("Database error: {}", e.getMessage(), e);
            if (isNotification) {
                return null;
            }
            return createErrorResponse("database_error", "Database error: " + e.getMessage(), requestId);
        } catch (Exception e) {
            logger.error("Unexpected error handling request", e);
            if (isNotification) {
                return null;
            }
            return createErrorResponse("internal_error", "Internal error: " + e.getMessage(), requestId);
        }
    }

    /**
     * Start the server in stdio mode
     */
    public void startStdioMode() throws IOException {
        logger.info("Starting Database MCP Server in stdio mode...");

        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
             PrintWriter printWriter = new PrintWriter(System.out, true)) {

            String currLine;
            while ((currLine = bufferedReader.readLine()) != null) {
                try {
                    JsonNode requestNode = objectMapper.readTree(currLine);
                    JsonNode responseNode = handleRequest(requestNode);

                    // Only send a response if it's not a notification
                    if (responseNode != null) {
                        printWriter.println(objectMapper.writeValueAsString(responseNode));
                    }
                } catch (Exception e) {
                    logger.error("Error processing request: {}", currLine, e);

                    // Try to determine if this was a notification
                    boolean isNotification = false;
                    try {
                        JsonNode requestNode = objectMapper.readTree(currLine);
                        isNotification = !requestNode.has("id");
                    } catch (Exception parseException) {
                        // If we can't parse the request, assume it's not a notification
                        // and send an error response
                    }

                    if (!isNotification) {
                        JsonNode errorResponse = createErrorResponse("internal_error",
                            "Internal server error: " + e.getMessage(), null);
                        printWriter.println(objectMapper.writeValueAsString(errorResponse));
                    }
                }
            }
        }

        logger.info("Database MCP Server stopped.");
    }

    private JsonNode handleInitialize() {
        ObjectNode resultNode = objectMapper.createObjectNode();
        resultNode.put("protocolVersion", "2024-11-05");
        resultNode.set("capabilities", createCapabilities());
        resultNode.set("serverInfo", objectMapper.valueToTree(serverInfo));
        return resultNode;
    }

    private JsonNode handleListTools() {
        ArrayNode toolsNode = objectMapper.createArrayNode();
        
        // Query tool
        ObjectNode queryTool = objectMapper.createObjectNode();
        queryTool.put("name", "query");
        queryTool.put("description", "Execute SQL queries on the database. Do not use SQL comments. They are blocked");
        
        ObjectNode querySchema = objectMapper.createObjectNode();
        querySchema.put("type", "object");
        querySchema.put("additionalProperties", false);

        ObjectNode queryProperties = objectMapper.createObjectNode();
        
        ObjectNode sqlProperty = objectMapper.createObjectNode();
        sqlProperty.put("type", "string");
        sqlProperty.put("description", "The SQL query to execute. IMPORTANT: Do not include any comments (-- or /* */) in SQL queries as they are blocked for security reasons. Use only plain SQL statements without explanatory comments.");

        ObjectNode maxRowsProperty = objectMapper.createObjectNode();
        maxRowsProperty.put("type", "integer");
        maxRowsProperty.put("description", "Maximum number of rows to return (default: 1000)");
        maxRowsProperty.put("minimum", 1);
        maxRowsProperty.put("maximum", databaseService.getDatabaseConfig().maxRowsLimit());
        maxRowsProperty.put("default", 1000);
        queryProperties.set("maxRows", maxRowsProperty);
        
        querySchema.set("properties", queryProperties);
        ArrayNode requiredNode = objectMapper.createArrayNode();
        requiredNode.add("sql");
        querySchema.set("required", requiredNode);
        
        queryTool.set("inputSchema", querySchema);
        toolsNode.add(queryTool);
        
        ObjectNode resultNode = objectMapper.createObjectNode();
        resultNode.set("tools", toolsNode);
        return resultNode;
    }
    
    JsonNode handleCallTool(JsonNode paramsNode) throws SQLException {
        String toolName = paramsNode.path("name").asText();
        JsonNode arguments = paramsNode.path("arguments");

        return switch (toolName) {
            case "query" -> executeQuery(arguments);
            default -> throw new IllegalArgumentException("Unknown tool: " + toolName);
        };
    }
    
    JsonNode executeQuery(JsonNode argsNode) throws SQLException {
        JsonNode sqlNode = argsNode.path("sql");
        if (sqlNode.isNull() || sqlNode.isMissingNode()) {
            throw new IllegalArgumentException("SQL query cannot be null");
        }
        String sqlText = sqlNode.asText();
        int maxRows = argsNode.path("maxRows").asInt(1000);
        
        if (sqlText == null || sqlText.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL query cannot be empty");
        }

        // length check to prevent extremely long queries
        int maxSqlLen = databaseService.getDatabaseConfig().maxSqlLength();
        if (sqlText.length() > maxSqlLen) {
            throw new IllegalArgumentException("SQL query too long (max " + maxSqlLen + " characters)");
        }

        logger.info("Executing query: {}", sqlNode);
        
        QueryResult resultNode = databaseService.executeQuery(sqlText, maxRows);
        
        ObjectNode responseNode = objectMapper.createObjectNode();
        ArrayNode contentNode = objectMapper.createArrayNode();
        
        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        
        StringBuilder resultText = new StringBuilder();
        resultText.append("Query executed successfully.\n");
        resultText.append("Rows returned: ").append(resultNode.rowCount()).append("\n");
        resultText.append("Execution time: ").append(resultNode.executionTimeMs()).append("ms\n\n");
        
        if (resultNode.rowCount() > 0) {
            resultText.append("Results:\n");
            resultText.append(formatResultsAsTable(resultNode));
        } else {
            resultText.append("No rows returned.");
        }
        
        textContent.put("text", resultText.toString());
        contentNode.add(textContent);
        
        responseNode.set("content", contentNode);
        responseNode.put("isError", false);
        
        return responseNode;
    }

    private JsonNode handleListResources() throws SQLException {
        List<DatabaseResource> resourceList = databaseService.listResources();
        
        ArrayNode resourceArray = objectMapper.createArrayNode();
        for (DatabaseResource databaseResource : resourceList) {
            ObjectNode resourceNode = objectMapper.createObjectNode();
            resourceNode.put("uri", databaseResource.uri());
            resourceNode.put("name", databaseResource.name());
            resourceNode.put("description", databaseResource.description());
            resourceNode.put("mimeType", databaseResource.mimeType());
            resourceArray.add(resourceNode);
        }
        
        ObjectNode resultNode = objectMapper.createObjectNode();
        resultNode.set("resources", resourceArray);
        return resultNode;
    }
    
    JsonNode handleReadResource(JsonNode paramsNode) throws SQLException {
        String uri = paramsNode.path("uri").asText();

        DatabaseResource databaseResource = databaseService.readResource(uri);
        if (databaseResource == null) {
            throw new IllegalArgumentException("Resource not found: " + uri);
        }

        ObjectNode contentNode = objectMapper.createObjectNode();
        contentNode.put("uri", databaseResource.uri());
        contentNode.put("mimeType", databaseResource.mimeType());
        contentNode.put("text", databaseResource.content());

        ArrayNode contentsArray = objectMapper.createArrayNode();
        contentsArray.add(contentNode);

        ObjectNode resultNode = objectMapper.createObjectNode();
        resultNode.set("contents", contentsArray);
        return resultNode;
    }
    
    private ObjectNode createCapabilities() {
        ObjectNode capabilitiesNode = objectMapper.createObjectNode();
        
        ObjectNode toolsNode = objectMapper.createObjectNode();
        toolsNode.put("listChanged", false);
        capabilitiesNode.set("tools", toolsNode);
        
        ObjectNode resourcesNode = objectMapper.createObjectNode();
        resourcesNode.put("subscribe", false);
        resourcesNode.put("listChanged", false);
        capabilitiesNode.set("resources", resourcesNode);
        
        return capabilitiesNode;
    }
    
    private Map<String, Object> createServerInfo() {
        Map<String, Object> infoMap = new HashMap<>();
        infoMap.put("name", "DBMCP - Database MCP Server");
        infoMap.put("version", "1.0.0");
        infoMap.put("description", "Generic MCP server for database operations");
        return infoMap;
    }
    
    private JsonNode createSuccessResponse(JsonNode resultNode, Object requestId) {
        ObjectNode responseNode = objectMapper.createObjectNode();
        responseNode.put("jsonrpc", "2.0");
        responseNode.set("result", resultNode);
        setRespId(requestId, responseNode);
        return responseNode;
    }

    private static void setRespId(Object requestId, ObjectNode responseNode) {
        // Always set the ID field to exactly match what was in the request
        if (requestId == null) {
            responseNode.putNull("id");
        } else if (requestId instanceof String) {
            responseNode.put("id", (String) requestId);
        } else if (requestId instanceof Number) {
            responseNode.put("id", ((Number) requestId).intValue());
        } else {
            // For other types, convert to tree
            JsonNode idNode = objectMapper.valueToTree(requestId);
            responseNode.set("id", idNode);
        }
    }

    JsonNode createErrorResponse(String code, String message, Object requestId) {
        ObjectNode responseNode = objectMapper.createObjectNode();
        responseNode.put("jsonrpc", "2.0");
        
        ObjectNode errorNode = objectMapper.createObjectNode();
        errorNode.put("code", getErrorCode(code));
        errorNode.put("message", message);
        responseNode.set("error", errorNode);

        setRespId(requestId, responseNode);

        return responseNode;
    }

    private int getErrorCode(String codeString) {
        return switch (codeString) {
            case "invalid_request" -> -32600;
            case "method_not_found" -> -32601;
            case "invalid_params" -> -32602;
            case "internal_error" -> -32603;
            case "database_error" -> -32000;
            default -> -32603;
        };
    }

    String formatResultsAsTable(QueryResult queryResult) {
        if (queryResult.isEmpty()) {
            return "No data";
        }
        
        List<String> allColumns = queryResult.allColumns();
        List<List<Object>> allRows = queryResult.allRows();
        
        // Calculate column widths
        int[] columnWidths = new int[allColumns.size()];
        for (int i = 0; i < allColumns.size(); i++) {
            columnWidths[i] = allColumns.get(i).length();
        }
        
        for (List<Object> currRow : allRows) {
            for (int i = 0; i < currRow.size() && i < columnWidths.length; i++) {
                Object columnValue = currRow.get(i);
                String columnStr = columnValue != null ? columnValue.toString() : "NULL";
                columnWidths[i] = Math.max(columnWidths[i], columnStr.length());
            }
        }
        
        StringBuilder resultBuilder = new StringBuilder();

        // Header
        for (int i = 0; i < allColumns.size(); i++) {
            if (i > 0) resultBuilder.append(" | ");
            resultBuilder.append(String.format("%-" + columnWidths[i] + "s", allColumns.get(i)));
        }
        resultBuilder.append("\n");

        // Separator
        for (int i = 0; i < allColumns.size(); i++) {
            if (i > 0) resultBuilder.append("-+-");
            resultBuilder.append("-".repeat(columnWidths[i]));
        }
        resultBuilder.append("\n");
        
        // Data rows
        for (List<Object> currRow : allRows) {
            for (int i = 0; i < allColumns.size(); i++) {
                if (i > 0) resultBuilder.append(" | ");
                Object columnValue = i < currRow.size() ? currRow.get(i) : null;
                String columnStr = columnValue != null ? columnValue.toString() : "NULL";
                resultBuilder.append(String.format("%-" + columnWidths[i] + "s", columnStr));
            }
            resultBuilder.append("\n");
        }
        
        return resultBuilder.toString();
    }

    /*
    Order of precedence:
    CLI argument: --db_url=...
    Environment variable: DB_URL
    System property: -Ddb.url=...
    Default: internal hard-coded values
    Keys are case-insensitive for args but uppercase for environment variables (as per convention).
    */
    static ConfigParams loadConfiguration(String[] args) {
        Map<String, String> cliArgs = parseArgs(args);
        // Load from environment variables  system properties
        String dbUrl = getConfigValue("DB_URL", "jdbc:h2:mem:testdb", cliArgs);
        String dbUser = getConfigValue("DB_USER", "sa", cliArgs);
        String dbPassword = getConfigValue("DB_PASSWORD", "", cliArgs);
        String dbDriver = getConfigValue("DB_DRIVER", "org.h2.Driver", cliArgs);
        String maxConnections = getConfigValue("MAX_CONNECTIONS", "10", cliArgs);
        String connectionTimeoutMs = getConfigValue("CONNECTION_TIMEOUT_MS", "30000", cliArgs);
        String queryTimeoutSeconds = getConfigValue("QUERY_TIMEOUT_SECONDS", "30", cliArgs);
        String selectOnly = getConfigValue("SELECT_ONLY", "true", cliArgs);
        String maxSql = getConfigValue("MAX_SQL", "10000", cliArgs);
        String maxRowsLimit = getConfigValue("MAX_ROWS_LIMIT", "10000", cliArgs);
        String idleTimeoutMs = getConfigValue("IDLE_TIMEOUT_MS", "600000", cliArgs);
        String maxLifetimeMs = getConfigValue("MAX_LIFETIME_MS", "1800000", cliArgs);
        String leakDetectionThresholdMs = getConfigValue("LEAK_DETECTION_THRESHOLD_MS", "60000", cliArgs);

        return new ConfigParams(dbUrl, dbUser, dbPassword, dbDriver,
                Integer.parseInt(maxConnections),
                Integer.parseInt(connectionTimeoutMs),
                Integer.parseInt(queryTimeoutSeconds),
                Boolean.parseBoolean(selectOnly),
                Integer.parseInt(maxSql),
                Integer.parseInt(maxRowsLimit),
                Integer.parseInt(idleTimeoutMs),
                Integer.parseInt(maxLifetimeMs),
                Integer.parseInt(leakDetectionThresholdMs));
    }

    static boolean isHttpMode(String[] args) {
        Map<String, String> cliArgs = parseArgs(args);
        String httpMode = getConfigValue("HTTP_MODE", "false", cliArgs);
        return Boolean.parseBoolean(httpMode);
    }

    static int getHttpPort(String[] args) {
        Map<String, String> cliArgs = parseArgs(args);
        String httpPort = getConfigValue("HTTP_PORT", "8080", cliArgs);
        return Integer.parseInt(httpPort);
    }

    static Map<String, String> parseArgs(String[] args) {
        Map<String, String> argsMap = new HashMap<>();
        for (String currArg : args) {
            if (currArg.startsWith("--")) {
                String[] argParts = currArg.substring(2).split("=", 2);
                if (argParts.length == 2) {
                    argsMap.put(argParts[0].toUpperCase(), argParts[1]);
                }
            }
        }
        return argsMap;
    }

    private static String getConfigValue(String envVar, String defaultValue, Map<String, String> cliArgs) {
        // 1. CLI arguments (exact match on envVar uppercased)
        String cliValue = cliArgs.get(envVar.toUpperCase());
        if (cliValue != null) {
            return cliValue;
        }

        // 2. Environment variable
        String paramValue = System.getenv(envVar);
        if (paramValue != null) {
            return paramValue;
        }

        // 3. System property (envVar.lower().replace('_', '.'))
        paramValue = System.getProperty(envVar.toLowerCase().replace('_', '.'));
        if (paramValue != null) {
            return paramValue;
        }

        // 4. Default
        return defaultValue;
    }

    public static void main(String[] args) throws IOException {
        // Load configuration from environment or args
        ConfigParams configParams = loadConfiguration(args);
        McpServer mcpServer = new McpServer(configParams);

        // Add shutdown hook for clean resource cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down Database MCP Server...");
            mcpServer.databaseService.close();
        }));

        // Check configuration for HTTP mode
        try {
            if (isHttpMode(args)) {
                int port = getHttpPort(args);
                mcpServer.startHttpMode(port);
            } else {
                mcpServer.startStdioMode();
            }
        } catch (IOException e) {
            logger.error("Failed to start server: {}", e.getMessage());

            // Print helpful error message and exit
            if (e.getMessage().contains("already in use")) {
                System.err.println("\nERROR: Cannot start server");
                System.err.println("The specified port is already in use by another application.");
                System.err.println("\nSolutions:");
                System.err.println("1. Stop the application using the port");
                System.err.println("2. Use a different port: --http_port=9090");
                System.err.println("3. Find what's using the port: netstat -ano | findstr :<port>");
            } else {
                System.err.println("\nERROR: Cannot start server");
                System.err.println("Reason: " + e.getMessage());
            }

            // Ensure database resources are cleaned up
            mcpServer.databaseService.close();
            System.exit(1);
        }
    }
}