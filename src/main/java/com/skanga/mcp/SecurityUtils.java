package com.skanga.mcp;

import java.util.regex.Pattern;

/**
 * Utility class for security-related operations including content sanitization and validation.
 * Provides centralized methods for detecting and handling potentially malicious content
 * in database values, identifiers, and other user-supplied data.
 */
public final class SecurityUtils {
    
    // Pre-compiled regex patterns for performance
    // Optimized: Removed leading/trailing .* to prevent backtracking, uses word boundaries for precision
    private static final Pattern INSTRUCTION_PATTERN = Pattern.compile("\\b(?:act as|pretend to be|you are now)\\b");
    
    private SecurityUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Sanitizes individual values to prevent injection and mark potentially dangerous content.
     * Detects instruction-like patterns and excessively long content that might hide malicious instructions.
     *
     * @param inputValue The value to sanitize (can be null)
     * @return Sanitized string with security markers if suspicious content is detected
     */
    public static String sanitizeValue(Object inputValue) {
        if (inputValue == null) {
            return "NULL";
        }

        String stringValue = inputValue.toString();

        // Detect and mark potentially dangerous content
        String lowerValue = stringValue.toLowerCase().trim();

        // Check for instruction-like patterns (optimized for performance)
        boolean containsInstructions =
                lowerValue.startsWith("ignore") ||
                lowerValue.startsWith("forget") ||
                lowerValue.startsWith("system:") ||
                lowerValue.startsWith("assistant:") ||
                lowerValue.startsWith("user:") ||
                lowerValue.contains("</instructions>") ||
                lowerValue.contains("<instructions>") ||
                lowerValue.contains("prompt:") ||
                lowerValue.contains("execute") ||
                lowerValue.contains("run the following") ||
                lowerValue.contains("new instructions") ||
                lowerValue.contains("override") ||
                lowerValue.contains("jailbreak") ||
                lowerValue.contains("roleplay") ||
                INSTRUCTION_PATTERN.matcher(lowerValue).find(); // Optimized regex usage

        // Mark suspicious content
        if (containsInstructions) {
            return "[FLAGGED CONTENT]: " + truncateString(stringValue, 100);
        }

        // Check for excessively long content that might hide instructions
        if (stringValue.length() > 500) {
            return "[LONG CONTENT]: " + truncateString(stringValue, 200);
        }

        // Return original for normal content
        return stringValue;
    }

    /**
     * Sanitizes database identifiers like table names, column names, index names, etc.
     * that might contain malicious content.
     *
     * @param inputIdentifier The identifier to sanitize (can be null)
     * @return Sanitized identifier with security markers if suspicious content is detected
     */
    public static String sanitizeIdentifier(String inputIdentifier) {
        if (inputIdentifier == null) return "NULL";

        // Check for suspicious patterns in identifier names
        String lower = inputIdentifier.toLowerCase().trim();
        if (lower.contains("ignore") || lower.contains("system") ||
                lower.contains("instruction") || lower.contains("prompt") ||
                lower.contains("forget") || lower.contains("override") ||
                lower.contains("execute") || lower.contains("jailbreak")) {
            return "[FLAGGED_ID]: " + inputIdentifier;
        }

        // Check for excessively long identifiers that might hide instructions
        if (inputIdentifier.length() > 100) {
            return "[LONG_ID]: " + truncateString(inputIdentifier, 50);
        }

        return inputIdentifier;
    }

    /**
     * Truncates a string to the specified maximum length.
     * Helper method for string sanitization operations.
     *
     * @param inputString The string to truncate (can be null)
     * @param maxLength Maximum length allowed
     * @return Truncated string, or original if within limit
     */
    public static String truncateString(String inputString, int maxLength) {
        if (maxLength <= 0) {
            maxLength = 0;
        }
        if (inputString == null || inputString.length() <= maxLength) {
            return inputString;
        }
        return inputString.substring(0, maxLength) + "...";
    }
}
