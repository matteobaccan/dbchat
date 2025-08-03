package com.skanga.mcp.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skanga.mcp.db.DatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Demo Data Service - MCP integration layer for automatic demo data setup.
 * Provides MCP tools and resources for managing demo scenarios with realistic business datasets.
 * Integrates with DemoDataGenerator for database-agnostic demo data creation.
 */
public class DemoDataService {
    private static final Logger logger = LoggerFactory.getLogger(DemoDataService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final DemoDataGenerator generator;
    private final String databaseType;
    
    public DemoDataService(DatabaseService databaseService, String databaseType) {
        this.databaseType = databaseType != null ? databaseType : "unknown";
        this.generator = new DemoDataGenerator(databaseService, this.databaseType);
        logger.info("DemoDataService initialized for database type: {}", this.databaseType);
    }
    
    /**
     * Creates MCP tool definition for setup_demo_scenario
     */
    public JsonNode createSetupDemoTool() {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("name", "setup_demo_scenario");
        tool.put("description", 
            "Sets up realistic demo data for business analysis scenarios. " +
            "Creates complete datasets with related tables, synthetic data, and business relationships " +
            "perfect for demonstrating database analysis capabilities.");
        
        ObjectNode inputSchema = objectMapper.createObjectNode();
        inputSchema.put("type", "object");
        
        ObjectNode properties = objectMapper.createObjectNode();
        
        // Scenario parameter
        ObjectNode scenarioParam = objectMapper.createObjectNode();
        scenarioParam.put("type", "string");
        scenarioParam.put("description", 
            "Demo scenario to set up. Available scenarios: " +
            "• 'retail' - E-commerce store with customers, products, orders, inventory " +
            "• 'finance' - Banking operations with accounts, transactions, loans, credit scores " +
            "• 'logistics' - Supply chain with warehouses, shipments, routes, deliveries");
        
        ArrayNode scenarioEnum = objectMapper.createArrayNode();
        scenarioEnum.add("retail");
        scenarioEnum.add("finance");
        scenarioEnum.add("logistics");
        scenarioParam.set("enum", scenarioEnum);
        
        properties.set("scenario", scenarioParam);
        
        // Reset parameter (optional)
        ObjectNode resetParam = objectMapper.createObjectNode();
        resetParam.put("type", "boolean");
        resetParam.put("description", 
            "Whether to clean up existing demo data before setting up new scenario (default: true)");
        resetParam.put("default", true);
        properties.set("reset", resetParam);
        
        inputSchema.set("properties", properties);
        
        ArrayNode required = objectMapper.createArrayNode();
        required.add("scenario");
        inputSchema.set("required", required);
        
        tool.set("inputSchema", inputSchema);
        
        // Add metadata about capabilities
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("category", "demo-data");
        metadata.put("riskLevel", "medium");
        metadata.put("description", "Creates and manages demo datasets for analysis");
        ArrayNode capabilities = objectMapper.createArrayNode();
        capabilities.add("creates-tables");
        capabilities.add("inserts-data");
        capabilities.add("database-modification");
        metadata.set("capabilities", capabilities);
        tool.set("metadata", metadata);
        
        return tool;
    }
    
    /**
     * Creates MCP tool definition for list_demo_scenarios
     */
    public JsonNode createListScenariosResource() {
        ObjectNode resource = objectMapper.createObjectNode();
        resource.put("uri", "demo://scenarios");
        resource.put("name", "Available Demo Scenarios");
        resource.put("description", "List of all available demo scenarios with descriptions and table information");
        resource.put("mimeType", "application/json");
        
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("type", "demo-scenarios");
        metadata.put("scenarioCount", DemoDataGenerator.getAvailableScenarios().size());
        resource.set("metadata", metadata);
        
        return resource;
    }
    
    /**
     * Creates MCP resource for demo scenario status
     */
    public JsonNode createScenarioStatusResource() {
        ObjectNode resource = objectMapper.createObjectNode();
        resource.put("uri", "demo://status");
        resource.put("name", "Demo Scenario Status");
        resource.put("description", "Current status of demo scenarios - which are active, table counts, data statistics");
        resource.put("mimeType", "application/json");
        return resource;
    }
    
    /**
     * Executes the setup_demo_scenario tool
     */
    public JsonNode executeSetupDemoScenario(String scenario, boolean reset) {
        logger.info("Setting up demo scenario: {} (reset: {})", scenario, reset);
        
        if (scenario == null || scenario.trim().isEmpty()) {
            return createErrorResponse("Missing required parameter: scenario");
        }
        
        String normalizedScenario = scenario.toLowerCase().trim();
        
        // Validate scenario
        if (!DemoDataGenerator.getAvailableScenarios().contains(normalizedScenario)) {
            return createErrorResponse("Unknown demo scenario: " + scenario + 
                ". Available scenarios: " + String.join(", ", DemoDataGenerator.getAvailableScenarios()));
        }
        
        try {
            // Clean up if requested
            if (reset) {
                boolean cleanupSuccess = generator.cleanupDemoScenario(normalizedScenario);
                if (!cleanupSuccess) {
                    logger.warn("Cleanup failed for scenario: {}, continuing with setup", normalizedScenario);
                }
            }
            
            // Set up the demo scenario
            boolean success = generator.setupDemoScenario(normalizedScenario);
            
            if (success) {
                DemoDataGenerator.DemoScenario scenarioInfo = DemoDataGenerator.getScenarioInfo(normalizedScenario);
                
                ObjectNode result = objectMapper.createObjectNode();
                result.put("success", true);
                result.put("scenario", normalizedScenario);
                result.put("message", String.format("Successfully set up %s demo scenario", scenarioInfo.displayName));
                
                ObjectNode details = objectMapper.createObjectNode();
                details.put("scenarioName", scenarioInfo.displayName);
                details.put("description", scenarioInfo.description);
                details.put("tablesCreated", scenarioInfo.tables.size());
                
                ArrayNode tables = objectMapper.createArrayNode();
                scenarioInfo.tables.forEach(tables::add);
                details.set("tables", tables);
                
                details.put("databaseType", databaseType.toUpperCase());
                details.put("dataGenerated", true);
                details.put("recommendedNextSteps", 
                    "Use the 'mcp-demo' prompt with topic='" + normalizedScenario + 
                    "' for guided analysis, or run 'SELECT * FROM " + scenarioInfo.tables.get(0) + " LIMIT 5' to explore the data");
                
                result.set("details", details);
                
                // Log success for audit
                logDemoEvent("DEMO_SCENARIO_SETUP", String.format("Scenario: %s, Tables: %d", 
                    normalizedScenario, scenarioInfo.tables.size()));
                
                return result;
            } else {
                return createErrorResponse("Failed to set up demo scenario: " + scenario + 
                    ". Check database permissions and connection.");
            }
            
        } catch (Exception e) {
            logger.error("Error setting up demo scenario: {}", scenario, e);
            return createErrorResponse("Error setting up demo scenario: " + e.getMessage());
        }
    }
    
    /**
     * Gets the content for demo scenarios resource
     */
    public JsonNode getDemoScenariosContent() {
        ObjectNode content = objectMapper.createObjectNode();
        content.put("title", "Available Demo Scenarios");
        content.put("description", "Complete business scenarios with realistic synthetic data for database analysis");
        content.put("databaseType", databaseType.toUpperCase());
        
        ArrayNode scenarios = objectMapper.createArrayNode();
        
        for (String scenarioName : DemoDataGenerator.getAvailableScenarios()) {
            DemoDataGenerator.DemoScenario scenario = DemoDataGenerator.getScenarioInfo(scenarioName);
            if (scenario != null) {
                ObjectNode scenarioNode = objectMapper.createObjectNode();
                scenarioNode.put("name", scenario.name);
                scenarioNode.put("displayName", scenario.displayName);
                scenarioNode.put("description", scenario.description);
                scenarioNode.put("tableCount", scenario.tables.size());
                
                ArrayNode tables = objectMapper.createArrayNode();
                scenario.tables.forEach(tables::add);
                scenarioNode.set("tables", tables);
                
                scenarioNode.put("active", generator.isDemoScenarioActive(scenarioName));
                
                scenarios.add(scenarioNode);
            }
        }
        
        content.set("scenarios", scenarios);
        
        ObjectNode usage = objectMapper.createObjectNode();
        usage.put("setupCommand", "Use the 'setup_demo_scenario' tool with scenario parameter");
        usage.put("exampleUsage", "{\"scenario\": \"retail\", \"reset\": true}");
        usage.put("integration", "Demo scenarios work with 'mcp-demo' prompts and 'append_insight' tool");
        content.set("usage", usage);
        
        return content;
    }
    
    /**
     * Gets the content for demo scenario status resource
     */
    public JsonNode getDemoStatusContent() {
        ObjectNode status = objectMapper.createObjectNode();
        status.put("title", "Demo Scenario Status");
        status.put("databaseType", databaseType.toUpperCase());
        status.put("timestamp", java.time.LocalDateTime.now().toString());
        
        ArrayNode activeScenarios = objectMapper.createArrayNode();
        ArrayNode inactiveScenarios = objectMapper.createArrayNode();
        
        for (String scenarioName : DemoDataGenerator.getAvailableScenarios()) {
            DemoDataGenerator.DemoScenario scenario = DemoDataGenerator.getScenarioInfo(scenarioName);
            boolean isActive = generator.isDemoScenarioActive(scenarioName);
            
            ObjectNode scenarioStatus = objectMapper.createObjectNode();
            scenarioStatus.put("name", scenario.name);
            scenarioStatus.put("displayName", scenario.displayName);
            scenarioStatus.put("tableCount", scenario.tables.size());
            
            if (isActive) {
                activeScenarios.add(scenarioStatus);
            } else {
                inactiveScenarios.add(scenarioStatus);
            }
        }
        
        status.set("activeScenarios", activeScenarios);
        status.set("inactiveScenarios", inactiveScenarios);
        
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("totalScenarios", DemoDataGenerator.getAvailableScenarios().size());
        summary.put("activeCount", activeScenarios.size());
        summary.put("inactiveCount", inactiveScenarios.size());
        status.set("summary", summary);
        
        if (activeScenarios.size() > 0) {
            ObjectNode recommendations = objectMapper.createObjectNode();
            recommendations.put("suggestedPrompt", "Use 'mcp-demo' prompt to start guided analysis");
            recommendations.put("explorationTip", "Start with simple SELECT queries to understand the data structure");
            recommendations.put("insightCapture", "Use 'append_insight' tool to capture findings during analysis");
            status.set("recommendations", recommendations);
        }
        
        return status;
    }
    
    /**
     * Executes cleanup for a specific scenario
     */
    public JsonNode executeCleanupScenario(String scenario) {
        logger.info("Cleaning up demo scenario: {}", scenario);
        
        if (scenario == null || scenario.trim().isEmpty()) {
            return createErrorResponse("Missing required parameter: scenario");
        }
        
        String normalizedScenario = scenario.toLowerCase().trim();
        
        if (!DemoDataGenerator.getAvailableScenarios().contains(normalizedScenario)) {
            return createErrorResponse("Unknown demo scenario: " + scenario);
        }
        
        try {
            boolean success = generator.cleanupDemoScenario(normalizedScenario);
            
            if (success) {
                ObjectNode result = objectMapper.createObjectNode();
                result.put("success", true);
                result.put("scenario", normalizedScenario);
                result.put("message", "Successfully cleaned up demo scenario: " + normalizedScenario);
                
                logDemoEvent("DEMO_SCENARIO_CLEANUP", "Scenario: " + normalizedScenario);
                return result;
            } else {
                return createErrorResponse("Failed to clean up demo scenario: " + scenario);
            }
            
        } catch (Exception e) {
            logger.error("Error cleaning up demo scenario: {}", scenario, e);
            return createErrorResponse("Error cleaning up demo scenario: " + e.getMessage());
        }
    }
    
    // Helper methods
    
    private JsonNode createErrorResponse(String message) {
        ObjectNode error = objectMapper.createObjectNode();
        error.put("success", false);
        error.put("error", message);
        return error;
    }
    
    private void logDemoEvent(String event, String details) {
        Logger securityLogger = LoggerFactory.getLogger("SECURITY." + DemoDataService.class.getName());
        securityLogger.info("DEMO_EVENT: {} - {}", event, details);
    }
    
    public String getDatabaseType() {
        return databaseType;
    }
}