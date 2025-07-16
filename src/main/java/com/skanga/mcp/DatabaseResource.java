package com.skanga.mcp;

/**
 * Represents a database resource (table, view, schema, etc.)
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