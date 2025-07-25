package com.skanga.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;

/**
 * Health check handler
 */
class HealthCheckHandler implements HttpHandler {
    private final McpServer mcpServer;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public HealthCheckHandler(McpServer mcpServer) {
        this.mcpServer = mcpServer;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        ObjectNode healthResponse = objectMapper.createObjectNode();
        healthResponse.put("status", "healthy");
        healthResponse.put("server", "Database MCP Server");
        healthResponse.put("timestamp", System.currentTimeMillis());
        healthResponse.put("state", mcpServer.getServerState());

        // Test database connection
        try (Connection ignored = mcpServer.databaseService.getConnection()) {
            // Connection test successful
            healthResponse.put("database", "connected");
        } catch (Exception e) {
            healthResponse.put("database", "error: " + e.getMessage());
        }

        String responseJson = objectMapper.writeValueAsString(healthResponse);
        httpExchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = responseJson.getBytes();

        httpExchange.sendResponseHeaders(200, responseBytes.length);
        try (OutputStream outputStream = httpExchange.getResponseBody()) {
            outputStream.write(responseBytes);
        }
    }
}
