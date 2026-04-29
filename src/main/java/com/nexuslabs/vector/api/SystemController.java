package com.nexuslabs.vector.api;

import com.nexuslabs.vector.config.AppConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SystemController {

    @PostMapping("/shutdown")
    public ResponseEntity<Map<String, String>> shutdown(
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey) {
        
        if (!"admin123".equals(adminKey)) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Unauthorized"));
        }
        
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                System.exit(0);
            } catch (Exception e) {
                System.exit(1);
            }
        }).start();
        
        return ResponseEntity.ok(Map.of("message", "Shutting down..."));
    }

    @PostMapping("/kill-ollama")
    public ResponseEntity<Map<String, String>> killOllama(
            @RequestHeader(value = "X-Admin-Key", required = false) String adminKey) {
        
        if (!"admin123".equals(adminKey)) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Unauthorized"));
        }
        
        try {
            ProcessBuilder pb = new ProcessBuilder("pkill", "-f", "ollama serve");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            
            return ResponseEntity.ok(Map.of("message", "Ollama process killed"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("message", "Ollama may not be running"));
        }
    }

    @GetMapping("/ram")
    public ResponseEntity<Map<String, Object>> ramUsage() {
        var runtime = Runtime.getRuntime();
        Map<String, Object> mem = Map.of(
            "total", runtime.totalMemory() / 1024 / 1024 + " MB",
            "free", runtime.freeMemory() / 1024 / 1024 + " MB",
            "used", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024 + " MB",
            "max", runtime.maxMemory() / 1024 / 1024 + " MB"
        );
        return ResponseEntity.ok(mem);
    }
}