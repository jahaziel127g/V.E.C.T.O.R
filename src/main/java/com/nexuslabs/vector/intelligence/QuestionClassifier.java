package com.nexuslabs.vector.intelligence;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

@Component
public class QuestionClassifier {

    private static final Set<String> SIMPLE_KEYWORDS = Set.of(
        "what is", "define", "meaning of", "who is", "what was", "what are",
        "when did", "when was", "where is", "where did", "which", "name the"
    );

    private static final Set<String> COMPLEX_KEYWORDS = Set.of(
        "why", "how", "explain", "compare", "analyze", "difference between",
        "describe", "discuss", "evaluate", "what would happen if",
        "what do you think", "should i", "is it better", "pros and cons"
    );

    private static final Pattern SIMPLE_PATTERN = buildPattern(SIMPLE_KEYWORDS);
    private static final Pattern COMPLEX_PATTERN = buildPattern(COMPLEX_KEYWORDS);

    private static Pattern buildPattern(Set<String> keywords) {
        String joined = String.join("|", keywords.stream()
            .map(Pattern::quote)
            .toList());
        return Pattern.compile("(?i)\\b(" + joined + ")\\b");
    }

    public QueryComplexity classify(String question) {
        if (question == null || question.isBlank()) {
            return QueryComplexity.SIMPLE;
        }

        String lowerQuestion = question.toLowerCase().trim();

        if (COMPLEX_PATTERN.matcher(lowerQuestion).find()) {
            return QueryComplexity.COMPLEX;
        }

        if (SIMPLE_PATTERN.matcher(lowerQuestion).find()) {
            return QueryComplexity.SIMPLE;
        }

        if (lowerQuestion.contains("?") && lowerQuestion.split("\\s+").length > 15) {
            return QueryComplexity.COMPLEX;
        }

        return QueryComplexity.SIMPLE;
    }
}