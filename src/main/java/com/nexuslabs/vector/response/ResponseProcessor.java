package com.nexuslabs.vector.response;

import org.springframework.stereotype.Component;

@Component
public class ResponseProcessor {

    private static final int MAX_RESPONSE_LENGTH = 2000;

    public String process(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            return "I don't have enough information to answer that question.";
        }

        String processed = rawResponse.trim();

        processed = removeFormattingArtifacts(processed);
        processed = fixSpacing(processed);
        processed = removeRepeatedHeaders(processed);
        processed = trimExcessiveLength(processed);

        return processed;
    }

    private String removeFormattingArtifacts(String text) {
        String result = text;

        result = result.replaceAll("(?m)^[#>*]+\\s*", "");
        result = result.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        result = result.replaceAll("__([^_]+)__", "$1");
        result = result.replaceAll("`([^`]+)`", "$1");

        result = result.replaceAll("\\[\\d+\\]", "");
        result = result.replaceAll("\\[source\\]", "");
        result = result.replaceAll("\\[citation needed\\]", "");

        return result;
    }

    private String fixSpacing(String text) {
        String result = text;

        result = result.replaceAll("\\n{3,}", "\n\n");
        result = result.replaceAll(" {2,}", " ");
        result = result.replaceAll("(?<!\\n)\n(?!\\n)", " ");

        return result;
    }

    private String removeRepeatedHeaders(String text) {
        String[] lines = text.split("\n");
        StringBuilder result = new StringBuilder();
        String lastLine = "";

        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.equals(lastLine)) {
                result.append(line).append("\n");
                lastLine = trimmed;
            }
        }

        return result.toString().trim();
    }

    private String trimExcessiveLength(String text) {
        if (text.length() <= MAX_RESPONSE_LENGTH) {
            return text;
        }

        String truncated = text.substring(0, MAX_RESPONSE_LENGTH);
        int lastPeriod = truncated.lastIndexOf('.');
        int lastQuestion = truncated.lastIndexOf('?');
        int lastNewline = truncated.lastIndexOf('\n');

        int cutoff = Math.max(lastPeriod, Math.max(lastQuestion, lastNewline));

        if (cutoff > MAX_RESPONSE_LENGTH * 0.7) {
            return truncated.substring(0, cutoff + 1);
        }

        return truncated + "...";
    }
}