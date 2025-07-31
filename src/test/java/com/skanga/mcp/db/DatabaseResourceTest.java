package com.skanga.mcp.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatabaseResourceTest {
    @Test
    @DisplayName("Should create database resource with all properties")
    void shouldCreateDatabaseResourceWithAllProperties() {
        // Given
        String uri = "database://table/users";
        String name = "users";
        String description = "User accounts table";
        String mimeType = "text/plain";
        String content = "Table schema and metadata";

        // When
        DatabaseResource resource = new DatabaseResource(uri, name, description, mimeType, content);

        // Then
        assertThat(resource.uri()).isEqualTo(uri);
        assertThat(resource.name()).isEqualTo(name);
        assertThat(resource.description()).isEqualTo(description);
        assertThat(resource.mimeType()).isEqualTo(mimeType);
        assertThat(resource.content()).isEqualTo(content);
    }

    @Test
    @DisplayName("Should handle null content")
    void shouldHandleNullContent() {
        // Given
        String uri = "database://table/products";
        String name = "products";
        String description = "Products table";
        String mimeType = "text/plain";
        String content = null;

        // When
        DatabaseResource resource = new DatabaseResource(uri, name, description, mimeType, content);

        // Then
        assertThat(resource.uri()).isEqualTo(uri);
        assertThat(resource.name()).isEqualTo(name);
        assertThat(resource.description()).isEqualTo(description);
        assertThat(resource.mimeType()).isEqualTo(mimeType);
        assertThat(resource.content()).isNull();
    }

    @Test
    @DisplayName("Should reject empty strings for required fields")
    void shouldRejectEmptyStrings() {
        // When/Then - URI cannot be empty
        assertThatThrownBy(() -> new DatabaseResource("", "name", "desc", "text/plain", "content"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("URI cannot be null or empty");

        // When/Then - Name cannot be empty
        assertThatThrownBy(() -> new DatabaseResource("uri://test", "", "desc", "text/plain", "content"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Name cannot be null or empty");
    }

    @Test
    @DisplayName("Should allow empty optional fields")
    void shouldAllowEmptyOptionalFields() {
        // Given - Only URI and name are required
        DatabaseResource resource = new DatabaseResource("uri://test", "TestName", "", "", "");

        // Then
        assertThat(resource.uri()).isEqualTo("uri://test");
        assertThat(resource.name()).isEqualTo("TestName");
        assertThat(resource.description()).isEmpty();
        assertThat(resource.mimeType()).isEmpty();
        assertThat(resource.content()).isEmpty();
    }

    @Test
    @DisplayName("Should create database info resource")
    void shouldCreateDatabaseInfoResource() {
        // Given
        String uri = "database://info";
        String name = "Database Information";
        String description = "General database metadata and connection information";
        String mimeType = "text/plain";
        String content = "Database: MySQL 8.0\nDriver: mysql-connector-java";

        // When
        DatabaseResource resource = new DatabaseResource(uri, name, description, mimeType, content);

        // Then
        assertThat(resource.uri()).isEqualTo(uri);
        assertThat(resource.name()).isEqualTo(name);
        assertThat(resource.description()).contains("metadata");
        assertThat(resource.content()).contains("MySQL");
    }

    @Test
    @DisplayName("Should create table resource")
    void shouldCreateTableResource() {
        // Given
        String uri = "database://table/orders";
        String name = "orders";
        String description = "TABLE: Customer orders";
        String mimeType = "text/plain";
        String content = "Columns:\n  - id (INT) NOT NULL\n  - customer_id (INT)\n  - order_date (DATE)";

        // When
        DatabaseResource resource = new DatabaseResource(uri, name, description, mimeType, content);

        // Then
        assertThat(resource.uri()).startsWith("database://table/");
        assertThat(resource.name()).isEqualTo("orders");
        assertThat(resource.description()).contains("TABLE");
        assertThat(resource.content()).contains("Columns:");
    }

    @Test
    @DisplayName("Should create view resource")
    void shouldCreateViewResource() {
        // Given
        String uri = "database://table/active_users";
        String name = "active_users";
        String description = "VIEW: Currently active users";
        String mimeType = "text/plain";
        String content = "View definition and columns";

        // When
        DatabaseResource resource = new DatabaseResource(uri, name, description, mimeType, content);

        // Then
        assertThat(resource.name()).isEqualTo("active_users");
        assertThat(resource.description()).contains("VIEW");
    }

    @Test
    @DisplayName("Should create schema resource")
    void shouldCreateSchemaResource() {
        // Given
        String uri = "database://schema/public";
        String name = "public";
        String description = "Database schema: public";
        String mimeType = "text/plain";
        String content = "Tables in this schema:\n  - users\n  - orders\n  - products";

        // When
        DatabaseResource resource = new DatabaseResource(uri, name, description, mimeType, content);

        // Then
        assertThat(resource.uri()).startsWith("database://schema/");
        assertThat(resource.name()).isEqualTo("public");
        assertThat(resource.description()).contains("schema");
        assertThat(resource.content()).contains("Tables in this schema");
    }

    @Test
    @DisplayName("Should have meaningful toString representation")
    void shouldHaveMeaningfulToString() {
        // Given
        DatabaseResource resource = new DatabaseResource(
            "database://table/users",
            "users",
            "User accounts table",
            "text/plain",
            "Table content"
        );

        // When
        String toString = resource.toString();

        // Then
        assertThat(toString)
            .contains("DatabaseResource")
            .contains("uri=database://table/users")
            .contains("name=users")
            .contains("description=User accounts table");
    }

    @Test
    @DisplayName("Should handle special characters in content")
    void shouldHandleSpecialCharactersInContent() {
        // Given
        String content = "Special characters: åäö üéñ 中文 🚀\nTabs:\t\tSpaces:    \nNewlines:\n\n";
        DatabaseResource resource = new DatabaseResource(
            "database://table/test",
            "test",
            "Test table",
            "text/plain",
            content
        );

        // When/Then
        assertThat(resource.content()).isEqualTo(content);
        assertThat(resource.content()).contains("åäö");
        assertThat(resource.content()).contains("中文");
        assertThat(resource.content()).contains("🚀");
    }

    @Test
    @DisplayName("Should handle large content")
    void shouldHandleLargeContent() {
        // Given
        StringBuilder largeContent = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            largeContent.append("Line ").append(i).append(": This is a test line with some content.\n");
        }
        
        DatabaseResource resource = new DatabaseResource(
            "database://table/large_table",
            "large_table",
            "Large table with many columns",
            "text/plain",
            largeContent.toString()
        );

        // When/Then
        assertThat(resource.content()).hasSize(largeContent.length());
        assertThat(resource.content()).startsWith("Line 0:");
        assertThat(resource.content()).contains("Line 9999:");
    }

    @Test
    @DisplayName("Should handle different MIME types")
    void shouldHandleDifferentMimeTypes() {
        // Given/When
        DatabaseResource textResource = new DatabaseResource("uri1", "name1", "desc1", "text/plain", "content1");
        DatabaseResource jsonResource = new DatabaseResource("uri2", "name2", "desc2", "application/json", "{}");
        DatabaseResource xmlResource = new DatabaseResource("uri3", "name3", "desc3", "application/xml", "<xml/>");

        // Then
        assertThat(textResource.mimeType()).isEqualTo("text/plain");
        assertThat(jsonResource.mimeType()).isEqualTo("application/json");
        assertThat(xmlResource.mimeType()).isEqualTo("application/xml");
    }
}
