package com.nexuslabs.vector.processing;

import org.springframework.stereotype.Component;

@Component
public class InputSanitizer {

    private static final String CONTROL_CHARS = "[\\p{Cntrl}]";
    private static final String INJECTION_PATTERNS = "(?i)(script|javascript:|onerror|onload|<iframe|<embed)";

    public String sanitize(String input) {
        if (input == null) {
            return null;
        }

        String sanitized = input.replaceAll(CONTROL_CHARS, "");
        sanitized = sanitized.replaceAll(INJECTION_PATTERNS, "");
        sanitized = sanitized.trim();
        sanitized = sanitized.replaceAll("\\s+", " ");

        return sanitized;
    }
}