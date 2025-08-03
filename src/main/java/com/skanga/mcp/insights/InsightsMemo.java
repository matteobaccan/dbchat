package com.skanga.mcp.insights;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates comprehensive business intelligence memos from collected businessInsights.
 * Creates structured documents with executive summaries, categorized findings,
 * and actionable recommendations.
 */
public class InsightsMemo {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM dd, yyyy");
    
    private final List<BusinessInsight> businessInsights;
    private final String databaseType;
    private final LocalDateTime generationTime;
    
    public InsightsMemo(List<BusinessInsight> businessInsights, String databaseType) {
        this.businessInsights = new ArrayList<>(businessInsights);
        this.databaseType = databaseType;
        this.generationTime = LocalDateTime.now();
    }
    
    /**
     * Generates a comprehensive business intelligence memo
     */
    public String generateComprehensiveMemo() {
        if (businessInsights.isEmpty()) {
            return generateEmptyMemo();
        }
        
        StringBuilder comprehensiveMemo = new StringBuilder();
        
        // Header and metadata
        comprehensiveMemo.append(generateHeader());
        comprehensiveMemo.append(generateExecutiveSummary());
        comprehensiveMemo.append(generateInsightsByCategory());
        comprehensiveMemo.append(generatePriorityInsights());
        comprehensiveMemo.append(generateTimelineAnalysis());
        comprehensiveMemo.append(generateRecommendations());
        comprehensiveMemo.append(generateAppendix());
        comprehensiveMemo.append(generateFooter());
        
        return comprehensiveMemo.toString();
    }
    
    /**
     * Generates a simple memo format for basic display
     */
    public String generateSimpleMemo() {
        if (businessInsights.isEmpty()) {
            return "No business businessInsights have been discovered yet.";
        }
        
        StringBuilder memo = new StringBuilder();
        memo.append("Database Analysis Insights\n\n");
        memo.append("Generated: ").append(generationTime.format(TIMESTAMP_FORMAT)).append("\n");
        memo.append("Database Type: ").append(databaseType.toUpperCase()).append("\n\n");
        memo.append("Key Insights Discovered:\n\n");
        
        for (int i = 0; i < businessInsights.size(); i++) {
            BusinessInsight businessInsight = businessInsights.get(i);
            memo.append(String.format("%d. [%s] %s\n", 
                    i + 1, businessInsight.getDisplayCategory(), businessInsight.getContent()));
        }
        
        if (businessInsights.size() > 1) {
            memo.append("\nSummary:\n");
            memo.append(String.format("Analysis has revealed %d key business businessInsights across %d categories.",
                    businessInsights.size(), getCategoryCount()));
        }
        
        return memo.toString();
    }
    
    private String generateEmptyMemo() {
        return String.format("""
            DATABASE ANALYSIS INSIGHTS MEMO
            
            Generated: %s
            Database: %s
            Status: No businessInsights collected yet
            
            Getting Started:
            Use the append_insight tool to capture significant findings during your analysis.
            Insights will be automatically categorized and compiled into comprehensive reports.
            
            Example usage: append_insight("Customer retention rate is 85%% - above industry average")
            """, generationTime.format(TIMESTAMP_FORMAT), databaseType.toUpperCase());
    }
    
    private String generateHeader() {
        return String.format("""
            ═══════════════════════════════════════════════════════════════════════════════
            BUSINESS INTELLIGENCE ANALYSIS MEMO
            ═══════════════════════════════════════════════════════════════════════════════
            
            Report Date: %s
            Database System: %s
            Analysis Period: %s
            Total Insights: %d
            Categories Covered: %d
            
            ═══════════════════════════════════════════════════════════════════════════════
            
            """, 
            generationTime.format(DATE_FORMAT),
            databaseType.toUpperCase(),
            getAnalysisPeriod(),
            businessInsights.size(),
            getCategoryCount());
    }
    
    private String generateExecutiveSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("EXECUTIVE SUMMARY\n");
        summary.append("═══════════════════════════════════════════════════════════════════════════════\n\n");
        
        Map<String, List<BusinessInsight>> categorizedInsights = groupInsightsByCategory();
        List<BusinessInsight> highPriorityInsights = getHighPriorityInsights();
        
