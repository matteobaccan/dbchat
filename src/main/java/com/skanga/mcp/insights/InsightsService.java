package com.skanga.mcp.insights;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.skanga.mcp.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Enhanced Insights Collection System supporting structured insights with categories,
 * priorities, persistence, and sophisticated memo generation.
 * Integrates with MCP resources for real-time updates and comprehensive reporting.
 */
public class InsightsService {
    private static final Logger logger = LoggerFactory.getLogger(InsightsService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final List<BusinessInsight> businessInsights = new CopyOnWriteArrayList<>();
    private final String databaseType;
    private final Path insightsFile;
    private volatile boolean autoSave;
    private final Map<String, Integer> categoryStats = new HashMap<>();
    
    public InsightsService(String databaseType) {
        this.databaseType = databaseType != null ? databaseType : "unknown";
        this.autoSave = true;
        this.insightsFile = Paths.get(System.getProperty("java.io.tmpdir"), 
                "dbchat-insights-" + this.databaseType.toLowerCase() + ".json");
        
        // Load existing insights if available
        loadInsightsFromFile();
        
        logger.info("InsightsService initialized for database type: {} (autosave: {})", 
                databaseType, autoSave);
    }
    
    /**
     * Adds a new insight with automatic categorization and security sanitization
     */
    public boolean addInsight(String content, String category) {
        if (content == null || content.trim().isEmpty()) {
            logger.warn("Attempted to add empty insight");
            return false;
        }
        
        // Sanitize content for security
        String sanitizedContent = SecurityUtils.sanitizeValue(content);
        
        // Normalize category
        String normalizedCategory = normalizeCategory(category);
        
        // Create structured insight
        BusinessInsight businessInsight = BusinessInsight.create(sanitizedContent, normalizedCategory);
        
        // Add to collection
        businessInsights.add(businessInsight);
        updateCategoryStats(normalizedCategory);
        
        logger.info("Added insight: category={}, content={}", normalizedCategory, 
                sanitizedContent.length() > 50 ? sanitizedContent.substring(0, 47) + "..." : sanitizedContent);
        
        // Auto-save if enabled
        if (autoSave) {
            saveInsightsToFile();
        }
        
        // Log security event for audit trail
        logSecurityEvent("INSIGHT_CAPTURED", String.format("Category: %s, Length: %d", 
                normalizedCategory, sanitizedContent.length()));
        
        return true;
    }
    
    /**
     * Adds a new insight with specified priority
     */
    public boolean addInsight(String content, String category, int priority) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }
        
        String sanitizedContent = SecurityUtils.sanitizeValue(content);
        String normalizedCategory = normalizeCategory(category);
        
        BusinessInsight businessInsight = BusinessInsight.create(sanitizedContent, normalizedCategory, priority);
        businessInsights.add(businessInsight);
        updateCategoryStats(normalizedCategory);
        
        logger.info("Added high priority insight: category={}, priority={}", normalizedCategory, priority);
        
        if (autoSave) {
            saveInsightsToFile();
        }
        
