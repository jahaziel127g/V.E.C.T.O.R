package com.nexuslabs.vector.inference;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexuslabs.vector.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    private final String baseUrl;
    private final int timeoutSeconds;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OllamaClient(AppConfig config) {
        this.baseUrl = config.getOllama().getBaseUrl();
        this.timeoutSeconds = config.getOllama().getTimeoutSeconds();
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .executor(Executors.newFixedThreadPool(4))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String generate(String prompt, String model) {
        try {
            // Create optimized JSON payload
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("prompt", prompt);
            requestBody.put("stream", false);
            
            ObjectNode options = objectMapper.createObjectNode();
            options.put("temperature", 0.7);
            options.put("top_p", 0.9);
            options.put("max_tokens", 200); // Reduced for faster response
            requestBody.set("options", options);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseResponse(response.body());
            } else {
                log.error("Ollama request failed with status {}: {}", response.statusCode(), response.body());
                return "I apologize, but I encountered an error processing your request. Please try again.";
            }

        } catch (IOException | InterruptedException e) {
            log.error("Ollama request failed: {}", e.getMessage());
            Thread.currentThread().interrupt();
            return "I apologize, but I'm unable to process your request at the moment. Please ensure Ollama is running.";
        }
    }

    private String parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode response = root.get("response");
            if (response != null) {
                return response.asText();
            }
            return responseBody;
        } catch (Exception e) {
            log.debug("Failed to parse Ollama response: {}", e.getMessage());
            return responseBody;
        }
    }

    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.debug("Ollama availability check failed: {}", e.getMessage());
            return false;
        }
    }
}