        summary.append(String.format("This analysis of the %s database has yielded %d actionable business businessInsights ",
                databaseType.toUpperCase(), businessInsights.size()));
        summary.append(String.format("across %d key business areas. ", getCategoryCount()));
        
        if (!highPriorityInsights.isEmpty()) {
            summary.append(String.format("%d businessInsights have been flagged as high priority and require immediate attention. ",
                    highPriorityInsights.size()));
        }
        
        // Highlight top categories
        String topCategories = categorizedInsights.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
                .limit(3)
                .map(e -> String.format("%s (%d businessInsights)",
                        BusinessInsight.create("", e.getKey()).getDisplayCategory(), e.getValue().size()))
                .collect(Collectors.joining(", "));
        
        summary.append(String.format("The primary areas of focus include: %s.\n\n", topCategories));
        
        // Key findings summary
        summary.append("KEY FINDINGS:\n");
        categorizedInsights.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
                .limit(5)
                .forEach(entry -> {
                    String categoryDisplay = BusinessInsight.create("", entry.getKey()).getDisplayCategory();
                    summary.append(String.format("• %s: %d businessInsights discovered\n", categoryDisplay, entry.getValue().size()));
                });
        
        summary.append("\n");
        return summary.toString();
    }
    
    private String generateInsightsByCategory() {
        StringBuilder section = new StringBuilder();
        section.append("DETAILED FINDINGS BY CATEGORY\n");
        section.append("═══════════════════════════════════════════════════════════════════════════════\n\n");
        
        Map<String, List<BusinessInsight>> categorizedInsights = groupInsightsByCategory();
        
        categorizedInsights.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
                .forEach(entry -> {
                    String category = entry.getKey();
                    List<BusinessInsight> categoryInsights = entry.getValue();
                    String categoryDisplay = BusinessInsight.create("", category).getDisplayCategory();
                    
                    section.append(String.format("## %s (%d businessInsights)\n", categoryDisplay, categoryInsights.size()));
                    section.append("─".repeat(80)).append("\n\n");
                    
                    categoryInsights.stream()
                            .sorted((i1, i2) -> Integer.compare(i2.getPriority(), i1.getPriority()))
                            .forEach(businessInsight -> {
                                section.append(String.format("* %s\n", businessInsight.getContent()));
                                section.append(String.format("   └─ Priority: %s | Time: %s\n\n", 
                                        businessInsight.getPriorityDisplay(), businessInsight.getFormattedTimestamp()));
                            });
                });
        
        return section.toString();
    }
    
    private String generatePriorityInsights() {
        List<BusinessInsight> highPriorityInsights = getHighPriorityInsights();
        
        if (highPriorityInsights.isEmpty()) {
            return "";
        }
        
        StringBuilder section = new StringBuilder();
        section.append("HIGH PRIORITY INSIGHTS\n");
        section.append("═══════════════════════════════════════════════════════════════════════════════\n\n");
        section.append("The following businessInsights require immediate attention:\n\n");
        
        highPriorityInsights.forEach(businessInsight -> {
            section.append(String.format("* %s\n", businessInsight.getContent()));
            section.append(String.format("   Category: %s | Discovered: %s\n\n", 
                    businessInsight.getDisplayCategory(), businessInsight.getFormattedTimestamp()));
        });
        
        return section.toString();
    }
    
    private String generateTimelineAnalysis() {
        if (businessInsights.size() < 2) {
            return "";
        }
        
        StringBuilder section = new StringBuilder();
        section.append("DISCOVERY TIMELINE\n");
        section.append("═══════════════════════════════════════════════════════════════════════════════\n\n");
        
        businessInsights.stream()
                .sorted(Comparator.comparing(BusinessInsight::getTimestamp))
                .forEach(businessInsight -> section.append(String.format("• %s - %s: %s\n",
                        businessInsight.getFormattedTimestamp(),
                        businessInsight.getDisplayCategory(),
                        businessInsight.getContent().length() > 60 ?
                                businessInsight.getContent().substring(0, 57) + "..." : businessInsight.getContent())));
        
        section.append("\n");
        return section.toString();
    }
    
    private String generateRecommendations() {
        StringBuilder section = new StringBuilder();
        section.append("STRATEGIC RECOMMENDATIONS\n");
        section.append("═══════════════════════════════════════════════════════════════════════════════\n\n");
        
        Map<String, List<BusinessInsight>> categorizedInsights = groupInsightsByCategory();
        
        section.append("Based on the analysis findings, we recommend the following actions:\n\n");
        
        categorizedInsights.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
                .limit(5)
                .forEach(entry -> {
                    String categoryDisplay = BusinessInsight.create("", entry.getKey()).getDisplayCategory();
                    section.append(String.format("%s Strategy:\n", categoryDisplay));
                    section.append(String.format("   • %d businessInsights suggest focused attention on this area\n", entry.getValue().size()));
                    section.append("   • Review detailed findings and develop targeted action plans\n");
                    section.append("   • Consider additional data collection for deeper analysis\n\n");
                });
        
        List<BusinessInsight> highPriorityInsights = getHighPriorityInsights();
        if (!highPriorityInsights.isEmpty()) {
            section.append("IMMEDIATE ACTIONS REQUIRED:\n");
            highPriorityInsights.forEach(businessInsight -> section.append(String.format("• Address: %s\n", businessInsight.getContent())));
            section.append("\n");
        }
        
        return section.toString();
    }
    
    private String generateAppendix() {
        StringBuilder section = new StringBuilder();
        section.append("APPENDIX\n");
        section.append("═══════════════════════════════════════════════════════════════════════════════\n\n");
        
        section.append("Analysis Metadata:\n");
        section.append(String.format("• Database Type: %s\n", databaseType.toUpperCase()));
        section.append(String.format("• Total Insights: %d\n", businessInsights.size()));
        section.append(String.format("• Categories: %d\n", getCategoryCount()));
        section.append(String.format("• High Priority: %d\n", getHighPriorityInsights().size()));
        section.append(String.format("• Analysis Period: %s\n", getAnalysisPeriod()));
        section.append(String.format("• Report Generated: %s\n\n", generationTime.format(TIMESTAMP_FORMAT)));
        
        // Category breakdown
        section.append("Category Breakdown:\n");
        Map<String, List<BusinessInsight>> categorizedInsights = groupInsightsByCategory();
        categorizedInsights.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(e2.getValue().size(), e1.getValue().size()))
                .forEach(entry -> {
                    String categoryDisplay = BusinessInsight.create("", entry.getKey()).getDisplayCategory();
                    section.append(String.format("• %s: %d businessInsights\n", categoryDisplay, entry.getValue().size()));
                });
        
        section.append("\n");
        return section.toString();
    }
    
    private String generateFooter() {
        return String.format("""
            ═══════════════════════════════════════════════════════════════════════════════
            Generated by DBChat MCP Server - Business Intelligence Analysis
            Report ID: MEMO-%s
            
            This memo was automatically generated from businessInsights collected during database
            analysis. All businessInsights should be validated with domain experts before
            implementing strategic decisions.
            ═══════════════════════════════════════════════════════════════════════════════
            """, generationTime.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
    }
    
    private Map<String, List<BusinessInsight>> groupInsightsByCategory() {
        return businessInsights.stream()
                .collect(Collectors.groupingBy(businessInsight ->
                        businessInsight.getCategory() != null ? businessInsight.getCategory() : "general"));
    }
    
    private List<BusinessInsight> getHighPriorityInsights() {
        return businessInsights.stream()
                .filter(BusinessInsight::isHighPriority)
                .sorted((i1, i2) -> Integer.compare(i2.getPriority(), i1.getPriority()))
                .collect(Collectors.toList());
    }
    
    private int getCategoryCount() {
        return (int) businessInsights.stream()
                .map(businessInsight -> businessInsight.getCategory() != null ? businessInsight.getCategory() : "general")
                .distinct()
                .count();
    }
    
    private String getAnalysisPeriod() {
        if (businessInsights.isEmpty()) {
            return "No data";
        }
        
        LocalDateTime earliest = businessInsights.stream()
                .map(BusinessInsight::getTimestamp)
                .min(LocalDateTime::compareTo)
                .orElse(generationTime);
        
        LocalDateTime latest = businessInsights.stream()
                .map(BusinessInsight::getTimestamp)
                .max(LocalDateTime::compareTo)
                .orElse(generationTime);
        
        if (earliest.equals(latest)) {
            return earliest.format(TIMESTAMP_FORMAT);
        }
        
        return String.format("%s to %s", 
                earliest.format(TIMESTAMP_FORMAT), 
                latest.format(TIMESTAMP_FORMAT));
    }
}