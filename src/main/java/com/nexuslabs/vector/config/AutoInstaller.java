package com.nexuslabs.vector.config;

import com.nexuslabs.vector.inference.OllamaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.*;

@Component
public class AutoInstaller implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(AutoInstaller.class);

    private enum InstallState {
        NOT_STARTED, CHECKING, INSTALLING, READY, FAILED
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
        if (osType == OSType.WINDOWS) {
            log.info("  Download from: https://github.com/ollama/ollama/releases");
        } else if (osType == OSType.MACOS) {
            log.info("  Run: brew install ollama");
        } else if (osType == OSType.LINUX) {
            log.info("  Run: curl -fsSL https://ollama.com/install.sh | sh");
        } else {
            log.info("  Visit: https://ollama.com/download");
        }
    }

    private boolean checkOllamaInstalled() {
        try {
            ProcessBuilder pb = new ProcessBuilder(isWindows() ? "ollama.exe" : "ollama", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean exists = p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
            if (exists) log.info("Ollama version: {}", getOllamaVersion());
            return exists;
        } catch (Exception e) {
            log.debug("Ollama not found: {}", e.getMessage());
            return false;
        }
    }

    private String getOllamaVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder(isWindows() ? "ollama.exe" : "ollama", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
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
            ProcessBuilder pb = new ProcessBuilder(isWindows() ? "ollama.exe" : "ollama", "serve");
            pb.redirectErrorStream(true);
            pb.start();
            log.info("Ollama server started");
        } catch (Exception e) {
            log.debug("Could not start Ollama: {}", e.getMessage());
        }
    }

    private void checkModelsAsync() {
        log.info("Checking AI models...");
        
        String simpleModel = config.getModel().getSimpleModel();
        String complexModel = config.getModel().getComplexModel();
        
        checkAndPromptModel(simpleModel, "Simple");
        checkAndPromptModel(complexModel, "Complex");
        
        state = InstallState.READY;
        log.info("=== V.E.C.T.O.R Initialization Complete ===");
    }

    private void checkAndPromptModel(String modelName, String type) {
        try {
            ProcessBuilder pb = new ProcessBuilder(isWindows() ? "ollama.exe" : "ollama", "list");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            String modelShort = modelName.contains("/") 
                ? modelName.substring(modelName.lastIndexOf("/") + 1)
                : modelName;
            modelShort = modelShort.split(":")[0].split("-")[0].toLowerCase();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String output;
            while ((output = reader.readLine()) != null) {
                if (output.toLowerCase().contains(modelShort)) {
                    log.info("{} model ({}) already installed", type, modelName);
                    return;
                }
            }
            
            log.warn("{} model ({}) - NOT FOUND. To install run: ollama pull {}", type, modelName, modelName);
            
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