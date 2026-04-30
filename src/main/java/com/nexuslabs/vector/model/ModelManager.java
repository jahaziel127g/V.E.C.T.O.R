package com.nexuslabs.vector.model;

import com.nexuslabs.vector.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ModelManager {

    private static final Logger log = LoggerFactory.getLogger(ModelManager.class);

    private final String simpleModel;
    private final String complexModel;
    private final int idleTimeoutMinutes;
    private final int maxRamPercent;

    private final AtomicReference<String> currentModel = new AtomicReference<>(null);
    private volatile long lastUsedTime = 0;
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    public ModelManager(AppConfig config) {
        this.simpleModel = config.getModel().getSimpleModel();
        this.complexModel = config.getModel().getComplexModel();
        this.idleTimeoutMinutes = config.getModel().getIdleTimeoutMinutes();
        this.maxRamPercent = config.getModel().getMaxRamPercent();
    }

    public void ensureModelLoaded(String modelName) {
        lastUsedTime = System.currentTimeMillis();
        
        String current = currentModel.get();
        if (modelName.equals(current)) {
            return;
        }
        
        // Check if we need to unload current model due to RAM pressure
        if (checkRamThreshold()) {
            log.warn("RAM threshold exceeded, forcing fallback to lightweight model");
            switchToModel(simpleModel);
            return;
        }
        
        switchToModel(modelName);
    }

    private void switchToModel(String modelName) {
        String oldModel = currentModel.get();
        
        if (oldModel != null && !oldModel.equals(modelName)) {
            unloadModel(oldModel);
        }
        
        log.info("Switching model from {} to {}", oldModel, modelName);
        currentModel.set(modelName);
    }

    private void unloadModel(String modelName) {
        log.info("Unloading model: {}", modelName);
        try {
            // Extract model name more efficiently
            String modelShortName = extractModelName(modelName);
            ProcessBuilder pb = new ProcessBuilder("ollama", "unload", modelShortName);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            // Wait for process to complete with timeout
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                log.warn("Model unload timed out for: {}", modelName);
            }
            log.info("Model unloaded: {}", modelName);
        } catch (Exception e) {
            log.debug("Could not unload model {}: {}", modelName, e.getMessage());
        }
    }

    private String extractModelName(String modelName) {
        // Extract the model name part before any tag (e.g., "gemma3:1b-it-qat" -> "gemma3")
        int slashIndex = modelName.lastIndexOf('/');
        int colonIndex = modelName.indexOf(':', slashIndex >= 0 ? slashIndex : 0);
        if (colonIndex > 0) {
            return modelName.substring(0, colonIndex);
        }
        return modelName;
    }

    @Scheduled(fixedRate = 60000)
    public void checkIdleTimeout() {
        String model = currentModel.get();
        if (model == null) {
            return;
        }

        long idleTime = System.currentTimeMillis() - lastUsedTime;
        long idleTimeoutMs = idleTimeoutMinutes * 60 * 1000L;

        if (idleTime > idleTimeoutMs) {
            log.info("Model idle timeout reached, unloading model");
            unloadModel(model);
            currentModel.set(null);
        }
    }

    public boolean checkRamThreshold() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        
        if (heapMax <= 0) {
            return false;
        }
        
        int usedPercent = (int) ((heapUsed * 100) / heapMax);
        return usedPercent >= maxRamPercent;
    }

    public String getCurrentModel() {
        return currentModel.get();
    }

    public Map<String, Object> getModelStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("currentModel", currentModel.get());
        status.put("lastUsed", lastUsedTime);
        status.put("simpleModel", simpleModel);
        status.put("complexModel", complexModel);
        
        MemoryMXBean mb = ManagementFactory.getMemoryMXBean();
        long used = mb.getHeapMemoryUsage().getUsed();
        long max = mb.getHeapMemoryUsage().getMax();
        status.put("ramUsagePercent", max > 0 ? (int)((used * 100) / max) : 0);
        
        return status;
    }

    public void switchToFallback() {
        log.warn("Switching to fallback model due to failure");
        switchToModel(simpleModel);
    }
}