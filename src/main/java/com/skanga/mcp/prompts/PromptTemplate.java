package com.skanga.mcp.prompts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skanga.mcp.demo.BusinessScenario;

/**
 * Manages structured prompt templates for MCP prompts.
 * Creates sophisticated demo/onboarding workflows with business narratives,
 * multiple choice progressions, and guided data exploration.
 */
public class PromptTemplate {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Creates the sophisticated "mcp-demo" prompt template with complete guided experience
     */
    public static JsonNode createMcpDemoPrompt(String topic, String databaseType) {
        BusinessScenario scenario = getScenarioForTopic(topic);
        
        String promptTemplate = createDemoWorkflowTemplate(scenario, databaseType);
        
        ObjectNode promptMessage = objectMapper.createObjectNode();
        promptMessage.put("role", "user");
        
        ObjectNode content = objectMapper.createObjectNode();
        content.put("type", "text");
        content.put("text", promptTemplate);
        promptMessage.set("content", content);
        
        ArrayNode messages = objectMapper.createArrayNode();
        messages.add(promptMessage);
        
        ObjectNode result = objectMapper.createObjectNode();
        result.put("name", "mcp-demo");
        result.put("description", "Complete guided database analysis demo with " + scenario.name());
        result.set("messages", messages);
        
        // Add metadata for the interactive workflow
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("scenario", scenario.name());
        metadata.put("hasMultipleChoice", true);
        metadata.put("requiresDemoData", true);
        metadata.put("expectedDuration", "15-20 minutes");
        result.set("metadata", metadata);
        
        return result;
    }
    
    /**
     * Creates the comprehensive demo workflow template
     */
    private static String createDemoWorkflowTemplate(BusinessScenario scenario, String databaseType) {
        StringBuilder template = new StringBuilder();
        
        // Business context and narrative
        template.append("# INTERACTIVE DATABASE ANALYSIS DEMO\n\n");
        template.append("## Business Scenario: ").append(scenario.description()).append("\n\n");
        template.append("**Context:** ").append(scenario.businessContext()).append("\n\n");
        
        // Protagonists and urgency
        template.append("**Key Stakeholders:**\n");
        for (String protagonist : scenario.protagonists()) {
            template.append("- ").append(protagonist).append("\n");
        }
        template.append("\n**Deadline:** ").append(scenario.deadline()).append("\n\n");
        
        // Demo workflow instructions
        template.append("---\n\n");
        template.append("## GUIDED ANALYSIS WORKFLOW\n\n");
        template.append("Welcome to an interactive database analysis session! This demo will guide you through a realistic business scenario using a ").append(databaseType.toUpperCase()).append(" database.\n\n");
        
        template.append("### Your Mission\n");
        template.append("Help the team analyze their data to make informed business decisions. You'll work through structured steps, making choices about what to explore next.\n\n");
        
        // Available data overview
        template.append("### Available Data\n");
        scenario.tableDefinitions().forEach((table, description) -> template.append("- **").append(table).append("**: ").append(description).append("\n"));
        template.append("\n");
        
        // Step-by-step workflow
        template.append("### Analysis Steps\n\n");
        template.append("**IMPORTANT:** This is an interactive workflow. After each step, you'll be presented with multiple choice options to guide your next action.\n\n");
        
        for (int i = 0; i < scenario.analysisSteps().size(); i++) {
            BusinessScenario.AnalysisStep step = scenario.analysisSteps().get(i);
            template.append("#### Step ").append(i + 1).append(": ").append(step.title()).append("\n");
            template.append(step.description()).append("\n\n");
            
            if (step.suggestedQuery() != null) {
                template.append("**Suggested Query:**\n");
                template.append("```sql\n").append(step.suggestedQuery()).append("\n```\n\n");
            }
            
            template.append("**Analysis Options:**\n");
            for (String option : step.multipleChoiceOptions()) {
                template.append(option).append("\n");
            }
            template.append("\n**Expected Outcome:** ").append(step.expectedResult()).append("\n\n");
            template.append("---\n\n");
        }
        
        // Instructions for interactive workflow
        template.append("## HOW TO USE THIS DEMO\n\n");
        template.append("1. **Start by exploring the database structure** using the `resources/list` and `resources/read` tools\n");
        template.append("2. **Run the suggested queries** using the `run_sql` tool to understand the data\n");
        template.append("3. **Record businessInsights** as you discover them using the `append_insight` tool\n");
        template.append("4. **Follow the multiple choice options** - tell me your choice (A, B, or C) to guide the analysis\n");
        template.append("5. **Build upon previous discoveries** to form a comprehensive understanding\n\n");
        
        // Demo data setup note
        template.append("## Demo Data Setup\n\n");
        template.append("If you don't see the expected tables, this scenario includes automatic demo data generation. The system will create:\n");
        scenario.tableDefinitions().forEach((table, description) -> template.append("- **").append(table).append("** table with realistic sample data\n"));
        template.append("\n");
        
        // Expected final businessInsight
        template.append("## Expected Final Insight\n\n");
        template.append("By the end of this analysis, you should discover: *").append(scenario.expectedInsight()).append("*\n\n");
        
        // Call to action
        template.append("---\n\n");
        template.append("## LET'S BEGIN!\n\n");
        template.append("Ready to dive into the data? Start by examining the database resources to see what's available, then let me know if you'd like to:\n\n");
        template.append("**A)** Begin with the suggested Step 1 analysis\n");
        template.append("**B)** Explore the database structure first\n");
        template.append("**C)** Set up demo data if tables are missing\n\n");
        template.append("What's your choice? Simply respond with A, B, or C to get started!\n\n");
        
        // Security and usage notes
        template.append("*Note: This demo uses realistic but synthetic data. All queries and businessInsights are for educational purposes.*");
        
        return template.toString();
    }
    
