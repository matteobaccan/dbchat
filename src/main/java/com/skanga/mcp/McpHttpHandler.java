package com.skanga.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;

/**
 * HTTP handler for MCP requests
 */
class McpHttpHandler implements HttpHandler {
    private final McpServer mcpServer;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LoggerFactory.getLogger(McpServer.class);

    public McpHttpHandler(McpServer mcpServer) {
        this.mcpServer = mcpServer;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        setCorsHeaders(httpExchange);

        if (handleOptionsRequest(httpExchange)) {
            return;
        }

        if (!"POST".equals(httpExchange.getRequestMethod())) {
            sendHttpError(httpExchange, 405, "Method not allowed. Use POST.");
            return;
        }

        try {
            // Read request body
            String requestBody = readRequestBody(httpExchange);
            logger.debug("Received HTTP request: {}", requestBody);

            // Parse and handle the MCP request
            JsonNode requestNode = objectMapper.readTree(requestBody);
            JsonNode responseNode = mcpServer.handleRequest(requestNode);

            // Send response (but only if not a notification)
            sendHttpResponse(httpExchange, responseNode);
        } catch (Exception e) {
            logger.error("Error handling HTTP request", e);
            sendHttpError(httpExchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    // Set CORS headers (useful for testing with browser clients)
    private void setCorsHeaders(HttpExchange httpExchange) {
        httpExchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        httpExchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
        httpExchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    // Handle preflight OPTIONS request
    private boolean handleOptionsRequest(HttpExchange httpExchange) throws IOException {
        if ("OPTIONS".equals(httpExchange.getRequestMethod())) {
            httpExchange.sendResponseHeaders(200, 0);
            httpExchange.getResponseBody().close();
            return true;
        }
        return false;
    }

    // Send response (but only if not a notification)
    private void sendHttpResponse(HttpExchange httpExchange, JsonNode responseNode) throws IOException {
        if (responseNode != null) {
            String responseJson = objectMapper.writeValueAsString(responseNode);
            logger.debug("Sending HTTP response: {}", responseJson);

            httpExchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] responseBytes = responseJson.getBytes();
            httpExchange.sendResponseHeaders(200, responseBytes.length);

            try (OutputStream outputStream = httpExchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } else {
            // Notification - send empty 204 response
            httpExchange.sendResponseHeaders(204, 0);
            httpExchange.getResponseBody().close();
        }
    }

    private void sendHttpError(HttpExchange httpExchange, int statusCode, String errorMessage) throws IOException {
        ObjectNode errorResponse = createHttpErrorResponse(errorMessage);
        String responseJson = objectMapper.writeValueAsString(errorResponse);

        httpExchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] responseBytes = responseJson.getBytes();

        try {
            httpExchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream outputStream = httpExchange.getResponseBody()) {
                outputStream.write(responseBytes);
            }
        } catch (Exception e) {
            logger.error("Error sending HTTP error response", e);
        }
    }

    private ObjectNode createHttpErrorResponse(String errorMessage) {
        ObjectNode errorResponse = objectMapper.createObjectNode();
        errorResponse.put("jsonrpc", "2.0");
        errorResponse.putNull("id");

        ObjectNode errorNode = objectMapper.createObjectNode();
        errorNode.put("code", -32603); // Internal error
        errorNode.put("message", errorMessage);
        errorResponse.set("error", errorNode);

        return errorResponse;
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody()))) {
            StringBuilder fullBody = new StringBuilder();
            String bodyLine;
            while ((bodyLine = bufferedReader.readLine()) != null) {
                fullBody.append(bodyLine);
            }
            return fullBody.toString();
        }
    }
}
