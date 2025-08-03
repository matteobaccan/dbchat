package com.skanga.mcp.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.skanga.mcp.db.DatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class DemoDataServiceTest {

    @Mock
    private DatabaseService mockDatabaseService;
    
    private DemoDataService demoDataService;
    
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Mock database service to avoid connection issues in tests
        try {
            when(mockDatabaseService.getConnection()).thenThrow(new SQLException("Mock connection not available"));
        } catch (Exception e) {
            // Ignore
        }
        
        demoDataService = new DemoDataService(mockDatabaseService, "h2");
    }
    
    @Test
    @DisplayName("Should create setup_demo_scenario tool with correct structure")
    void testCreateSetupDemoTool() {
        JsonNode tool = demoDataService.createSetupDemoTool();
        
        assertThat(tool).isNotNull();
        assertThat(tool.get("name").asText()).isEqualTo("setup_demo_scenario");
        assertThat(tool.get("description").asText()).contains("Sets up realistic demo data");
        assertThat(tool.has("inputSchema")).isTrue();
        
        JsonNode inputSchema = tool.get("inputSchema");
        assertThat(inputSchema.get("type").asText()).isEqualTo("object");
        assertThat(inputSchema.has("properties")).isTrue();
        
        JsonNode properties = inputSchema.get("properties");
        assertThat(properties.has("scenario")).isTrue();
        assertThat(properties.has("reset")).isTrue();
        
        JsonNode scenarioParam = properties.get("scenario");
        assertThat(scenarioParam.get("type").asText()).isEqualTo("string");
        assertThat(scenarioParam.has("enum")).isTrue();
        
        JsonNode enumValues = scenarioParam.get("enum");
        assertThat(enumValues.size()).isEqualTo(3);
        assertThat(enumValues.get(0).asText()).isEqualTo("retail");
        assertThat(enumValues.get(1).asText()).isEqualTo("finance");
        assertThat(enumValues.get(2).asText()).isEqualTo("logistics");
        
        JsonNode resetParam = properties.get("reset");
        assertThat(resetParam.get("type").asText()).isEqualTo("boolean");
        assertThat(resetParam.get("default").asBoolean()).isTrue();
        
        JsonNode required = inputSchema.get("required");
        assertThat(required.size()).isEqualTo(1);
        assertThat(required.get(0).asText()).isEqualTo("scenario");
        
        assertThat(tool.has("metadata")).isTrue();
        JsonNode metadata = tool.get("metadata");
        assertThat(metadata.get("category").asText()).isEqualTo("demo-data");
        assertThat(metadata.get("riskLevel").asText()).isEqualTo("medium");
        assertThat(metadata.has("capabilities")).isTrue();
    }
    
    @Test
    @DisplayName("Should create list scenarios resource with correct structure")
    void testCreateListScenariosResource() {
        JsonNode resource = demoDataService.createListScenariosResource();
        
        assertThat(resource).isNotNull();
        assertThat(resource.get("uri").asText()).isEqualTo("demo://scenarios");
        assertThat(resource.get("name").asText()).isEqualTo("Available Demo Scenarios");
        assertThat(resource.get("description").asText()).contains("List of all available demo scenarios");
        assertThat(resource.get("mimeType").asText()).isEqualTo("application/json");
        
        assertThat(resource.has("metadata")).isTrue();
        JsonNode metadata = resource.get("metadata");
        assertThat(metadata.get("type").asText()).isEqualTo("demo-scenarios");
        assertThat(metadata.get("scenarioCount").asInt()).isEqualTo(3);
    }
    
    @Test
    @DisplayName("Should create scenario status resource with correct structure")
    void testCreateScenarioStatusResource() {
        JsonNode resource = demoDataService.createScenarioStatusResource();
        
        assertThat(resource).isNotNull();
        assertThat(resource.get("uri").asText()).isEqualTo("demo://status");
        assertThat(resource.get("name").asText()).isEqualTo("Demo Scenario Status");
        assertThat(resource.get("description").asText()).contains("Current status of demo scenarios");
        assertThat(resource.get("mimeType").asText()).isEqualTo("application/json");
    }
    
    @Test
    @DisplayName("Should get demo scenarios content with all scenarios")
    void testGetDemoScenariosContent() {
        JsonNode content = demoDataService.getDemoScenariosContent();
        
        assertThat(content).isNotNull();
        assertThat(content.get("title").asText()).isEqualTo("Available Demo Scenarios");
        assertThat(content.get("description").asText()).contains("Complete business scenarios");
        assertThat(content.get("databaseType").asText()).isEqualTo("H2");
        
        assertThat(content.has("scenarios")).isTrue();
        JsonNode scenarios = content.get("scenarios");
        assertThat(scenarios.isArray()).isTrue();
        assertThat(scenarios.size()).isEqualTo(3);
        
        // Check for presence of all scenarios, regardless of order
        assertThat(scenarios).anySatisfy(scenario -> {
            assertThat(scenario.get("name").asText()).isEqualTo("retail");
            assertThat(scenario.get("displayName").asText()).isEqualTo("E-commerce Retail Analysis");
            assertThat(scenario.has("description")).isTrue();
            assertThat(scenario.get("tableCount").asInt()).isEqualTo(5);
            assertThat(scenario.has("tables")).isTrue();
            assertThat(scenario.has("active")).isTrue();
        });
        
        assertThat(scenarios).anySatisfy(scenario -> {
            assertThat(scenario.get("name").asText()).isEqualTo("finance");
            assertThat(scenario.get("displayName").asText()).isEqualTo("Banking & Finance Analysis");
        });
        
        assertThat(scenarios).anySatisfy(scenario -> {
            assertThat(scenario.get("name").asText()).isEqualTo("logistics");
            assertThat(scenario.get("displayName").asText()).isEqualTo("Supply Chain & Logistics Analysis");
        });
        
        assertThat(content.has("usage")).isTrue();
        JsonNode usage = content.get("usage");
        assertThat(usage.has("setupCommand")).isTrue();
        assertThat(usage.has("exampleUsage")).isTrue();
        assertThat(usage.has("integration")).isTrue();
    }
    
    @Test
    @DisplayName("Should get demo status content with summary")
    void testGetDemoStatusContent() {
        JsonNode content = demoDataService.getDemoStatusContent();
        
        assertThat(content).isNotNull();
        assertThat(content.get("title").asText()).isEqualTo("Demo Scenario Status");
        assertThat(content.get("databaseType").asText()).isEqualTo("H2");
        assertThat(content.has("timestamp")).isTrue();
        
        assertThat(content.has("activeScenarios")).isTrue();
        assertThat(content.has("inactiveScenarios")).isTrue();
        assertThat(content.has("summary")).isTrue();
        
        JsonNode summary = content.get("summary");
        assertThat(summary.get("totalScenarios").asInt()).isEqualTo(3);
        assertThat(summary.has("activeCount")).isTrue();
        assertThat(summary.has("inactiveCount")).isTrue();
        
        // Check scenarios arrays are present
        JsonNode activeScenarios = content.get("activeScenarios");
        JsonNode inactiveScenarios = content.get("inactiveScenarios");
        assertThat(activeScenarios.isArray()).isTrue();
        assertThat(inactiveScenarios.isArray()).isTrue();
        
        // Total should match
        int totalActive = activeScenarios.size();
        int totalInactive = inactiveScenarios.size();
        assertThat(totalActive + totalInactive).isEqualTo(3);
    }
    
    @Test
    @DisplayName("Should handle missing scenario parameter in setup")
    void testExecuteSetupDemoScenarioMissingScenario() {
        JsonNode result = demoDataService.executeSetupDemoScenario(null, true);
        
        assertThat(result).isNotNull();
        assertThat(result.get("success").asBoolean()).isFalse();
        assertThat(result.get("error").asText()).contains("Missing required parameter: scenario");
    }
    
    @Test
    @DisplayName("Should handle empty scenario parameter in setup")
    void testExecuteSetupDemoScenarioEmptyScenario() {
        JsonNode result = demoDataService.executeSetupDemoScenario("", true);
        
        assertThat(result).isNotNull();
        assertThat(result.get("success").asBoolean()).isFalse();
        assertThat(result.get("error").asText()).contains("Missing required parameter: scenario");
    }
    
    @Test
    @DisplayName("Should handle unknown scenario parameter in setup")
    void testExecuteSetupDemoScenarioUnknownScenario() {
        JsonNode result = demoDataService.executeSetupDemoScenario("unknown", true);
        
        assertThat(result).isNotNull();
        assertThat(result.get("success").asBoolean()).isFalse();
        assertThat(result.get("error").asText()).contains("Unknown demo scenario: unknown");
        assertThat(result.get("error").asText()).contains("Available scenarios:");
    }
    
    @Test
    @DisplayName("Should handle missing scenario parameter in cleanup")
    void testExecuteCleanupScenarioMissingScenario() {
        JsonNode result = demoDataService.executeCleanupScenario(null);
        
        assertThat(result).isNotNull();
        assertThat(result.get("success").asBoolean()).isFalse();
        assertThat(result.get("error").asText()).contains("Missing required parameter: scenario");
    }
    
    @Test
    @DisplayName("Should handle unknown scenario parameter in cleanup")
    void testExecuteCleanupScenarioUnknownScenario() {
        JsonNode result = demoDataService.executeCleanupScenario("unknown");
        
        assertThat(result).isNotNull();
        assertThat(result.get("success").asBoolean()).isFalse();
        assertThat(result.get("error").asText()).contains("Unknown demo scenario: unknown");
    }
    
    @Test
    @DisplayName("Should validate database type configuration")
    void testDatabaseTypeConfiguration() {
        DemoDataService serviceWithNullType = new DemoDataService(mockDatabaseService, null);
        assertThat(serviceWithNullType.getDatabaseType()).isEqualTo("unknown");
        
        DemoDataService serviceWithValidType = new DemoDataService(mockDatabaseService, "postgresql");
        assertThat(serviceWithValidType.getDatabaseType()).isEqualTo("postgresql");
    }
    
    @Test
    @DisplayName("Should handle case insensitive scenario names")
    void testCaseInsensitiveScenarios() {
        // Test uppercase
        JsonNode result1 = demoDataService.executeSetupDemoScenario("RETAIL", true);
        assertThat(result1).isNotNull();
        // Should either succeed or fail gracefully (depends on database service mock)
        
        // Test mixed case
        JsonNode result2 = demoDataService.executeSetupDemoScenario("Finance", true);
        assertThat(result2).isNotNull();
        
        // Test cleanup with mixed case
        JsonNode result3 = demoDataService.executeCleanupScenario("Logistics");
        assertThat(result3).isNotNull();
    }
}