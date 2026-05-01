package com.nexuslabs.vector.inference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * LlamaCpp client for local model inference using llama.cpp
 */
@Component
public class LlamaCppClient {

    @Value("${vector.llama-cli.path:/home/jahazielo/V.E.C.T.O.R/llama.cpp/build/bin/llama-cli}")
    private String llamaCliPath;

    @Value("${vector.model.simple-model-path:/home/jahazielo/V.E.C.T.O.R/models/gemma-3-1b-it.q4_K_M.gguf}")
    private String simpleModelPath;

    @Value("${vector.model.complex-model-path:/home/jahazielo/V.E.C.T.O.R/models/deepseek-r1-1.5b.q4_K_M.gguf}")
    private String complexModelPath;

    @Value("${vector.llama.ctx-size:512}")
    private int ctxSize;

    @Value("${vector.llama.batch-size:512}")
    private int batchSize;

    @Value("${vector.llama.threads:4}")
    private int threads;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Generate response using llama.cpp CLI
     */
    public String generateResponse(String prompt, boolean isComplex) throws Exception {
        String modelPath = isComplex ? complexModelPath : simpleModelPath;
        
        // Build command
        StringBuilder command = new StringBuilder();
        command.append(llamaCliPath)
                .append(" -m ").append(modelPath)
                .append(" -p ").append(escapeForShell(prompt))
                .append(" -n 200") // Limit tokens for faster response
                .append(" -c ").append(ctxSize)
                .append(" -b ").append(batchSize)
                .append(" -t ").append(threads)
                .append(" --temp 0.7")
                .append(" --top-p 0.9")
                .append(" --repeat-penalty 1.1");

        // Execute command
        ProcessBuilder processBuilder = new ProcessBuilder(command.toString().split(" "));
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        // Read output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("llama.cpp exited with code " + exitCode);
        }

        // Extract generated text (skip the prompt echo)
        String result = output.toString();
        int promptIndex = result.indexOf(prompt);
        if (promptIndex >= 0) {
            result = result.substring(promptIndex + prompt.length());
        }
        
        // Clean up common artifacts
        result = result.replaceAll("[\\[\\]\\{\\}<>]", "").trim();
        
        return result.isEmpty() ? "I apologize, but I couldn't generate a response." : result;
    }

    /**
     * Escape string for shell usage
     */
    private String escapeForShell(String input) {
        if (input == null) return "";
        return "'" + input.replace("'", "'\\''") + "'";
    }
}