package com.skanga.mcp;

/**
 * Represents a database resource (table, view, schema, etc.) that can be accessed through the MCP server.
 * This record encapsulates metadata about database objects and their content for client consumption.
 *
 * @param uri Unique resource identifier in the format "database://type/name" (e.g., "database://table/users")
 * @param name Human-readable name of the resource
 * @param description Detailed description of what this resource contains
 * @param mimeType MIME type of the resource content (typically "text/plain")
 * @param content The actual content of the resource (may-be null for lazy-loaded resources)
 */
public record DatabaseResource(
        String uri,
        String name,
        String description,
        String mimeType,
        String content
) {
    // Custom validation in compact constructor
    public DatabaseResource {
        if (uri == null || uri.trim().isEmpty()) {
            throw new IllegalArgumentException("URI cannot be null or empty");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
    }
}
