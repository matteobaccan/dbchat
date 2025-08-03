package com.skanga.mcp.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowServiceTest {

    private WorkflowService workflowService;
    
    @BeforeEach
    void setUp() {
        workflowService = new WorkflowService("h2");
    }
    
    @Test
    @DisplayName("Should create start_workflow tool with correct structure")
    void testCreateStartWorkflowTool() {
        JsonNode tool = workflowService.createStartWorkflowTool();
        
        assertThat(tool).isNotNull();
        assertThat(tool.get("name").asText()).isEqualTo("start_workflow");
        assertThat(tool.get("description").asText()).contains("INTERACTIVE WORKFLOW");
        assertThat(tool.has("inputSchema")).isTrue();
        
        JsonNode inputSchema = tool.get("inputSchema");
        assertThat(inputSchema.get("type").asText()).isEqualTo("object");
        assertThat(inputSchema.has("properties")).isTrue();
        
        JsonNode properties = inputSchema.get("properties");
        assertThat(properties.has("scenario")).isTrue();
        assertThat(properties.has("userId")).isTrue();
        
        JsonNode scenarioParam = properties.get("scenario");
        assertThat(scenarioParam.get("type").asText()).isEqualTo("string");
        assertThat(scenarioParam.has("enum")).isTrue();
        
        JsonNode enumValues = scenarioParam.get("enum");
        assertThat(enumValues.size()).isEqualTo(4);
        assertThat(enumValues.get(0).asText()).isEqualTo("retail");
        assertThat(enumValues.get(1).asText()).isEqualTo("finance");
        assertThat(enumValues.get(2).asText()).isEqualTo("logistics");
        assertThat(enumValues.get(3).asText()).isEqualTo("generic");
    }
    
    @Test
    @DisplayName("Should create workflow_choice tool with correct structure")
    void testCreateWorkflowChoiceTool() {
        JsonNode tool = workflowService.createWorkflowChoiceTool();
        
        assertThat(tool).isNotNull();
        assertThat(tool.get("name").asText()).isEqualTo("workflow_choice");
        assertThat(tool.get("description").asText()).contains("WORKFLOW PROGRESSION");
        assertThat(tool.has("inputSchema")).isTrue();
        
        JsonNode inputSchema = tool.get("inputSchema");
        assertThat(inputSchema.get("type").asText()).isEqualTo("object");
        assertThat(inputSchema.has("properties")).isTrue();
        
        JsonNode properties = inputSchema.get("properties");
        assertThat(properties.has("workflowId")).isTrue();
        assertThat(properties.has("choiceId")).isTrue();
        assertThat(properties.has("additionalData")).isTrue();
        
        JsonNode required = inputSchema.get("required");
        assertThat(required.size()).isEqualTo(2);
        assertThat(required.get(0).asText()).isEqualTo("workflowId");
        assertThat(required.get(1).asText()).isEqualTo("choiceId");
    }
    
    @Test
    @DisplayName("Should create workflow status resource with correct structure")
    void testCreateWorkflowStatusResource() {
        JsonNode resource = workflowService.createWorkflowStatusResource();
        
        assertThat(resource).isNotNull();
        assertThat(resource.get("uri").asText()).isEqualTo("workflow://status");
        assertThat(resource.get("name").asText()).isEqualTo("Active Workflow Status");
        assertThat(resource.get("description").asText()).contains("Status and progress of all active interactive workflows");
        assertThat(resource.get("mimeType").asText()).isEqualTo("application/json");
        
        assertThat(resource.has("metadata")).isTrue();
        JsonNode metadata = resource.get("metadata");
        assertThat(metadata.get("type").asText()).isEqualTo("workflow-status");
        assertThat(metadata.get("interactive").asBoolean()).isTrue();
    }
    
    @Test
    @DisplayName("Should execute start_workflow for retail scenario successfully")
    void testExecuteStartWorkflowRetail() {
        JsonNode result = workflowService.executeStartWorkflow("retail", "testUser");
        
        assertThat(result).isNotNull();
        assertThat(result.has("content")).isTrue();
        
        JsonNode content = result.get("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.size()).isEqualTo(1);
        
        JsonNode textContent = content.get(0);
        assertThat(textContent.get("type").asText()).isEqualTo("text");
        
        String text = textContent.get("text").asText();
        assertThat(text).contains("INTERACTIVE WORKFLOW STARTED");
        assertThat(text).contains("RETAIL");
        assertThat(text).contains("What would you like to do?");
        assertThat(text).contains("workflow_choice");
    }
    
    @Test
    @DisplayName("Should execute start_workflow for finance scenario successfully")
    void testExecuteStartWorkflowFinance() {
        JsonNode result = workflowService.executeStartWorkflow("finance", "testUser");
        
        assertThat(result).isNotNull();
        assertThat(result.has("content")).isTrue();
        
        String text = result.get("content").get(0).get("text").asText();
        assertThat(text).contains("INTERACTIVE WORKFLOW STARTED");
        assertThat(text).contains("FINANCE");
        assertThat(text).contains("Banking Analysis");
    }
    
    @Test
    @DisplayName("Should execute start_workflow for logistics scenario successfully")
    void testExecuteStartWorkflowLogistics() {
        JsonNode result = workflowService.executeStartWorkflow("logistics", "testUser");
        
        assertThat(result).isNotNull();
        assertThat(result.has("content")).isTrue();
        
        String text = result.get("content").get(0).get("text").asText();
        assertThat(text).contains("INTERACTIVE WORKFLOW STARTED");
        assertThat(text).contains("LOGISTICS");
        assertThat(text).contains("Supply Chain Analysis");
    }
    
    @Test
    @DisplayName("Should execute start_workflow for generic scenario successfully")
    void testExecuteStartWorkflowGeneric() {
        JsonNode result = workflowService.executeStartWorkflow("generic", "testUser");
        
        assertThat(result).isNotNull();
        assertThat(result.has("content")).isTrue();
        
        String text = result.get("content").get(0).get("text").asText();
        assertThat(text).contains("INTERACTIVE WORKFLOW STARTED");
        assertThat(text).contains("GENERIC");
        assertThat(text).contains("Database Analysis");
    }
    
    @Test
    @DisplayName("Should handle missing scenario parameter")
    void testExecuteStartWorkflowMissingScenario() {
        JsonNode result = workflowService.executeStartWorkflow(null, "testUser");
        
        assertThat(result).isNotNull();
        assertThat(result.has("content")).isTrue();
        
        String text = result.get("content").get(0).get("text").asText();
        assertThat(text).contains("WORKFLOW ERROR");
        assertThat(text).contains("Missing required parameter: scenario");
    }
    
    @Test
    @DisplayName("Should handle empty scenario parameter")
    void testExecuteStartWorkflowEmptyScenario() {
        JsonNode result = workflowService.executeStartWorkflow("", "testUser");
        
        assertThat(result).isNotNull();
        assertThat(result.has("content")).isTrue();
        
        String text = result.get("content").get(0).get("text").asText();
        assertThat(text).contains("WORKFLOW ERROR");
        assertThat(text).contains("Missing required parameter: scenario");
    }
    
    @Test
    @DisplayName("Should handle invalid scenario parameter")
    void testExecuteStartWorkflowInvalidScenario() {
        JsonNode result = workflowService.executeStartWorkflow("invalid", "testUser");
        
        assertThat(result).isNotNull();
        assertThat(result.has("content")).isTrue();
        
        String text = result.get("content").get(0).get("text").asText();
        assertThat(text).contains("WORKFLOW ERROR");
        assertThat(text).contains("Invalid scenario: invalid");
        assertThat(text).contains("Available scenarios: retail, finance, logistics, generic");
    }
    
    @Test
    @DisplayName("Should handle missing user ID with default")
    void testExecuteStartWorkflowMissingUserId() {
        JsonNode result = workflowService.executeStartWorkflow("retail", null);
        
        assertThat(result).isNotNull();
        assertThat(result.has("content")).isTrue();
        
        String text = result.get("content").get(0).get("text").asText();
        assertThat(text).contains("INTERACTIVE WORKFLOW STARTED");
        assertThat(text).contains("RETAIL");
    }
    
    @Test
    @DisplayName("Should handle workflow choice with missing workflow ID")
    void testExecuteWorkflowChoiceMissingWorkflowId() {
        Map<String, String> additionalData = new HashMap<>();
        JsonNode result = workflowService.executeWorkflowChoice(null, "test_choice", additionalData);
        
        assertThat(result).isNotNull();
        assertThat(result.has("content")).isTrue();
        
        String text = result.get("content").get(0).get("text").asText();
        assertThat(text).contains("WORKFLOW ERROR");
        assertThat(text).contains("Missing required parameter: workflowId");
    }
    
    @Test
    @DisplayName("Should handle workflow choice with missing choice ID")
    void testExecuteWorkflowChoiceMissingChoiceId() {
        Map<String, String> additionalData = new HashMap<>();
        JsonNode result = workflowService.executeWorkflowChoice("test_workflow", null, additionalData);
        
        assertThat(result).isNotNull();
        assertThat(result.has("content")).isTrue();
        
        String text = result.get("content").get(0).get("text").asText();
        assertThat(text).contains("WORKFLOW ERROR");
        assertThat(text).contains("Missing required parameter: choiceId");
    }
    
    @Test
    @DisplayName("Should handle workflow choice with invalid workflow ID")
    void testExecuteWorkflowChoiceInvalidWorkflowId() {
        Map<String, String> additionalData = new HashMap<>();
        JsonNode result = workflowService.executeWorkflowChoice("invalid_workflow", "test_choice", additionalData);
        
        assertThat(result).isNotNull();
        assertThat(result.has("content")).isTrue();
        
        String text = result.get("content").get(0).get("text").asText();
        assertThat(text).contains("WORKFLOW ERROR");
        assertThat(text).contains("Workflow not found: invalid_workflow");
    }
    
    @Test
    @DisplayName("Should get workflow status content")
    void testGetWorkflowStatusContent() {
        JsonNode statusContent = workflowService.getWorkflowStatusContent();
        
        assertThat(statusContent).isNotNull();
        assertThat(statusContent.get("title").asText()).isEqualTo("Active Workflow Status");
        assertThat(statusContent.get("databaseType").asText()).isEqualTo("H2");
        assertThat(statusContent.has("timestamp")).isTrue();
        assertThat(statusContent.get("totalActiveWorkflows").asInt()).isEqualTo(0);
        
        // Should have suggestions when no active workflows
        assertThat(statusContent.has("suggestions")).isTrue();
        JsonNode suggestions = statusContent.get("suggestions");
        assertThat(suggestions.has("startWorkflow")).isTrue();
        assertThat(suggestions.has("availableScenarios")).isTrue();
        assertThat(suggestions.has("integration")).isTrue();
    }
    
    @Test
    @DisplayName("Should handle workflow progression correctly")
    void testWorkflowProgression() {
        // Start a workflow
        JsonNode startResult = workflowService.executeStartWorkflow("retail", "testUser");
        assertThat(startResult).isNotNull();
        
        String startText = startResult.get("content").get(0).get("text").asText();
        assertThat(startText).contains("INTERACTIVE WORKFLOW STARTED");
        
        // Extract workflow ID from the start result
        String workflowId = extractWorkflowId(startText);
        assertThat(workflowId).isNotNull().isNotEmpty();
        
        // Check workflow status shows active workflow
        JsonNode statusContent = workflowService.getWorkflowStatusContent();
        assertThat(statusContent.get("totalActiveWorkflows").asInt()).isEqualTo(1);
        assertThat(statusContent.has("activeWorkflows")).isTrue();
        assertThat(statusContent.has("recommendations")).isTrue();
    }
    
    @Test
    @DisplayName("Should validate database type configuration")
    void testDatabaseTypeConfiguration() {
        WorkflowService serviceWithNullType = new WorkflowService(null);
        assertThat(serviceWithNullType.getDatabaseType()).isEqualTo("unknown");
        
        WorkflowService serviceWithValidType = new WorkflowService("postgresql");
        assertThat(serviceWithValidType.getDatabaseType()).isEqualTo("postgresql");
    }
    
    // Helper method to extract workflow ID from response text
    private String extractWorkflowId(String text) {
        String marker = "Workflow ID: ";
        int startIndex = text.indexOf(marker);
        if (startIndex == -1) return null;
        
        startIndex += marker.length();
        int endIndex = text.indexOf('\n', startIndex);
        if (endIndex == -1) endIndex = text.length();
        
        return text.substring(startIndex, endIndex).trim();
    }
}