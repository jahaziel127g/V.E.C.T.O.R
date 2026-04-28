package com.nexuslabs.vector.config;

import com.nexuslabs.vector.inference.OllamaClient;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
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

    private enum OSType {
        WINDOWS, LINUX, MACOS, UNKNOWN
    }

    private final AppConfig config;
    private final OllamaClient ollamaClient;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private volatile InstallState state = InstallState.NOT_STARTED;
    private final OSType osType = detectOS();

    public AutoInstaller(AppConfig config, OllamaClient ollamaClient) {
        this.config = config;
        this.ollamaClient = ollamaClient;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("=== V.E.C.T.O.R Initialization Starting (async) ===");
        log.info("Detected OS: {}", osType.name());
        
        executor.submit(() -> {
            try {
                initializeAsync();
            } catch (Exception e) {
                log.error("Initialization failed: {}", e.getMessage());
            }
        });
    }

    private OSType detectOS() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) return OSType.WINDOWS;
        if (os.contains("linux")) return OSType.LINUX;
        if (os.contains("mac") || os.contains("darwin")) return OSType.MACOS;
        return OSType.UNKNOWN;
    }

    private boolean isWindows() { return osType == OSType.WINDOWS; }
    private boolean isMacOS() { return osType == OSType.MACOS; }
    private boolean isLinux() { return osType == OSType.LINUX; }

    private void initializeAsync() {
        state = InstallState.CHECKING;
        
        if (checkOllamaInstalled()) {
            log.info("Ollama already installed");
            waitForOllamaAsync();
        } else {
            log.warn("Ollama not found");
            logInstallationHelp();
            state = InstallState.FAILED;
            return;
        }
    }

    private void logInstallationHelp() {
        log.info("To install Ollama manually:");
        switch (osType) {
            case WINDOWS -> {
                log.info("  Download from: https://github.com/ollama/ollama/releases");
                log.info("  Or run in PowerShell as Admin:");
                log.info("    iwr https://ollama.com/install.ps1 -useb | iex");
            }
            case MACOS -> log.info("  Run: brew install ollama");
            case LINUX -> log.info("  Run: curl -fsSL https://ollama.com/install.sh | sh");
            default -> log.info("  Visit: https://ollama.com/download");
        }
    }

    private boolean checkOllamaInstalled() {
        try {
            ProcessBuilder pb;
            if (isWindows()) {
                pb = new ProcessBuilder("ollama.exe", "--version");
            } else {
                pb = new ProcessBuilder("ollama", "--version");
            }
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
            ProcessBuilder pb = isWindows() 
                ? new ProcessBuilder("ollama.exe", "--version")
                : new ProcessBuilder("ollama", "--version");
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
            ProcessBuilder pb;
            if (isWindows()) {
                pb = new ProcessBuilder("ollama.exe", "serve");
                pb.environment().put("PATH", System.getenv("PATH"));
            } else {
                pb = new ProcessBuilder("ollama", "serve");
            }
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
            ProcessBuilder pb = isWindows()
                ? new ProcessBuilder("ollama.exe", "list")
                : new ProcessBuilder("ollama", "list");
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
            
            log.info("{} model ({}) - may need to be pulled", type, modelName);
            
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