package com.skanga.mcp.insights;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Represents a structured business insight captured during database analysis.
 * Each insight includes content, category, timestamp, and optional metadata.
 */
public class BusinessInsight {
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final String content;
    private final String category;
    private final LocalDateTime timestamp;
    private final String source;
    private final int priority;
    
    @JsonCreator
    public BusinessInsight(
            @JsonProperty("content") String content,
            @JsonProperty("category") String category,
            @JsonProperty("timestamp") LocalDateTime timestamp,
            @JsonProperty("source") String source,
            @JsonProperty("priority") int priority) {
        this.content = content;
        this.category = category;
        this.timestamp = timestamp != null ? timestamp : LocalDateTime.now();
        this.source = source;
        this.priority = priority;
    }
    
    /**
     * Creates a new insight with current timestamp and default values
     */
    public static BusinessInsight create(String content, String category) {
        return new BusinessInsight(content, category, LocalDateTime.now(), "user", 1);
    }
    
    /**
     * Creates a new insight with specified priority
     */
    public static BusinessInsight create(String content, String category, int priority) {
        return new BusinessInsight(content, category, LocalDateTime.now(), "user", priority);
    }
    
    /**
     * Creates a new insight with specified source
     */
    public static BusinessInsight create(String content, String category, String source) {
        return new BusinessInsight(content, category, LocalDateTime.now(), source, 1);
    }
    
    public String getContent() { return content; }
    public String getCategory() { return category; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getSource() { return source; }
    public int getPriority() { return priority; }
    
    /**
     * Gets formatted timestamp as string
     */
    public String getFormattedTimestamp() {
        return timestamp.format(TIMESTAMP_FORMAT);
    }
    
    /**
     * Converts insight to JSON string for storage
     */
    public String toJson() {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize insight to JSON", e);
        }
    }
    
    /**
     * Creates insight from JSON string
     */
    public static BusinessInsight fromJson(String json) {
        try {
            return objectMapper.readValue(json, BusinessInsight.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize insight from JSON", e);
        }
    }
    
    /**
     * Gets display category with emoji and formatting
     */
    public String getDisplayCategory() {
        if (category == null || category.trim().isEmpty()) {
            return "General";
        }
        
        return switch (category.toLowerCase()) {
            case "sales" -> "Sales";
            case "customers" -> "Customers";
            case "products" -> "Products";
            case "inventory" -> "Inventory";
            case "finance" -> "Finance";
            case "performance" -> "Performance";
            case "quality" -> "Quality";
            case "trends" -> "Trends";
            case "risk" -> "Risk";
            case "opportunity" -> "Opportunity";
            case "operations" -> "Operations";
            case "marketing" -> "Marketing";
            default -> capitalize(category);
        };
    }
    
    /**
     * Gets priority display with appropriate emoji
     */
    public String getPriorityDisplay() {
        return switch (priority) {
            case 3 -> "High";
            case 2 -> "Medium";
            case 1 -> "Low";
            default -> "Normal";
        };
    }
    
    /**
     * Checks if this insight is considered high priority
     */
    public boolean isHighPriority() {
        return priority >= 3;
    }
    
    /**
     * Gets a formatted summary line for the insight
     */
    public String getSummaryLine() {
        return String.format("[%s] %s - %s", 
                getFormattedTimestamp(), 
                getDisplayCategory(), 
                content.length() > 80 ? content.substring(0, 77) + "..." : content);
    }
    
    /**
     * Gets a detailed formatted display of the insight
     */
    public String getDetailedDisplay() {
        return ("┌─ %s (%s)\n" +
                "├─ Timestamp: %s\n" +
                "├─ Source: %s\n" +
                "└─ Content: %s\n").formatted(getDisplayCategory(), getPriorityDisplay(), getFormattedTimestamp(), source != null ? source : "unknown", content);
    }
    
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BusinessInsight businessInsight = (BusinessInsight) o;
        return priority == businessInsight.priority &&
                Objects.equals(content, businessInsight.content) &&
                Objects.equals(category, businessInsight.category) &&
                Objects.equals(timestamp, businessInsight.timestamp) &&
                Objects.equals(source, businessInsight.source);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(content, category, timestamp, source, priority);
    }
    
    @Override
    public String toString() {
        return String.format("Insight{category='%s', priority=%d, content='%s', timestamp=%s}", 
                category, priority, content, getFormattedTimestamp());
    }
}