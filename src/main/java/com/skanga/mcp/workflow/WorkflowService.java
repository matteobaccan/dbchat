package com.skanga.mcp.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Workflow Service - MCP integration layer for Interactive Multiple Choice Workflows.
 * Provides MCP tools and resources for managing guided database analysis workflows
 * with structured progressions and contextual choices for non-technical users.
 */
public class WorkflowService {
    private static final Logger logger = LoggerFactory.getLogger(WorkflowService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final WorkflowManager workflowManager;
    private final String databaseType;
    
    public WorkflowService(String databaseType) {
        this.databaseType = databaseType != null ? databaseType : "unknown";
        this.workflowManager = new WorkflowManager(this.databaseType);
        logger.info("WorkflowService initialized for database type: {}", this.databaseType);
    }
    
    /**
     * Creates MCP tool definition for start_workflow
     */
    public JsonNode createStartWorkflowTool() {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "start_workflow");
        tool.put("description", 
            "INTERACTIVE WORKFLOW: Starts a guided database analysis workflow with multiple choice progressions. " +
            "Perfect for non-technical users who want structured guidance through database exploration. " +
            "Provides step-by-step analysis with contextual choices, suggested queries, and insight capture.");
        
        ObjectNode inputSchema = objectMapper.createObjectNode();
        inputSchema.put("type", "object");
        
        ObjectNode properties = objectMapper.createObjectNode();
        
        // Scenario type parameter
        ObjectNode scenarioParam = objectMapper.createObjectNode();
        scenarioParam.put("type", "string");
        scenarioParam.put("description", 
            "Type of analysis workflow to start. Available workflows: " +
            "• 'retail' - E-commerce analysis with customers, products, and orders " +
            "• 'finance' - Banking analysis with accounts, transactions, and loans " +
            "• 'logistics' - Supply chain analysis with shipments, routes, and deliveries " +
            "• 'generic' - General database exploration workflow");
        
        ArrayNode scenarioEnum = objectMapper.createArrayNode();
        scenarioEnum.add("retail");
        scenarioEnum.add("finance");
        scenarioEnum.add("logistics");
        scenarioEnum.add("generic");
        scenarioParam.set("enum", scenarioEnum);
        
        properties.set("scenario", scenarioParam);
        
        // User ID parameter (optional)
        ObjectNode userParam = objectMapper.createObjectNode();
        userParam.put("type", "string");
        userParam.put("description", "User identifier for workflow tracking (default: 'user')");
        userParam.put("default", "user");
        properties.set("userId", userParam);
        
        inputSchema.set("properties", properties);
        
        ArrayNode required = objectMapper.createArrayNode();
        required.add("scenario");
        inputSchema.set("required", required);
        
        tool.set("inputSchema", inputSchema);
        
        // Add metadata
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("category", "workflow");
        metadata.put("interactive", true);
        metadata.put("guidance", "structured");
        metadata.put("userFriendly", true);
        tool.set("metadata", metadata);
        
        return tool;
    }
    
    /**
     * Creates MCP tool definition for workflow_choice
     */
    public JsonNode createWorkflowChoiceTool() {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "workflow_choice");
        tool.put("description", 
            "WORKFLOW PROGRESSION: Processes user choice in an active workflow and advances to the next step. " +
            "Use this tool to respond to multiple choice questions and continue guided analysis. " +
            "Each choice shapes the analysis path and provides contextual next steps.");
        
        ObjectNode inputSchema = objectMapper.createObjectNode();
        inputSchema.put("type", "object");
        
        ObjectNode properties = objectMapper.createObjectNode();
        
        // Workflow ID parameter
        ObjectNode workflowParam = objectMapper.createObjectNode();
        workflowParam.put("type", "string");
        workflowParam.put("description", "ID of the active workflow (returned by start_workflow)");
        properties.set("workflowId", workflowParam);
        
        // Choice ID parameter
        ObjectNode choiceParam = objectMapper.createObjectNode();
        choiceParam.put("type", "string");
        choiceParam.put("description", "ID of the selected choice option from the current step");
        properties.set("choiceId", choiceParam);
        
