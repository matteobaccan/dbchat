package com.skanga.mcp.prompts;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromptServiceTest {
    private PromptService promptService;

    @BeforeEach
    void setUp() {
        promptService = new PromptService("postgresql");
    }

    @Test
    @DisplayName("Should list all available prompts")
    void testListPrompts() {
        JsonNode result = promptService.listPrompts();

        assertThat(result).isNotNull();
        assertThat(result.has("prompts")).isTrue();
        assertThat(result.get("prompts").isArray()).isTrue();
        assertThat(result.get("prompts").size()).isEqualTo(3);

        // Check prompt names
        JsonNode prompts = result.get("prompts");
        String[] expectedPrompts = {"mcp-demo", "business-intelligence", "database-analysis"};
        
        for (int i = 0; i < prompts.size(); i++) {
            JsonNode prompt = prompts.get(i);
            assertThat(prompt.has("name")).isTrue();
            assertThat(prompt.has("description")).isTrue();
            assertThat(prompt.has("arguments")).isTrue();
            
            String promptName = prompt.get("name").asText();
            assertThat(expectedPrompts).contains(promptName);
        }
    }

    @Test
    @DisplayName("Should get mcp-demo prompt with retail scenario")
    void testGetMcpDemoPromptRetail() {
        Map<String, String> arguments = new HashMap<>();
        arguments.put("topic", "retail");

        JsonNode result = promptService.getPrompt("mcp-demo", arguments);

        assertThat(result).isNotNull();
        assertThat(result.has("name")).isTrue();
        assertThat(result.get("name").asText()).isEqualTo("mcp-demo");
        assertThat(result.has("description")).isTrue();
        assertThat(result.has("messages")).isTrue();
        assertThat(result.get("messages").isArray()).isTrue();
        assertThat(result.get("messages").size()).isGreaterThan(0);

        // Check message content contains business scenario elements
        JsonNode message = result.get("messages").get(0);
        assertThat(message.has("role")).isTrue();
        assertThat(message.get("role").asText()).isEqualTo("user");
        assertThat(message.has("content")).isTrue();
        
        JsonNode content = message.get("content");
        assertThat(content.has("type")).isTrue();
        assertThat(content.get("type").asText()).isEqualTo("text");
        assertThat(content.has("text")).isTrue();
        
        String promptText = content.get("text").asText();
        assertThat(promptText).contains("INTERACTIVE DATABASE ANALYSIS DEMO");
        assertThat(promptText).contains("TechnoMart");
        assertThat(promptText).contains("customers");
        assertThat(promptText).contains("products");
        assertThat(promptText).contains("orders");
    }

    @Test
    @DisplayName("Should get mcp-demo prompt with finance scenario")
    void testGetMcpDemoPromptFinance() {
        Map<String, String> arguments = new HashMap<>();
        arguments.put("topic", "finance");

        JsonNode result = promptService.getPrompt("mcp-demo", arguments);

        assertThat(result).isNotNull();
        JsonNode message = result.get("messages").get(0);
        String promptText = message.get("content").get("text").asText();
        
        assertThat(promptText).contains("SecureBank");
        assertThat(promptText).contains("accounts");
        assertThat(promptText).contains("transactions");
        assertThat(promptText).contains("Risk Manager");
    }

    @Test
    @DisplayName("Should get mcp-demo prompt with default scenario when topic is null")
    void testGetMcpDemoPromptDefaultScenario() {
        Map<String, String> arguments = new HashMap<>();
        // No topic specified

        JsonNode result = promptService.getPrompt("mcp-demo", arguments);

        assertThat(result).isNotNull();
        JsonNode message = result.get("messages").get(0);
        String promptText = message.get("content").get("text").asText();
        
        // Should default to retail scenario
        assertThat(promptText).contains("TechnoMart");
    }

    @Test
    @DisplayName("Should get business-intelligence prompt")
    void testGetBusinessIntelligencePrompt() {
        Map<String, String> arguments = new HashMap<>();
        arguments.put("focus_area", "sales");

        JsonNode result = promptService.getPrompt("business-intelligence", arguments);

        assertThat(result).isNotNull();
        assertThat(result.has("name")).isTrue();
        assertThat(result.get("name").asText()).isEqualTo("business-intelligence");
        
        JsonNode message = result.get("messages").get(0);
        String promptText = message.get("content").get("text").asText();
        
        assertThat(promptText).contains("BUSINESS INTELLIGENCE ANALYSIS");
        assertThat(promptText).contains("Focus Area: sales");
        assertThat(promptText).contains("POSTGRESQL");
        assertThat(promptText).contains("append_insight");
    }

    @Test
    @DisplayName("Should get database-analysis prompt")
    void testGetDatabaseAnalysisPrompt() {
        Map<String, String> arguments = new HashMap<>();
        arguments.put("focus_area", "customers");

        JsonNode result = promptService.getPrompt("database-analysis", arguments);

        assertThat(result).isNotNull();
        assertThat(result.has("name")).isTrue();
        assertThat(result.get("name").asText()).isEqualTo("database-analysis");
        
        JsonNode message = result.get("messages").get(0);
        String promptText = message.get("content").get("text").asText();
        
        assertThat(promptText).contains("DATABASE ANALYSIS WORKFLOW");
        assertThat(promptText).contains("Focus Area: customers");
        assertThat(promptText).contains("POSTGRESQL");
        assertThat(promptText).contains("Phase 1: Discovery");
        assertThat(promptText).contains("Phase 2: Data Exploration");
        assertThat(promptText).contains("Phase 3: Insight Generation");
        assertThat(promptText).contains("Phase 4: Comprehensive Understanding");
    }

    @Test
    @DisplayName("Should throw exception for unknown prompt")
    void testGetUnknownPrompt() {
        Map<String, String> arguments = new HashMap<>();
        
        assertThatThrownBy(() -> promptService.getPrompt("unknown-prompt", arguments))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown prompt: unknown-prompt");
    }

    @Test
    @DisplayName("Should throw exception for database-analysis without focus_area")
    void testGetDatabaseAnalysisWithoutFocusArea() {
        Map<String, String> arguments = new HashMap<>();
        // Missing focus_area
        
        assertThatThrownBy(() -> promptService.getPrompt("database-analysis", arguments))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required argument: focus_area");
    }

    @Test
    @DisplayName("Should throw exception for business-intelligence without focus_area")
    void testGetBusinessIntelligenceWithoutFocusArea() {
        Map<String, String> arguments = new HashMap<>();
        // Missing focus_area
        
        assertThatThrownBy(() -> promptService.getPrompt("business-intelligence", arguments))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Missing required argument: focus_area");
    }

    @Test
    @DisplayName("Should get database type")
    void testGetDatabaseType() {
        assertThat(promptService.databaseType()).isEqualTo("postgresql");
    }

    @Test
    @DisplayName("Should work with different database types")
    void testDifferentDatabaseTypes() {
        PromptService mysqlService = new PromptService("mysql");
        Map<String, String> arguments = new HashMap<>();
        arguments.put("focus_area", "inventory");

        JsonNode result = mysqlService.getPrompt("database-analysis", arguments);
        String promptText = result.get("messages").get(0).get("content").get("text").asText();
        
        assertThat(promptText).contains("MYSQL");
        assertThat(mysqlService.databaseType()).isEqualTo("mysql");
    }

    @Test
    @DisplayName("MCP Demo prompt should have metadata")
    void testMcpDemoPromptMetadata() {
        JsonNode prompts = promptService.listPrompts();
        JsonNode mcpDemoPrompt = null;
        
        for (JsonNode prompt : prompts.get("prompts")) {
            if ("mcp-demo".equals(prompt.get("name").asText())) {
                mcpDemoPrompt = prompt;
                break;
            }
        }
        
        assertThat(mcpDemoPrompt).isNotNull();
        assertThat(mcpDemoPrompt.has("metadata")).isTrue();
        
        JsonNode metadata = mcpDemoPrompt.get("metadata");
        assertThat(metadata.get("type").asText()).isEqualTo("interactive-demo");
        assertThat(metadata.get("features").asText()).contains("business-narrative");
        assertThat(metadata.get("features").asText()).contains("multiple-choice");
        assertThat(metadata.get("features").asText()).contains("guided-workflow");
        assertThat(metadata.get("duration").asText()).isEqualTo("15-20 minutes");
        assertThat(metadata.get("difficulty").asText()).isEqualTo("beginner-friendly");
    }

    @Test
    @DisplayName("Should handle empty arguments map")
    void testEmptyArguments() {
        Map<String, String> emptyArgs = new HashMap<>();
        
        // mcp-demo should work with empty arguments (defaults to retail)
        JsonNode result = promptService.getPrompt("mcp-demo", emptyArgs);
        assertThat(result).isNotNull();
        
        // database-analysis should fail with empty arguments
        assertThatThrownBy(() -> promptService.getPrompt("database-analysis", emptyArgs))
                .isInstanceOf(IllegalArgumentException.class);
    }
}