package com.skanga.mcp.prompts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Enhanced MCP Prompts Service supporting sophisticated demo workflows.
 * Provides structured prompts for guided database analysis with business narratives,
 * multiple choice progressions, and interactive data exploration.
 */
public record PromptService(String databaseType) {
    private static final Logger logger = LoggerFactory.getLogger(PromptService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public PromptService(String databaseType) {
        this.databaseType = databaseType != null ? databaseType : "unknown";
        logger.info("PromptService initialized for database type: {}", this.databaseType);
    }

    /**
     * Lists all available prompts following MCP prompts protocol
     */
    public JsonNode listPrompts() {
        ArrayNode promptsArray = objectMapper.createArrayNode();

        // Enhanced MCP Demo prompt with sophisticated workflow
        ObjectNode mcpDemoPrompt = createMcpDemoPromptDefinition();
        promptsArray.add(mcpDemoPrompt);

        // Business Intelligence analysis prompt
        ObjectNode biPrompt = createBusinessIntelligencePromptDefinition();
        promptsArray.add(biPrompt);

        // Original database analysis prompt (enhanced)
        ObjectNode analysisPrompt = createDatabaseAnalysisPromptDefinition();
        promptsArray.add(analysisPrompt);

        ObjectNode result = objectMapper.createObjectNode();
        result.set("prompts", promptsArray);

        logger.debug("Listed {} available prompts", promptsArray.size());
        return result;
    }

    /**
     * Gets a specific prompt with arguments following MCP prompts protocol
     */
    public JsonNode getPrompt(String name, Map<String, String> arguments) {
        logger.info("Getting prompt: {} with arguments: {}", name, arguments);

        return switch (name) {
            case "mcp-demo" -> PromptTemplate.createMcpDemoPrompt(
                    arguments.get("topic"), databaseType);
            case "business-intelligence" -> PromptTemplate.createBusinessIntelligencePrompt(
                    arguments.get("focus_area"), databaseType);
            case "database-analysis" -> createAnalysisPrompt(arguments);
            default -> throw new IllegalArgumentException("Unknown prompt: " + name);
        };
    }

    /**
     * Creates the MCP Demo prompt definition with sophisticated workflow
     */
    private ObjectNode createMcpDemoPromptDefinition() {
        ObjectNode mcpDemo = objectMapper.createObjectNode();
        mcpDemo.put("name", "mcp-demo");
        mcpDemo.put("description", "Interactive database analysis demo with complete business scenarios, guided workflows, multiple choice progressions, and realistic data exploration. Perfect for onboarding and demonstrating database analysis capabilities.");

        ArrayNode arguments = objectMapper.createArrayNode();

        // Topic argument for scenario selection
        ObjectNode topicArg = objectMapper.createObjectNode();
        topicArg.put("name", "topic");
        topicArg.put("description", "Business scenario topic (e.g., 'retail', 'finance', 'ecommerce'). Determines the demo scenario with appropriate business context, sample data, and analysis workflow.");
        topicArg.put("required", false);
        arguments.add(topicArg);

        mcpDemo.set("arguments", arguments);

        // Add metadata about the sophisticated features
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("type", "interactive-demo");
        metadata.put("features", "business-narrative,multiple-choice,guided-workflow,demo-data");
        metadata.put("duration", "15-20 minutes");
        metadata.put("difficulty", "beginner-friendly");
        mcpDemo.set("metadata", metadata);

        return mcpDemo;
    }

    /**
     * Creates the Business Intelligence prompt definition
     */
    private ObjectNode createBusinessIntelligencePromptDefinition() {
        ObjectNode biPrompt = objectMapper.createObjectNode();
        biPrompt.put("name", "business-intelligence");
        biPrompt.put("description", "Comprehensive business intelligence analysis framework. Guides users through structured data discovery, exploratory analysis, businessInsight generation, and dashboard creation for specific business domains.");

        ArrayNode arguments = objectMapper.createArrayNode();

        ObjectNode focusArg = objectMapper.createObjectNode();
        focusArg.put("name", "focus_area");
        focusArg.put("description", "Business area to focus analysis on (e.g., sales, customers, operations, finance). Provides tailored analysis framework and relevant query examples.");
        focusArg.put("required", true);
        arguments.add(focusArg);

        biPrompt.set("arguments", arguments);

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("type", "business-analysis");
        metadata.put("features", "structured-framework,businessInsight-capture,dashboard-creation");
        metadata.put("difficulty", "intermediate");
        biPrompt.set("metadata", metadata);

        return biPrompt;
    }

    /**
     * Creates the enhanced Database Analysis prompt definition
     */
    private ObjectNode createDatabaseAnalysisPromptDefinition() {
        ObjectNode analysisPrompt = objectMapper.createObjectNode();
        analysisPrompt.put("name", "database-analysis");
        analysisPrompt.put("description", "Enhanced database exploration and analysis workflow. Provides structured approach to understanding database structure, running exploratory queries, and capturing businessInsights with built-in guidance.");

        ArrayNode arguments = objectMapper.createArrayNode();

        ObjectNode focusArg = objectMapper.createObjectNode();
        focusArg.put("name", "focus_area");
        focusArg.put("description", "Specific area or domain to focus the analysis on. Helps tailor the exploration approach and query suggestions.");
        focusArg.put("required", true);
        arguments.add(focusArg);

        analysisPrompt.set("arguments", arguments);

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("type", "database-exploration");
        metadata.put("features", "structured-workflow,businessInsight-capture");
        metadata.put("difficulty", "beginner");
        analysisPrompt.set("metadata", metadata);

        return analysisPrompt;
    }

    /**
     * Creates the enhanced database analysis prompt (original functionality improved)
     */
    private JsonNode createAnalysisPrompt(Map<String, String> arguments) {
        String focusArea = arguments.get("focus_area");
        if (focusArea == null || focusArea.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required argument: focus_area");
        }

        String promptTemplate = String.format("""
                # DATABASE ANALYSIS WORKFLOW
                            
                ## Focus Area: %s
                Database Type: %s
                            
                You are a database analyst conducting a comprehensive exploration of this database.
                            
                ## STRUCTURED ANALYSIS APPROACH:
                            
                ### Phase 1: Discovery
                1. **Examine Database Structure**
                   - Use `resources/list` to see all available tables and schemas
                   - Use `resources/read` to understand table structures and relationships
                   - Look for tables related to: %s
                            
                ### Phase 2: Data Exploration
                2. **Run Exploratory Queries**
                   - Start with simple SELECT statements to understand data distribution
                   - Look for patterns, trends, and data quality issues
                   - Focus queries on %s-related metrics and dimensions
                            
                ### Phase 3: Insight Generation
                3. **Capture Significant Findings**
                   - Use the `append_insight` tool whenever you discover something important
                   - Look for business implications of the data patterns
                   - Build a narrative around your discoveries
                            
                ### Phase 4: Comprehensive Understanding
                4. **Synthesize Results**
                   - Connect businessInsights to form a complete picture
                   - Identify actionable recommendations
                   - Prepare summary of key findings
                            
                ## CURRENT FOCUS: %s
                            
                **Getting Started:**
                Begin by examining the database resources to understand what %s-related data is available.
                Then formulate targeted queries that will reveal meaningful businessInsights about this domain.
                            
                **Remember:** Use the `append_insight` tool to capture every significant discovery!
                            
                ---
                *Ready to begin your analysis? Start by exploring the database structure.*
                """, focusArea, databaseType.toUpperCase(), focusArea, focusArea, focusArea, focusArea);

        ObjectNode promptMessage = objectMapper.createObjectNode();
        promptMessage.put("role", "user");
        ObjectNode content = objectMapper.createObjectNode();
        content.put("type", "text");
        content.put("text", promptTemplate);
        promptMessage.set("content", content);

        ArrayNode messages = objectMapper.createArrayNode();
        messages.add(promptMessage);

        ObjectNode result = objectMapper.createObjectNode();
        result.put("name", "database-analysis");
        result.put("description", "Enhanced database analysis workflow for " + focusArea);
        result.set("messages", messages);

        // Add workflow metadata
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("focusArea", focusArea);
        metadata.put("databaseType", databaseType);
        metadata.put("workflowPhases", 4);
        metadata.put("expectedDuration", "10-15 minutes");
        result.set("metadata", metadata);

        return result;
    }

    /**
     * Gets the database type this service is configured for
     */
    @Override
    public String databaseType() {
        return databaseType;
    }
}