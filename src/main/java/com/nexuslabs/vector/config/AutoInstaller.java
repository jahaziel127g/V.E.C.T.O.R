package com.nexuslabs.vector.config;

import com.nexuslabs.vector.inference.OllamaClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.*;

@Component
public class AutoInstaller implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(AutoInstaller.class);

    private enum InstallState {
        NOT_STARTED,
        CHECKING,
        INSTALLING,
        READY,
        FAILED
    }

    private final AppConfig config;
    private final OllamaClient ollamaClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private volatile InstallState state = InstallState.NOT_STARTED;

    public AutoInstaller(AppConfig config, OllamaClient ollamaClient) {
        this.config = config;
        this.ollamaClient = ollamaClient;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("=== V.E.C.T.O.R Initialization Starting (async) ===");
        
        executor.submit(() -> {
            try {
                initializeAsync();
            } catch (Exception e) {
                log.error("Initialization failed: {}", e.getMessage());
            }
        });
    }

    private void initializeAsync() {
        state = InstallState.CHECKING;
        
        if (checkOllamaInstalled()) {
            log.info("Ollama already installed");
            waitForOllamaAsync();
        } else {
            log.warn("Ollama not found - install manually or set vector.ollama.auto-install=false");
            state = InstallState.FAILED;
            return;
        }
    }

    private boolean checkOllamaInstalled() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ollama", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean exists = p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
            
            if (exists) {
                log.info("Ollama version: {}", getOllamaVersion());
            }
            return exists;
        } catch (Exception e) {
            log.debug("Ollama not found: {}", e.getMessage());
            return false;
        }
    }

    private String getOllamaVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ollama", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream()));
            String version = reader.readLine();
            p.waitFor(3, TimeUnit.SECONDS);
            return version != null ? version.trim() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void waitForOllamaAsync() {
        state = InstallState.CHECKING;
        log.info("Waiting for Ollama...");
        
        int maxRetries = 15;
        int retry = 0;
        int backoffMs = 2000;
        
        while (retry < maxRetries) {
            if (ollamaClient.isAvailable()) {
                log.info("Ollama is ready");
                checkModelsAsync();
                return;
            }
            
            retry++;
            if (retry == 1) {
                log.info("Starting Ollama server...");
                startOllamaBackground();
            }
            
            try {
                Thread.sleep(backoffMs);
                backoffMs = Math.min(backoffMs * 2, 10000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        log.warn("Ollama not available - will retry on first request");
        state = InstallState.READY;
    }

    private void startOllamaBackground() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ollama", "serve");
            pb.redirectErrorStream(true);
            pb.start();
            log.info("Ollama server started");
        } catch (IOException e) {
            log.debug("Could not start Ollama: {}", e.getMessage());
        }
    }

    private void checkModelsAsync() {
        log.info("Checking AI models...");
        
        checkModel(config.getModel().getSimpleModel(), "Simple");
        checkModel(config.getModel().getComplexModel(), "Complex");
        
        state = InstallState.READY;
        log.info("=== V.E.C.T.O.R Initialization Complete ===");
    }

    private void checkModel(String modelName, String type) {
        try {
            ProcessBuilder pb = new ProcessBuilder("ollama", "list");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            String modelShort = modelName.contains("/") 
                ? modelName.substring(modelName.lastIndexOf("/") + 1)
                : modelName;
            modelShort = modelShort.split(":")[0].split("-")[0].toLowerCase();
            
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(p.getInputStream()));
            String output = reader.readLine();
            while ((output = reader.readLine()) != null) {
                if (output.toLowerCase().contains(modelShort)) {
                    log.info("{} model ({}) already installed", type, modelName);
                    return;
                }
            }
            
            log.info("Installing {} model: {} (this may take a few minutes)", type, modelName);
            
        } catch (Exception e) {
            log.warn("Model check error: {}", e.getMessage());
        }
    }

    public boolean isReady() {
        return state == InstallState.READY || ollamaClient.isAvailable();
    }

    public InstallState getState() {
        return state;
    }
}