    /**
     * Creates a business intelligence prompt template
     */
    public static JsonNode createBusinessIntelligencePrompt(String focusArea, String databaseType) {
        if (focusArea == null || focusArea.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required argument: focus_area");
        }
        
        String template = String.format("""
            # BUSINESS INTELLIGENCE ANALYSIS
            
            ## Focus Area: %s
            Database Type: %s
            
            You are a senior business analyst tasked with conducting a comprehensive analysis of %s data.
            
            ## Analysis Framework:
            
            ### 1. Data Discovery Phase
            - Examine database structure and available tables
            - Identify key metrics and dimensions related to %s
            - Understand data quality and completeness
            
            ### 2. Exploratory Analysis
            - Run descriptive statistics on key metrics
            - Identify trends, patterns, and anomalies
            - Look for correlations and relationships
            
            ### 3. Insight Generation
            - Use the `append_insight` tool to capture significant findings
            - Build a narrative around the data story
            - Identify actionable recommendations
            
            ### 4. Dashboard Creation
            - Summarize key findings in a structured format
            - Present businessInsights with supporting data
            - Provide clear recommendations for stakeholders
            
            ## Getting Started:
            
            Begin by examining the database resources to understand what %s-related data is available.
            Then formulate queries that will reveal meaningful businessInsights about business performance.
            
            Remember to use the `append_insight` tool whenever you discover something significant!
            """, focusArea, databaseType.toUpperCase(), focusArea, focusArea, focusArea);
        
        ObjectNode promptMessage = objectMapper.createObjectNode();
        promptMessage.put("role", "user");
        ObjectNode content = objectMapper.createObjectNode();
        content.put("type", "text");
        content.put("text", template);
        promptMessage.set("content", content);
        
        ArrayNode messages = objectMapper.createArrayNode();
        messages.add(promptMessage);
        
        ObjectNode result = objectMapper.createObjectNode();
        result.put("name", "business-intelligence");
        result.put("description", "Business intelligence analysis focused on " + focusArea);
        result.set("messages", messages);
        
        return result;
    }
    
    /**
     * Gets the appropriate business scenario based on the topic
     */
    private static BusinessScenario getScenarioForTopic(String topic) {
        if (topic == null) {
            return BusinessScenario.createRetailScenario(); // Default
        }
        
        String lowerTopic = topic.toLowerCase();
        if (lowerTopic.contains("retail") || lowerTopic.contains("ecommerce") || 
            lowerTopic.contains("sales") || lowerTopic.contains("customers")) {
            return BusinessScenario.createRetailScenario();
        } else if (lowerTopic.contains("finance") || lowerTopic.contains("bank") || 
                   lowerTopic.contains("account") || lowerTopic.contains("transaction")) {
            return BusinessScenario.createFinanceScenario();
        } else {
            // Default to retail for unknown topics
            return BusinessScenario.createRetailScenario();
        }
    }
}