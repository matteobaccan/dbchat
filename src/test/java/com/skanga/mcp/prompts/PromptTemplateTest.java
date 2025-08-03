package com.skanga.mcp.prompts;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptTemplateTest {

    @Test
    @DisplayName("Should create MCP demo prompt for retail topic")
    void testCreateMcpDemoPromptRetail() {
        JsonNode result = PromptTemplate.createMcpDemoPrompt("retail", "postgresql");

        assertThat(result).isNotNull();
        assertThat(result.get("name").asText()).isEqualTo("mcp-demo");
        assertThat(result.has("description")).isTrue();
        assertThat(result.has("messages")).isTrue();
        assertThat(result.has("metadata")).isTrue();

        // Check metadata
        JsonNode metadata = result.get("metadata");
        assertThat(metadata.get("scenario").asText()).isEqualTo("retail-analysis");
        assertThat(metadata.get("hasMultipleChoice").asBoolean()).isTrue();
        assertThat(metadata.get("requiresDemoData").asBoolean()).isTrue();
        assertThat(metadata.get("expectedDuration").asText()).isEqualTo("15-20 minutes");

        // Check message content
        JsonNode message = result.get("messages").get(0);
        assertThat(message.get("role").asText()).isEqualTo("user");
        
        String promptText = message.get("content").get("text").asText();
        assertThat(promptText).contains("INTERACTIVE DATABASE ANALYSIS DEMO");
        assertThat(promptText).contains("TechnoMart");
        assertThat(promptText).contains("Sarah Chen (CEO)");
        assertThat(promptText).contains("Mike Rodriguez (Sales Director)");
        assertThat(promptText).contains("Jennifer Kim (Marketing Manager)");
        assertThat(promptText).contains("Board meeting on Friday");
        assertThat(promptText).contains("customers");
        assertThat(promptText).contains("products");
        assertThat(promptText).contains("orders");
    }

    @Test
    @DisplayName("Should create MCP demo prompt for finance topic")
    void testCreateMcpDemoPromptFinance() {
        JsonNode result = PromptTemplate.createMcpDemoPrompt("finance", "mysql");

        assertThat(result).isNotNull();
        
        String promptText = result.get("messages").get(0).get("content").get("text").asText();
        assertThat(promptText).contains("SecureBank");
        assertThat(promptText).contains("David Park (Risk Manager)");
        assertThat(promptText).contains("Lisa Thompson (Customer Analytics)");
        assertThat(promptText).contains("Robert Chen (Branch Manager)");
        assertThat(promptText).contains("Regulatory review next Monday");
        assertThat(promptText).contains("accounts");
        assertThat(promptText).contains("transactions");
        
        // Check metadata shows finance scenario
        JsonNode metadata = result.get("metadata");
        assertThat(metadata.get("scenario").asText()).isEqualTo("finance-analysis");
    }

    @Test
    @DisplayName("Should default to retail scenario for unknown topic")
    void testCreateMcpDemoPromptUnknownTopic() {
        JsonNode result = PromptTemplate.createMcpDemoPrompt("unknown-topic", "h2");

        assertThat(result).isNotNull();
        
        String promptText = result.get("messages").get(0).get("content").get("text").asText();
        assertThat(promptText).contains("TechnoMart"); // Should default to retail
        
        JsonNode metadata = result.get("metadata");
        assertThat(metadata.get("scenario").asText()).isEqualTo("retail-analysis");
    }

    @Test
    @DisplayName("Should default to retail scenario for null topic")
    void testCreateMcpDemoPromptNullTopic() {
        JsonNode result = PromptTemplate.createMcpDemoPrompt(null, "sqlite");

        assertThat(result).isNotNull();
        
        String promptText = result.get("messages").get(0).get("content").get("text").asText();
        assertThat(promptText).contains("TechnoMart"); // Should default to retail
    }

    @Test
    @DisplayName("Should include database type in prompt")
    void testCreateMcpDemoPromptIncludesDatabaseType() {
        JsonNode result = PromptTemplate.createMcpDemoPrompt("retail", "oracle");

        String promptText = result.get("messages").get(0).get("content").get("text").asText();
        assertThat(promptText).contains("ORACLE database");
    }

    @Test
    @DisplayName("Should create business intelligence prompt")
    void testCreateBusinessIntelligencePrompt() {
        JsonNode result = PromptTemplate.createBusinessIntelligencePrompt("sales", "postgresql");

        assertThat(result).isNotNull();
        assertThat(result.get("name").asText()).isEqualTo("business-intelligence");
        assertThat(result.has("description")).isTrue();
        assertThat(result.get("description").asText()).contains("Business intelligence analysis focused on sales");

        String promptText = result.get("messages").get(0).get("content").get("text").asText();
        assertThat(promptText).contains("BUSINESS INTELLIGENCE ANALYSIS");
        assertThat(promptText).contains("Focus Area: sales");
        assertThat(promptText).contains("Database Type: POSTGRESQL");
        assertThat(promptText).contains("Data Discovery Phase");
        assertThat(promptText).contains("Exploratory Analysis");
        assertThat(promptText).contains("Insight Generation");
        assertThat(promptText).contains("Dashboard Creation");
        assertThat(promptText).contains("append_insight");
    }

    @Test
    @DisplayName("MCP demo prompt should include interactive workflow instructions")
    void testMcpDemoPromptWorkflowInstructions() {
        JsonNode result = PromptTemplate.createMcpDemoPrompt("retail", "h2");

        String promptText = result.get("messages").get(0).get("content").get("text").asText();
        
        // Check for workflow sections
        assertThat(promptText).contains("GUIDED ANALYSIS WORKFLOW");
        assertThat(promptText).contains("Your Mission");
        assertThat(promptText).contains("Available Data");
        assertThat(promptText).contains("Analysis Steps");
        assertThat(promptText).contains("HOW TO USE THIS DEMO");
        assertThat(promptText).contains("Demo Data Setup");
        assertThat(promptText).contains("Expected Final Insight");
        assertThat(promptText).contains("LET'S BEGIN!");
        
        // Check for specific instructions
        assertThat(promptText).contains("This is an interactive workflow");
        assertThat(promptText).contains("multiple choice options");
        assertThat(promptText).contains("resources/list");
        assertThat(promptText).contains("run_sql");
        assertThat(promptText).contains("append_insight");
        
        // Check for multiple choice at the end
        assertThat(promptText).contains("**A)**");
        assertThat(promptText).contains("**B)**");
        assertThat(promptText).contains("**C)**");
    }

    @Test
    @DisplayName("MCP demo prompt should include analysis steps with multiple choice")
    void testMcpDemoPromptAnalysisSteps() {
        JsonNode result = PromptTemplate.createMcpDemoPrompt("retail", "mysql");

        String promptText = result.get("messages").get(0).get("content").get("text").asText();
        
        // Check for step structure
        assertThat(promptText).contains("#### Step 1: Customer Base Overview");
        assertThat(promptText).contains("#### Step 2: Sales Performance Analysis");
        assertThat(promptText).contains("#### Step 3: Product Performance Deep Dive");
        
        // Check for multiple choice options in steps
        assertThat(promptText).contains("**Analysis Options:**");
        assertThat(promptText).contains("A) Analyze customer demographics");
        assertThat(promptText).contains("B) Look at total sales and revenue");
        assertThat(promptText).contains("C) Examine product inventory");
        
        // Check for suggested queries
        assertThat(promptText).contains("**Suggested Query:**");
        assertThat(promptText).contains("```sql");
        assertThat(promptText).contains("SELECT");
        
        // Check for expected outcomes
        assertThat(promptText).contains("**Expected Outcome:**");
    }

    @Test
    @DisplayName("Should handle ecommerce topic as retail scenario")
    void testEcommerceTopicMapsToRetail() {
        JsonNode result = PromptTemplate.createMcpDemoPrompt("ecommerce", "postgresql");

        String promptText = result.get("messages").get(0).get("content").get("text").asText();
        assertThat(promptText).contains("TechnoMart"); // Should map to retail scenario
        
        JsonNode metadata = result.get("metadata");
        assertThat(metadata.get("scenario").asText()).isEqualTo("retail-analysis");
    }

    @Test
    @DisplayName("Should handle banking topic as finance scenario")
    void testBankingTopicMapsToFinance() {
        JsonNode result = PromptTemplate.createMcpDemoPrompt("banking", "h2");

        String promptText = result.get("messages").get(0).get("content").get("text").asText();
        assertThat(promptText).contains("SecureBank"); // Should map to finance scenario
        
        JsonNode metadata = result.get("metadata");
        assertThat(metadata.get("scenario").asText()).isEqualTo("finance-analysis");
    }
}