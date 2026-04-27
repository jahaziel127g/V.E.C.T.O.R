package com.nexuslabs.vector.prompt;

import com.nexuslabs.vector.intelligence.QueryComplexity;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    private static final String SYSTEM_PROMPT_SIMPLE = """
        You are an AI tutor. Provide clear, concise answers to user questions.
        Use simple language. Stay factual based only on the provided context.
        If no context is provided, give your best answer based on your knowledge.
        """;

    private static final String SYSTEM_PROMPT_COMPLEX = """
        You are an AI tutor specialized in detailed explanations.
        Provide thorough, step-by-step reasoning for complex questions.
        Use the provided context to ground your answer in facts.
        If analyzing or comparing, structure your response clearly.
        Avoid speculation beyond what the context supports.
        """;

    public String build(String question, QueryComplexity complexity, String wikiContext) {
        StringBuilder prompt = new StringBuilder();

        String systemPrompt = complexity == QueryComplexity.COMPLEX 
            ? SYSTEM_PROMPT_COMPLEX 
            : SYSTEM_PROMPT_SIMPLE;

        prompt.append("System: ").append(systemPrompt).append("\n\n");

        if (wikiContext != null && !wikiContext.isBlank()) {
            prompt.append("Context: ").append(wikiContext).append("\n\n");
        }

        prompt.append("User: ").append(question).append("\n\n");

        if (complexity == QueryComplexity.COMPLEX) {
            prompt.append("Please provide a detailed, well-reasoned answer.\n");
        } else {
            prompt.append("Please provide a concise answer.\n");
        }

        prompt.append("Assistant:");

        return prompt.toString();
    }

    public String buildFallback(String question) {
        return build(question, QueryComplexity.SIMPLE, null);
    }
}