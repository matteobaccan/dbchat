package com.skanga.mcp.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for loading and managing externalized text resources.
 * Provides caching and formatting capabilities for database help content,
 * security warnings, and error messages.
 * 
 * <p>Template formatting failures are propagated as {@link IllegalArgumentException}
 * to ensure configuration issues are detected early rather than silently ignored.
 */
public class ResourceManager {
    private static final Logger logger = LoggerFactory.getLogger(ResourceManager.class);
    private static final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    
    // Cache configuration
    private static final int MAX_CACHE_SIZE = 50;
    private static final long CACHE_TTL_MINUTES = 60; // 1 hour TTL
    
    // Cache for loaded YAML resources with TTL support
    static final ConcurrentHashMap<String, CacheEntry> yamlCache = new ConcurrentHashMap<>();
    
    /**
     * Cache entry with TTL support
     */
    private static class CacheEntry {
        final Map<String, String> data;
        final Instant timestamp;
        
        CacheEntry(Map<String, String> data) {
            this.data = data;
            this.timestamp = Instant.now();
        }
        
        boolean isExpired() {
            return Instant.now().isAfter(timestamp.plusSeconds(CACHE_TTL_MINUTES * 60));
        }
    }
    
    /**
     * Gets database-specific help content (dialect guidance, examples, data types)
     */
    public static String getDatabaseHelp(String dbType, String helpType) {
        // Handle null dbType
        if (dbType == null) {
            return "null specific help not available. Use standard SQL syntax or check your own database specific documentation if available.";
        }

        // Handle null helpType
        if (helpType == null) {
            return dbType + " specific help not available. Use standard SQL syntax or check your own database specific documentation if available.";
        }

        String resourcePath = String.format("db/%s.yaml", dbType.toLowerCase());
        Map<String, String> dbHelp = loadYamlResource(resourcePath);
        return dbHelp.getOrDefault(helpType, dbType + " specific help not available. Use standard SQL syntax or check your own database specific documentation if available.");
    }
    
    /**
     * Gets formatted security warning with parameters
     * 
     * @param warningType The warning template key
     * @param paramsList Parameters for template formatting
     * @return Formatted security warning message
     * @throws IllegalArgumentException if template formatting fails
     */
    public static String getSecurityWarning(String warningType, Object... paramsList) {
        // Handle null warningType
        if (warningType == null) {
            return "Security warning template not found";
        }

        Map<String, String> securityTemplates = loadYamlResource("security-warning-template.yaml");
        String securityTemplate = securityTemplates.getOrDefault(warningType, "Security warning template not found");

        try {
            return MessageFormat.format(securityTemplate, paramsList);
        } catch (IllegalArgumentException e) {
            String errorMsg = String.format("Failed to format security warning template '%s' with %d parameters: %s", 
                    warningType, paramsList != null ? paramsList.length : 0, e.getMessage());
            logger.error(errorMsg, e);
            throw new IllegalArgumentException(errorMsg, e);
        }
    }
    
    /**
     * Gets error message with parameters
     * 
     * @param messageKey The error message template key
     * @param paramsList Parameters for template formatting
     * @return Formatted error message
     * @throws IllegalArgumentException if template formatting fails
     */
    public static String getErrorMessage(String messageKey, Object... paramsList) {
        // Handle null messageKey
        if (messageKey == null) {
            return "Error message not found: null";
        }

        Map<String, String> errorMessages = loadYamlResource("error-messages.yaml");
        String template = errorMessages.getOrDefault(messageKey, "Error message not found: " + messageKey);

        try {
            return MessageFormat.format(template, paramsList);
        } catch (IllegalArgumentException e) {
            String errorMsg = String.format("Failed to format error message '%s' with %d parameters: %s", 
                    messageKey, paramsList != null ? paramsList.length : 0, e.getMessage());
            logger.error(errorMsg, e);
            throw new IllegalArgumentException(errorMsg, e);
        }
    }
    
    
    /**
     * Loads YAML resource file with caching, TTL, and size limits
     */
    private static Map<String, String> loadYamlResource(String resourcePath) {
        // Handle null resourcePath
        if (resourcePath == null) {
            logger.warn("YAML resource path is null");
            return new HashMap<>(); // Return empty map
        }

        // Clean expired entries periodically
        cleanExpiredEntries();
        
        // Check if we have a valid cached entry
        CacheEntry cachedEntry = yamlCache.get(resourcePath);
        if (cachedEntry != null && !cachedEntry.isExpired()) {
            return cachedEntry.data;
        }
        
        // Load the resource
        Map<String, String> yamlMap = new HashMap<>();
        try (InputStream inputStream = ResourceManager.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                logger.warn("YAML resource file not found: {}", resourcePath);
                return yamlMap; // Return empty map
            }
            JsonNode rootNode = yamlMapper.readTree(inputStream);
            rootNode.fields().forEachRemaining(entry -> yamlMap.put(entry.getKey(), entry.getValue().asText()));
            logger.debug("Loaded YAML resource file: {} with {} entries", resourcePath, yamlMap.size());
        } catch (IOException e) {
            logger.error("Failed to load YAML resource file: {}", resourcePath, e);
            return yamlMap; // Return empty map on error
        }
        
        // Enforce cache size limit before adding new entry
        if (yamlCache.size() >= MAX_CACHE_SIZE) {
            evictOldestEntry();
        }
        
        // Cache the loaded data
        yamlCache.put(resourcePath, new CacheEntry(yamlMap));
        return yamlMap;
    }
    
    /**
     * Removes expired entries from the cache
     */
    private static void cleanExpiredEntries() {
        yamlCache.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }
    
    /**
     * Evicts the oldest entry from the cache when size limit is reached
     */
    private static void evictOldestEntry() {
        String oldestKey = null;
        Instant oldestTime = Instant.now();
        
        for (Map.Entry<String, CacheEntry> entry : yamlCache.entrySet()) {
            if (entry.getValue().timestamp.isBefore(oldestTime)) {
                oldestTime = entry.getValue().timestamp;
                oldestKey = entry.getKey();
            }
        }
        
        if (oldestKey != null) {
            yamlCache.remove(oldestKey);
            logger.debug("Evicted oldest cache entry: {}", oldestKey);
        }
    }
    
    /**
     * Database help content keys
     */
    public static class DatabaseHelp {
        public static final String DIALECT_GUIDANCE = "dialect.guidance";
        public static final String QUERY_EXAMPLES = "query.examples";
        public static final String DATATYPE_INFO = "datatype.info";
    }
    
    /**
     * Security warning keys
     */
    public static class SecurityWarnings {
        public static final String TOOL_RUN_SQL_DESCRIPTION = "tool.run_sql.description";
        public static final String RESULT_HEADER = "result.header";
        public static final String RESULT_FOOTER = "result.footer";
        public static final String RESOURCE_WRAPPER = "resource.wrapper";
    }
    
    /**
     * Error message keys
     */
    public static class ErrorMessages {
        public static final String CONFIG_ERROR_TITLE = "config.error.title";
        public static final String STARTUP_PORT_INUSE = "startup.port.inuse";
        public static final String STARTUP_SOLUTIONS = "startup.solutions";
    }
}