package com.skanga.mcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for loading and managing externalized text resources.
 * Provides caching and formatting capabilities for database help content,
 * security warnings, and error messages.
 */
public class ResourceManager {
    private static final Logger logger = LoggerFactory.getLogger(ResourceManager.class);
    
    // Cache for loaded properties
    static final ConcurrentHashMap<String, Properties> propertiesCache = new ConcurrentHashMap<>();
    
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

        String resourcePath = String.format("db/%s.properties", dbType.toLowerCase());
        Properties dbProps = loadProperties(resourcePath);
        return dbProps.getProperty(helpType, dbType + " specific help not available. Use standard SQL syntax or check your own database specific documentation if available.");
    }
    
    /**
     * Gets formatted security warning with parameters
     */
    public static String getSecurityWarning(String warningType, Object... params) {
        // Handle null warningType
        if (warningType == null) {
            return "Security warning template not found";
        }

        Properties securityProps = loadProperties("security-warning-template.properties");
        String securityTemplate = securityProps.getProperty(warningType, "Security warning template not found");

        try {
            return MessageFormat.format(securityTemplate, params);
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to format security warning template '{}' with {} parameters: {}",
                       warningType, params != null ? params.length : 0, e.getMessage());
            return securityTemplate; // Return unformatted template if formatting fails
        }
    }
    
    /**
     * Gets error message with parameters
     */
    public static String getErrorMessage(String messageKey, Object... params) {
        // Handle null messageKey
        if (messageKey == null) {
            return "Error message not found: null";
        }

        Properties props = loadProperties("error-messages.properties");
        String template = props.getProperty(messageKey, "Error message not found: " + messageKey);

        try {
            return MessageFormat.format(template, params);
        } catch (IllegalArgumentException e) {
            logger.warn("Failed to format error message '{}' with {} parameters: {}",
                       messageKey, params != null ? params.length : 0, e.getMessage());
            return template; // Return unformatted template if formatting fails
        }
    }
    
    /**
     * Loads properties file with caching
     */
    private static Properties loadProperties(String resourcePath) {
        // Handle null resourcePath
        if (resourcePath == null) {
            logger.warn("Resource path is null");
            return new Properties(); // Return empty properties
        }

        return propertiesCache.computeIfAbsent(resourcePath, path -> {
            Properties props = new Properties();
            try (InputStream inputStream = ResourceManager.class.getClassLoader().getResourceAsStream(path)) {
                if (inputStream == null) {
                    logger.warn("Resource file not found: {}", path);
                    return props; // Return empty properties
                }
                props.load(inputStream);
                logger.debug("Loaded resource file: {}", path);
            } catch (IOException e) {
                logger.error("Failed to load resource file: {}", path, e);
            }
            return props;
        });
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
        public static final String TOOL_QUERY_DESCRIPTION = "tool.query.description";
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