package com.nexuslabs.vector.context;

import com.nexuslabs.vector.config.AppConfig;
import org.springframework.stereotype.Component;

@Component
public class ContextOptimizer {

    private final int maxChars;

    public ContextOptimizer(AppConfig config) {
        this.maxChars = config.getWikipedia().getMaxChars();
    }

    public String optimize(String context) {
        if (context == null || context.isBlank()) {
            return null;
        }

        String optimized = context.trim();

        optimized = optimized.replaceAll("\\s+", " ");

        optimized = removeDuplicateSentences(optimized);

        if (optimized.length() > maxChars) {
            optimized = truncateToSentence(optimized, maxChars);
        }

        return optimized;
    }

    private String removeDuplicateSentences(String text) {
        String[] sentences = text.split("(?<=[.!?])\\s+");
        StringBuilder result = new StringBuilder();
        java.util.Set<String> seen = new java.util.HashSet<>();

        for (String sentence : sentences) {
            String normalized = sentence.toLowerCase().trim();
            if (normalized.length() < 10) {
                continue;
            }
            if (!seen.contains(normalized)) {
                seen.add(normalized);
                result.append(sentence).append(" ");
            }
        }

        return result.toString().trim();
    }

    private String truncateToSentence(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }

        String truncated = text.substring(0, maxLength);
        int lastPeriod = truncated.lastIndexOf('.');
        int lastQuestion = truncated.lastIndexOf('?');
        int lastExclamation = truncated.lastIndexOf('!');
        
        int cutoff = Math.max(lastPeriod, Math.max(lastQuestion, lastExclamation));
        
        if (cutoff > maxLength / 2) {
            return truncated.substring(0, cutoff + 1);
        }
        
        return truncated + "...";
    }
}