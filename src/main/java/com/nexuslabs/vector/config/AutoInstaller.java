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
        NOT_STARTED, CHECKING, INSTALLING, PULLING_MODELS, READY, FAILED
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
                state = InstallState.FAILED;
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
            log.info("Ollama already installed: {}", getOllamaVersion());
            startOllamaAndWait();
        } else {
            log.warn("Ollama not found, installing...");
            installOllama();
        }
    }

    private boolean checkOllamaInstalled() {
        try {
            ProcessBuilder pb = new ProcessBuilder(isWindows() ? "ollama.exe" : "ollama", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            boolean exists = p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
            return exists;
        } catch (Exception e) {
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

    private void installOllama() {
        state = InstallState.INSTALLING;
        
        if (osType != OSType.LINUX && osType != OSType.MACOS) {
            log.error("Cannot auto-install on {}. Please install manually from https://ollama.com/download", osType);
            state = InstallState.FAILED;
            return;
        }
        
        try {
            log.info("Installing Ollama...");
            ProcessBuilder pb = osType == OSType.MACOS
                ? new ProcessBuilder("brew", "install", "ollama")
                : new ProcessBuilder("sh", "-c", "curl -fsSL https://ollama.com/install.sh | sh");
            
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[Ollama Install] {}", line);
            }
            
            if (p.waitFor(300, TimeUnit.SECONDS) && p.exitValue() == 0) {
                log.info("Ollama installed successfully");
                startOllamaAndWait();
            } else {
                log.error("Ollama installation failed");
                state = InstallState.FAILED;
            }
        } catch (Exception e) {
            log.error("Ollama installation error: {}", e.getMessage());
            state = InstallState.FAILED;
        }
    }

    private void startOllamaAndWait() {
        state = InstallState.CHECKING;
        log.info("Starting Ollama server...");
        
        try {
            ProcessBuilder pb = new ProcessBuilder(isWindows() ? "ollama.exe" : "ollama", "serve");
            pb.redirectErrorStream(true);
            pb.start();
            log.info("Ollama server started");
        } catch (Exception e) {
            log.debug("Ollama may already be running: {}", e.getMessage());
        }
        
        // Wait for Ollama to be ready
        int retries = 0;
        while (retries < 15) {
            if (ollamaClient.isAvailable()) {
                log.info("Ollama is ready");
                pullModels();
                return;
            }
            retries++;
            try { Thread.sleep(2000); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        log.warn("Ollama not available after waiting");
        state = InstallState.READY; // Still mark as ready so app can retry later
    }

    private void pullModels() {
        state = InstallState.PULLING_MODELS;
        log.info("Checking and installing AI models...");
        
        String simpleModel = config.getModel().getSimpleModel();
        String complexModel = config.getModel().getComplexModel();
        
        pullModelIfNeeded(simpleModel, "Simple");
        pullModelIfNeeded(complexModel, "Complex");
        
        // Preload simple model for instant first response
        log.info("Preloading simple model for fast startup...");
        try {
            ProcessBuilder pb = new ProcessBuilder("ollama", "run", extractModelShortName(simpleModel), "hi");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor(60, TimeUnit.SECONDS);
            log.info("Simple model preloaded successfully");
        } catch (Exception e) {
            log.debug("Model preload note: {}", e.getMessage());
        }
        
        state = InstallState.READY;
        log.info("=== V.E.C.T.O.R Initialization Complete ===");
    }

    private void pullModelIfNeeded(String modelName, String type) {
        try {
            // Check if model already exists
            ProcessBuilder pb = new ProcessBuilder(isWindows() ? "ollama.exe" : "ollama", "list");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            String modelShort = extractModelShortName(modelName);
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().contains(modelShort)) {
                    log.info("{} model ({}) already installed", type, modelName);
                    return;
                }
            }
            
            log.info("Installing {} model: {}...", type, modelName);
            pullModel(modelName);
            
        } catch (Exception e) {
            log.warn("Model check error for {}: {}", modelName, e.getMessage());
        }
    }

    private String extractModelShortName(String modelName) {
        String name = modelName.contains("/") 
            ? modelName.substring(modelName.lastIndexOf("/") + 1)
            : modelName;
        // Get the part before the colon (tag) and before the first dash
        name = name.split(":")[0].toLowerCase();
        // Take only the model name part (e.g., "gemma3" from "gemma3:1b-it-qat")
        int dashIdx = name.indexOf('-');
        return dashIdx > 0 ? name.substring(0, dashIdx) : name;
    }

    private void pullModel(String modelName) {
        try {
            log.info("Pulling model: {} (this may take several minutes)...", modelName);
            ProcessBuilder pb = new ProcessBuilder(isWindows() ? "ollama.exe" : "ollama", "pull", modelName);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                log.info("[Ollama Pull] {}", line);
            }
            
            if (p.waitFor(600, TimeUnit.SECONDS) && p.exitValue() == 0) {
                log.info("Model {} installed successfully", modelName);
            } else {
                log.warn("Failed to install model {}", modelName);
            }
        } catch (Exception e) {
            log.error("Model pull error for {}: {}", modelName, e.getMessage());
        }
    }

    public boolean isReady() {
        return state == InstallState.READY || ollamaClient.isAvailable();
    }

    public InstallState getState() {
        return state;
    }
}
