package com.nexuslabs.vector.config;

import com.nexuslabs.vector.inference.OllamaClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Component
public class AutoInstaller {

    private static final Logger log = LoggerFactory.getLogger(AutoInstaller.class);

    private final AppConfig config;
    private final OllamaClient ollamaClient;

    public AutoInstaller(AppConfig config, OllamaClient ollamaClient) {
        this.config = config;
        this.ollamaClient = ollamaClient;
    }

    @PostConstruct
    public void initialize() {
        log.info("=== V.E.C.T.O.R Auto-Initialization ===");
        
        checkAndInstallOllama();
        waitForOllama();
        checkAndInstallModels();
        
        log.info("=== Initialization Complete ===");
    }

    private void checkAndInstallOllama() {
        if (isOllamaInstalled()) {
            log.info("Ollama is already installed");
            return;
        }

        log.warn("Ollama not found. Installing...");
        
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "sh", "-c", 
                "curl -fsSL https://ollama.com/install.sh | sh"
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            String output = readProcess(p);
            boolean success = p.waitFor(60, TimeUnit.SECONDS);
            
            if (success && p.exitValue() == 0) {
                log.info("Ollama installed successfully");
            } else {
                log.error("Failed to install Ollama: {}", output);
            }
        } catch (Exception e) {
            log.error("Error installing Ollama: {}", e.getMessage());
        }
    }

    private boolean isOllamaInstalled() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ollama", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void waitForOllama() {
        log.info("Waiting for Ollama to be available...");
        
        for (int i = 0; i < 30; i++) {
            if (ollamaClient.isAvailable()) {
                log.info("Ollama is ready");
                return;
            }
            
            if (i == 0) {
                startOllamaServer();
            }
            
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        log.warn("Ollama not available, will retry on first request");
    }

    private void startOllamaServer() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ollama", "serve");
            pb.redirectErrorStream(true);
            pb.start();
            log.info("Started Ollama server");
        } catch (Exception e) {
            log.debug("Could not start Ollama: {}", e.getMessage());
        }
    }

    private void checkAndInstallModels() {
        String simpleModel = config.getModel().getSimpleModel();
        String complexModel = config.getModel().getComplexModel();

        log.info("Checking models...");
        
        if (!isModelInstalled(simpleModel)) {
            log.info("Installing simple model: {}", simpleModel);
            pullModel(simpleModel);
        } else {
            log.info("Simple model already installed");
        }
        
        if (!isModelInstalled(complexModel)) {
            log.info("Installing complex model: {}", complexModel);
            pullModel(complexModel);
        } else {
            log.info("Complex model already installed");
        }
    }

    private boolean isModelInstalled(String modelName) {
        try {
            String output = runCommand("ollama", "list");
            String shortName = modelName.contains("/") 
                ? modelName.substring(modelName.lastIndexOf("/") + 1)
                : modelName;
            shortName = shortName.split(":")[0];
            return output.toLowerCase().contains(shortName.toLowerCase());
        } catch (Exception e) {
            return false;
        }
    }

    private void pullModel(String modelName) {
        try {
            log.info("Pulling model: {} (this may take a few minutes)", modelName);
            
            ProcessBuilder pb = new ProcessBuilder("ollama", "pull", modelName);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            String output = readProcess(p);
            
            if (p.waitFor(600, TimeUnit.SECONDS) && p.exitValue() == 0) {
                log.info("Model {} installed successfully", modelName);
            } else {
                log.warn("Model {} installation may have failed: {}", modelName, 
                    output.length() > 100 ? output.substring(0, 100) + "..." : output);
            }
        } catch (Exception e) {
            log.error("Error pulling model {}: {}", modelName, e.getMessage());
        }
    }

    private String runCommand(String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output = readProcess(p);
        p.waitFor(10, TimeUnit.SECONDS);
        return output;
    }

    private String readProcess(Process p) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}