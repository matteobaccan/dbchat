package com.skanga.mcp.demo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessScenarioTest {

    @Test
    @DisplayName("Should create retail scenario with all required fields")
    void testCreateRetailScenario() {
        BusinessScenario scenario = BusinessScenario.createRetailScenario();

        assertThat(scenario.name()).isEqualTo("retail-analysis");
        assertThat(scenario.description()).isEqualTo("E-commerce store performance analysis");
        assertThat(scenario.businessContext()).contains("TechnoMart");
        assertThat(scenario.businessContext()).contains("online electronics retailer");
        
        // Check protagonists
        assertThat(scenario.protagonists()).hasSize(3);
        assertThat(scenario.protagonists()).contains("Sarah Chen (CEO)");
        assertThat(scenario.protagonists()).contains("Mike Rodriguez (Sales Director)");
        assertThat(scenario.protagonists()).contains("Jennifer Kim (Marketing Manager)");
        
        assertThat(scenario.deadline()).contains("Board meeting on Friday");
        
        // Check table definitions
        assertThat(scenario.tableDefinitions()).hasSize(3);
        assertThat(scenario.tableDefinitions()).containsKey("customers");
        assertThat(scenario.tableDefinitions()).containsKey("products");
        assertThat(scenario.tableDefinitions()).containsKey("orders");
        
        // Check analysis steps
        assertThat(scenario.analysisSteps()).hasSize(3);
        assertThat(scenario.analysisSteps().get(0).title()).isEqualTo("Customer Base Overview");
        assertThat(scenario.analysisSteps().get(1).title()).isEqualTo("Sales Performance Analysis");
        assertThat(scenario.analysisSteps().get(2).title()).isEqualTo("Product Performance Deep Dive");
        
        assertThat(scenario.expectedInsight()).contains("TechnoMart shows strong growth potential");
    }

    @Test
    @DisplayName("Should create finance scenario with all required fields")
    void testCreateFinanceScenario() {
        BusinessScenario scenario = BusinessScenario.createFinanceScenario();

        assertThat(scenario.name()).isEqualTo("finance-analysis");
        assertThat(scenario.description()).isEqualTo("Banking customer behavior and risk analysis");
        assertThat(scenario.businessContext()).contains("SecureBank");
        assertThat(scenario.businessContext()).contains("regional bank");
        
        // Check protagonists
        assertThat(scenario.protagonists()).hasSize(3);
        assertThat(scenario.protagonists()).contains("David Park (Risk Manager)");
        assertThat(scenario.protagonists()).contains("Lisa Thompson (Customer Analytics)");
        assertThat(scenario.protagonists()).contains("Robert Chen (Branch Manager)");
        
        assertThat(scenario.deadline()).contains("Regulatory review next Monday");
        
        // Check table definitions
        assertThat(scenario.tableDefinitions()).hasSize(3);
        assertThat(scenario.tableDefinitions()).containsKey("customers");
        assertThat(scenario.tableDefinitions()).containsKey("accounts");
        assertThat(scenario.tableDefinitions()).containsKey("transactions");
        
        // Check analysis steps
        assertThat(scenario.analysisSteps()).hasSize(3);
        assertThat(scenario.analysisSteps().get(0).title()).isEqualTo("Account Portfolio Overview");
        assertThat(scenario.analysisSteps().get(1).title()).isEqualTo("Transaction Pattern Analysis");
        assertThat(scenario.analysisSteps().get(2).title()).isEqualTo("Customer Behavior Insights");
        
        assertThat(scenario.expectedInsight()).contains("SecureBank has a diverse customer base");
    }

    @Test
    @DisplayName("Analysis steps should have multiple choice options")
    void testAnalysisStepsHaveMultipleChoice() {
        BusinessScenario scenario = BusinessScenario.createRetailScenario();
        
        for (BusinessScenario.AnalysisStep step : scenario.analysisSteps()) {
            assertThat(step.title()).isNotNull().isNotEmpty();
            assertThat(step.description()).isNotNull().isNotEmpty();
            assertThat(step.multipleChoiceOptions()).hasSize(3);
            assertThat(step.expectedResult()).isNotNull().isNotEmpty();
            
            // Each option should start with A), B), or C)
            assertThat(step.multipleChoiceOptions().get(0)).startsWith("A)");
            assertThat(step.multipleChoiceOptions().get(1)).startsWith("B)");
            assertThat(step.multipleChoiceOptions().get(2)).startsWith("C)");
        }
    }

    @Test
    @DisplayName("Analysis steps should have suggested queries")
    void testAnalysisStepsHaveSuggestedQueries() {
        BusinessScenario retailScenario = BusinessScenario.createRetailScenario();
        BusinessScenario financeScenario = BusinessScenario.createFinanceScenario();
        
        // Check retail scenario queries
        for (BusinessScenario.AnalysisStep step : retailScenario.analysisSteps()) {
            if (step.suggestedQuery() != null) {
                assertThat(step.suggestedQuery()).isNotEmpty();
                assertThat(step.suggestedQuery().toUpperCase()).contains("SELECT");
            }
        }
        
        // Check finance scenario queries
        for (BusinessScenario.AnalysisStep step : financeScenario.analysisSteps()) {
            if (step.suggestedQuery() != null) {
                assertThat(step.suggestedQuery()).isNotEmpty();
                assertThat(step.suggestedQuery().toUpperCase()).contains("SELECT");
            }
        }
    }

    @Test
    @DisplayName("Table definitions should be meaningful")
    void testTableDefinitionsAreMeaningful() {
        BusinessScenario retailScenario = BusinessScenario.createRetailScenario();
        BusinessScenario financeScenario = BusinessScenario.createFinanceScenario();
        
        // Retail scenario table definitions
        assertThat(retailScenario.tableDefinitions().get("customers")).contains("Customer information");
        assertThat(retailScenario.tableDefinitions().get("products")).contains("Product catalog");
        assertThat(retailScenario.tableDefinitions().get("orders")).contains("Order history");
        
        // Finance scenario table definitions
        assertThat(financeScenario.tableDefinitions().get("customers")).contains("Customer profiles");
        assertThat(financeScenario.tableDefinitions().get("accounts")).contains("Account details");
        assertThat(financeScenario.tableDefinitions().get("transactions")).contains("Transaction history");
    }

    @Test
    @DisplayName("Business context should be comprehensive")
    void testBusinessContextIsComprehensive() {
        BusinessScenario retailScenario = BusinessScenario.createRetailScenario();
        BusinessScenario financeScenario = BusinessScenario.createFinanceScenario();
        
        // Retail context should mention key business elements
        String retailContext = retailScenario.businessContext();
        assertThat(retailContext).contains("TechnoMart");
        assertThat(retailContext).contains("2 years");
        assertThat(retailContext).contains("insights");
        assertThat(retailContext).contains("next quarter");
        
        // Finance context should mention key business elements
        String financeContext = financeScenario.businessContext();
        assertThat(financeContext).contains("SecureBank");
        assertThat(financeContext).contains("customer transaction patterns");
        assertThat(financeContext).contains("risks");
        assertThat(financeContext).contains("opportunities");
    }

    @Test
    @DisplayName("Expected insights should be realistic and actionable")
    void testExpectedInsights() {
        BusinessScenario retailScenario = BusinessScenario.createRetailScenario();
        BusinessScenario financeScenario = BusinessScenario.createFinanceScenario();
        
        // Retail insight
        String retailInsight = retailScenario.expectedInsight();
        assertThat(retailInsight).contains("growth potential");
        assertThat(retailInsight).contains("customer acquisition");
        assertThat(retailInsight).contains("inventory");
        assertThat(retailInsight).contains("marketing decisions");
        
        // Finance insight
        String financeInsight = financeScenario.expectedInsight();
        assertThat(financeInsight).contains("diverse customer base");
        assertThat(financeInsight).contains("engagement levels");
        assertThat(financeInsight).contains("targeted financial products");
        assertThat(financeInsight).contains("customer segments");
    }
}