        return true;
    }
    
    /**
     * Legacy method for backward compatibility
     */
    public void addInsight(String insight) {
        addInsight(insight, "general");
    }
    
    /**
     * Generates a comprehensive business intelligence memo
     */
    public String generateMemo() {
        InsightsMemo memo = new InsightsMemo(businessInsights, databaseType);
        return memo.generateComprehensiveMemo();
    }
    
    /**
     * Generates a simple memo format
     */
    public String generateSimpleMemo() {
        InsightsMemo memo = new InsightsMemo(businessInsights, databaseType);
        return memo.generateSimpleMemo();
    }
    
    /**
     * Gets insights by category
     */
    public List<BusinessInsight> getInsightsByCategory(String category) {
        String normalizedCategory = normalizeCategory(category);
        return businessInsights.stream()
                .filter(businessInsight -> normalizedCategory.equals(businessInsight.getCategory()))
                .sorted((i1, i2) -> i2.getTimestamp().compareTo(i1.getTimestamp()))
                .collect(Collectors.toList());
    }
    
    /**
     * Gets high priority insights
     */
    public List<BusinessInsight> getHighPriorityInsights() {
        return businessInsights.stream()
                .filter(BusinessInsight::isHighPriority)
                .sorted((i1, i2) -> Integer.compare(i2.getPriority(), i1.getPriority()))
                .collect(Collectors.toList());
    }
    
    /**
     * Gets recent insights (last N)
     */
    public List<BusinessInsight> getRecentInsights(int limit) {
        return businessInsights.stream()
                .sorted((i1, i2) -> i2.getTimestamp().compareTo(i1.getTimestamp()))
                .limit(limit)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets all insights sorted by timestamp (newest first)
     */
    public List<BusinessInsight> getAllInsights() {
        return businessInsights.stream()
                .sorted((i1, i2) -> i2.getTimestamp().compareTo(i1.getTimestamp()))
                .collect(Collectors.toList());
    }
    
    /**
     * Gets category statistics
     */
    public Map<String, Integer> getCategoryStats() {
        return new HashMap<>(categoryStats);
    }
    
    /**
     * Gets available categories
     */
    public Set<String> getCategories() {
        return businessInsights.stream()
                .map(BusinessInsight::getCategory)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
    
    /**
     * Clears all insights (with confirmation)
     */
    public boolean clearInsights() {
        if (businessInsights.isEmpty()) {
            return false;
        }
        
        int count = businessInsights.size();
        businessInsights.clear();
        categoryStats.clear();
        
        if (autoSave) {
            saveInsightsToFile();
        }
        
        logger.warn("Cleared {} insights from collection", count);
        logSecurityEvent("INSIGHTS_CLEARED", String.format("Count: %d", count));
        
        return true;
    }
    
    /**
     * Exports insights as JSON for external processing
     */
    public String exportInsightsAsJson() {
        try {
            ObjectNode export = objectMapper.createObjectNode();
            export.put("databaseType", databaseType);
            export.put("exportTime", LocalDateTime.now().toString());
            export.put("totalInsights", businessInsights.size());
            
            ArrayNode insightsArray = objectMapper.createArrayNode();
            for (BusinessInsight businessInsight : businessInsights) {
                insightsArray.add(objectMapper.readTree(businessInsight.toJson()));
            }
            export.set("insights", insightsArray);
            
            ObjectNode stats = objectMapper.createObjectNode();
            categoryStats.forEach(stats::put);
            export.set("categoryStats", stats);
            
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(export);
        } catch (Exception e) {
            logger.error("Failed to export insights as JSON", e);
            return "{}";
        }
    }
    
    /**
     * Creates MCP resource for insights memo
     */
    public JsonNode createMemoResource() {
        ObjectNode resource = objectMapper.createObjectNode();
        resource.put("uri", "insights://memo");
        resource.put("name", "Business Insights Memo");
        resource.put("description", String.format("Comprehensive analysis memo with %d insights across %d categories", 
                businessInsights.size(), getCategories().size()));
        resource.put("mimeType", "text/plain");
        
        // Add metadata
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("insightCount", businessInsights.size());
        metadata.put("categoryCount", getCategories().size());
        metadata.put("highPriorityCount", getHighPriorityInsights().size());
        metadata.put("databaseType", databaseType);
        metadata.put("lastUpdated", LocalDateTime.now().toString());
        resource.set("metadata", metadata);
        
        return resource;
    }
    
    /**
     * Creates MCP resource for insights summary
     */
    public JsonNode createSummaryResource() {
        ObjectNode resource = objectMapper.createObjectNode();
        resource.put("uri", "insights://summary");
        resource.put("name", "Insights Summary");
        resource.put("description", String.format("Quick summary of %d collected insights", businessInsights.size()));
        resource.put("mimeType", "text/plain");
        return resource;
    }
    
    // Legacy methods for backward compatibility
    public int getInsightCount() {
        return businessInsights.size();
    }
    
    public boolean hasInsights() {
        return !businessInsights.isEmpty();
    }
    
    // Private helper methods
    
    private String normalizeCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return "general";
        }
        return category.trim().toLowerCase();
    }
    
    private void updateCategoryStats(String category) {
        categoryStats.merge(category, 1, Integer::sum);
    }
    
    private void loadInsightsFromFile() {
        if (!Files.exists(insightsFile)) {
            logger.debug("No existing insights file found");
            return;
        }
        
        try {
            String content = Files.readString(insightsFile);
            JsonNode root = objectMapper.readTree(content);
            
            if (root.has("insights") && root.get("insights").isArray()) {
                ArrayNode insightsArray = (ArrayNode) root.get("insights");
                for (JsonNode insightNode : insightsArray) {
                    try {
                        BusinessInsight businessInsight = BusinessInsight.fromJson(insightNode.toString());
                        businessInsights.add(businessInsight);
                        updateCategoryStats(businessInsight.getCategory());
                    } catch (Exception e) {
                        logger.warn("Failed to load insight: {}", e.getMessage());
                    }
                }
                logger.info("Loaded {} insights from file", businessInsights.size());
            }
        } catch (IOException e) {
            logger.warn("Failed to load insights from file: {}", e.getMessage());
        }
    }
    
    private void saveInsightsToFile() {
        try {
            String json = exportInsightsAsJson();
            Files.writeString(insightsFile, json, 
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            logger.debug("Saved {} insights to file", businessInsights.size());
        } catch (IOException e) {
            logger.error("Failed to save insights to file: {}", e.getMessage());
        }
    }
    
    private void logSecurityEvent(String event, String details) {
        Logger securityLogger = LoggerFactory.getLogger("SECURITY." + InsightsService.class.getName());
        securityLogger.info("SECURITY_EVENT: {} - {}", event, details);
    }
    
    public String getDatabaseType() {
        return databaseType;
    }
    
    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
        logger.debug("Auto-save set to: {}", autoSave);
    }
    
    public boolean isAutoSave() {
        return autoSave;
    }
}