        // Additional data parameter (optional)
        ObjectNode dataParam = objectMapper.createObjectNode();
        dataParam.put("type", "object");
        dataParam.put("description", "Optional additional data for the choice (e.g., custom query text)");
        properties.set("additionalData", dataParam);
        
        inputSchema.set("properties", properties);
        
        ArrayNode required = objectMapper.createArrayNode();
        required.add("workflowId");
        required.add("choiceId");
        inputSchema.set("required", required);
        
        tool.set("inputSchema", inputSchema);
        
        return tool;
    }
    
    /**
     * Creates MCP resource for workflow status
     */
    public JsonNode createWorkflowStatusResource() {
        ObjectNode resource = objectMapper.createObjectNode();
        resource.put("uri", "workflow://status");
        resource.put("name", "Active Workflow Status");
        resource.put("description", "Status and progress of all active interactive workflows");
        resource.put("mimeType", "application/json");
        
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("type", "workflow-status");
        metadata.put("interactive", true);
        resource.set("metadata", metadata);
        
        return resource;
    }
    
    /**
     * Executes the start_workflow tool
     */
    public JsonNode executeStartWorkflow(String scenario, String userId) {
        logger.info("Starting workflow: scenario={}, userId={}", scenario, userId);
        
        if (scenario == null || scenario.trim().isEmpty()) {
            return createErrorResponse("Missing required parameter: scenario");
        }
        
        String normalizedScenario = scenario.toLowerCase().trim();
        String normalizedUserId = userId != null ? userId.trim() : "user";
        
        if (normalizedUserId.isEmpty()) {
            normalizedUserId = "user";
        }
        
        // Validate scenario
        if (!isValidScenario(normalizedScenario)) {
            return createErrorResponse("Invalid scenario: " + scenario + 
                ". Available scenarios: retail, finance, logistics, generic");
        }
        
        try {
            JsonNode workflowResponse = workflowManager.startWorkflow(normalizedScenario, normalizedUserId);
            
            // Wrap in MCP tool response format
            ObjectNode responseNode = objectMapper.createObjectNode();
            ArrayNode contentNode = objectMapper.createArrayNode();
            
            ObjectNode textContent = objectMapper.createObjectNode();
            textContent.put("type", "text");
            
            String workflowId = workflowResponse.path("workflowId").asText("");
            String title = workflowResponse.path("title").asText("Workflow Started");
            String description = workflowResponse.path("description").asText("Interactive workflow has been initiated");
            double progress = workflowResponse.path("progress").asDouble(0.0);
            
            StringBuilder responseText = new StringBuilder();
            responseText.append("INTERACTIVE WORKFLOW STARTED\n\n");
            responseText.append(String.format("Workflow ID: %s\n", workflowId));
            responseText.append(String.format("Scenario: %s\n", normalizedScenario.toUpperCase()));
            responseText.append(String.format("Progress: %.1f%%\n\n", progress));
            
            responseText.append(String.format("## %s\n\n", title));
            responseText.append(String.format("%s\n\n", description));
            
            // Add multiple choice options
            if (workflowResponse.has("choices")) {
                responseText.append("## What would you like to do?\n\n");
                JsonNode choices = workflowResponse.get("choices");
                for (JsonNode choice : choices) {
                    String choiceId = choice.get("id").asText();
                    String label = choice.get("label").asText();
                    String choiceDesc = choice.get("description").asText();
                    boolean recommended = choice.get("recommended").asBoolean();
                    
                    String emoji = recommended ? "⭐" : "•";
                    responseText.append(String.format("%s **%s** (`%s`)\n", emoji, label, choiceId));
                    responseText.append(String.format("   %s\n\n", choiceDesc));
                }
                
                responseText.append("**Next Step:** Use the `workflow_choice` tool with your selected choice ID\n");
                responseText.append(String.format("   Example: `{\"workflowId\": \"%s\", \"choiceId\": \"explore_customers\"}`\n\n", workflowId));
            }
            
            // Add guidance
            if (workflowResponse.has("guidance")) {
                JsonNode guidance = workflowResponse.get("guidance");
                responseText.append("## Guidance\n\n");
                if (guidance.has("tip")) {
                    responseText.append(String.format("**Tip:** %s\n\n", guidance.get("tip").asText()));
                }
                if (guidance.has("whatToExpect")) {
                    responseText.append(String.format("**What to Expect:** %s\n\n", guidance.get("whatToExpect").asText()));
                }
            }
            
            textContent.put("text", responseText.toString());
            contentNode.add(textContent);
            responseNode.set("content", contentNode);
            
            // Log workflow start event
            logWorkflowEvent("WORKFLOW_STARTED", String.format("Scenario: %s, User: %s, WorkflowID: %s", 
                            normalizedScenario, normalizedUserId, workflowId));
            
            return responseNode;
            
        } catch (Exception e) {
            logger.error("Error starting workflow: {}", e.getMessage(), e);
            return createErrorResponse("Failed to start workflow: " + e.getMessage());
        }
    }
    
    /**
     * Executes the workflow_choice tool
     */
    public JsonNode executeWorkflowChoice(String workflowId, String choiceId, Map<String, String> additionalData) {
        logger.info("Processing workflow choice: workflowId={}, choiceId={}", workflowId, choiceId);
        
        if (workflowId == null || workflowId.trim().isEmpty()) {
            return createErrorResponse("Missing required parameter: workflowId");
        }
        
        if (choiceId == null || choiceId.trim().isEmpty()) {
            return createErrorResponse("Missing required parameter: choiceId");
        }
        
        try {
            JsonNode workflowResponse = workflowManager.processChoice(workflowId, choiceId, additionalData);
            
            if (workflowResponse.has("error")) {
                return createErrorResponse(workflowResponse.get("error").asText());
            }
            
            // Check if workflow is completed
            if (workflowResponse.has("completed") && workflowResponse.get("completed").asBoolean()) {
                return formatCompletionResponse(workflowResponse);
            }
            
            // Format next step response
            return formatStepResponse(workflowResponse);
            
        } catch (Exception e) {
            logger.error("Error processing workflow choice: {}", e.getMessage(), e);
            return createErrorResponse("Failed to process workflow choice: " + e.getMessage());
        }
    }
    
    /**
     * Gets workflow status content
     */
    public JsonNode getWorkflowStatusContent() {
        JsonNode statusResponse = workflowManager.listActiveWorkflows();
        
        ObjectNode content = objectMapper.createObjectNode();
        content.put("title", "Active Workflow Status");
        content.put("databaseType", databaseType.toUpperCase());
        content.put("timestamp", java.time.LocalDateTime.now().toString());
        
        int totalActive = statusResponse.get("totalActive").asInt();
        content.put("totalActiveWorkflows", totalActive);
        
        if (totalActive > 0) {
            content.set("activeWorkflows", statusResponse.get("workflows"));
            
            ObjectNode recommendations = objectMapper.createObjectNode();
            recommendations.put("continueWorkflow", "Use 'workflow_choice' tool to continue active workflows");
            recommendations.put("statusCheck", "Monitor progress and completion status");
            recommendations.put("newWorkflow", "Start additional workflows with 'start_workflow' tool");
            content.set("recommendations", recommendations);
        } else {
            ObjectNode suggestions = objectMapper.createObjectNode();
            suggestions.put("startWorkflow", "Use 'start_workflow' tool to begin guided analysis");
            suggestions.put("availableScenarios", "retail, finance, logistics, generic");
            suggestions.put("integration", "Workflows integrate with demo data and insight capture");
            content.set("suggestions", suggestions);
        }
        
        return content;
    }
    
    // Helper methods
    
    private boolean isValidScenario(String scenario) {
        return scenario.equals("retail") || scenario.equals("finance") || 
               scenario.equals("logistics") || scenario.equals("generic");
    }
    
    private JsonNode formatStepResponse(JsonNode workflowResponse) {
        ObjectNode responseNode = objectMapper.createObjectNode();
        ArrayNode contentNode = objectMapper.createArrayNode();
        
        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        
        String workflowId = workflowResponse.path("workflowId").asText("");
        int stepNumber = workflowResponse.path("stepNumber").asInt(0);
        String title = workflowResponse.path("title").asText("Workflow Step");
        String description = workflowResponse.path("description").asText("Continuing workflow progression");
        double progress = workflowResponse.path("progress").asDouble(0.0);
        
        StringBuilder responseText = new StringBuilder();
        responseText.append(String.format("## Step %d: %s\n\n", stepNumber + 1, title));
        responseText.append(String.format("Progress: %.1f%%\n\n", progress));
        responseText.append(String.format("%s\n\n", description));
        
        // Add suggested query if present
        if (workflowResponse.has("suggestedQuery")) {
            String query = workflowResponse.get("suggestedQuery").asText();
            responseText.append("## Suggested Query\n\n");
            responseText.append("```sql\n");
            responseText.append(query);
            responseText.append("\n```\n\n");
        }
        
        // Add multiple choice options
        if (workflowResponse.has("choices")) {
            responseText.append("## What would you like to do next?\n\n");
            JsonNode choices = workflowResponse.get("choices");
            for (JsonNode choice : choices) {
                String choiceId = choice.get("id").asText();
                String label = choice.get("label").asText();
                String choiceDesc = choice.get("description").asText();
                boolean recommended = choice.get("recommended").asBoolean();
                
                String emoji = recommended ? "⭐" : "•";
                responseText.append(String.format("%s **%s** (`%s`)\n", emoji, label, choiceId));
                responseText.append(String.format("   %s\n\n", choiceDesc));
            }
            
            responseText.append("**Continue:** Use the `workflow_choice` tool with your selected choice ID\n");
            responseText.append(String.format("   Example: `{\"workflowId\": \"%s\", \"choiceId\": \"run_query\"}`\n\n", workflowId));
        }
        
        textContent.put("text", responseText.toString());
        contentNode.add(textContent);
        responseNode.set("content", contentNode);
        
        return responseNode;
    }
    
    private JsonNode formatCompletionResponse(JsonNode completionResponse) {
        ObjectNode responseNode = objectMapper.createObjectNode();
        ArrayNode contentNode = objectMapper.createArrayNode();
        
        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        
        String workflowId = completionResponse.path("workflowId").asText("");
        String scenarioType = completionResponse.path("scenarioType").asText("unknown");
        int totalSteps = completionResponse.path("totalSteps").asInt(0);
        long duration = completionResponse.path("duration").asLong(0);
        String summary = completionResponse.path("summary").asText("Workflow completed successfully");
        
        String responseText = String.format("""
            WORKFLOW COMPLETED!
            
            Successfully completed %s analysis workflow
            Workflow ID: %s
            Total Steps: %d
            Duration: %.1f minutes
            
            ## Summary
            %s
            
            ## What's Next?
            • Review captured businessInsights with businessInsights resources
            • Start a new workflow to explore different aspects
            • Use the knowledge gained for independent analysis
            • Generate reports using the businessInsights memo
            
            **Tip:** Use `resources/read businessInsights://memo` to see all businessInsights captured during this workflow.
            """, 
            scenarioType.toUpperCase(),
            workflowId,
            totalSteps,
            duration / (1000.0 * 60.0),
            summary
        );
        
        textContent.put("text", responseText);
        contentNode.add(textContent);
        responseNode.set("content", contentNode);
        
        return responseNode;
    }
    
    private JsonNode createErrorResponse(String message) {
        ObjectNode error = objectMapper.createObjectNode();
        ArrayNode contentNode = objectMapper.createArrayNode();
        
        ObjectNode textContent = objectMapper.createObjectNode();
        textContent.put("type", "text");
        textContent.put("text", "WORKFLOW ERROR\n\n" + message);
        
        contentNode.add(textContent);
        error.set("content", contentNode);
        
        return error;
    }
    
    private void logWorkflowEvent(String event, String details) {
        Logger securityLogger = LoggerFactory.getLogger("SECURITY." + WorkflowService.class.getName());
        securityLogger.info("WORKFLOW_EVENT: {} - {}", event, details);
    }
    
    public String getDatabaseType() {
        return databaseType;
    }
}