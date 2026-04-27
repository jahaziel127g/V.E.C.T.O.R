package com.nexuslabs.vector.knowledge;

import com.nexuslabs.vector.config.AppConfig;
import com.nexuslabs.vector.memory.WikiCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class WikipediaService {

    private static final Logger log = LoggerFactory.getLogger(WikipediaService.class);

    private final WikiCache wikiCache;
    private final AppConfig config;

    public WikipediaService(WikiCache wikiCache, AppConfig config) {
        this.wikiCache = wikiCache;
        this.config = config;
    }

    public String getContextForQuery(String query) {
        if (!config.getWikipedia().isEnabled()) {
            return null;
        }

        String cacheKey = "wiki:" + query.toLowerCase().trim();
        String cached = wikiCache.get(cacheKey);
        if (cached != null) {
            log.debug("Wiki cache hit for: {}", query);
            return cached;
        }

        try {
            String zimPath = config.getKiwix().getZimPath();
            File zimFile = new File(zimPath);

            if (!zimFile.exists()) {
                log.warn("ZIM file not found at: {}", zimPath);
                return getFallbackSearch(query);
            }

            String result = searchZimFile(query, zimPath);
            if (result != null && !result.isBlank()) {
                wikiCache.put(cacheKey, result);
                return result;
            }

            return getFallbackSearch(query);

        } catch (Exception e) {
            log.error("Error accessing Wikipedia: {}", e.getMessage());
            return getFallbackSearch(query);
        }
    }

    private String searchZimFile(String query, String zimPath) {
        return null;
    }

    private String getFallbackSearch(String query) {
        try {
            String dataDir = "/var/lib/kiwix";
            File dataDirFile = new File(dataDir);
            
            if (!dataDirFile.exists()) {
                return null;
            }

            File[] textFiles = dataDirFile.listFiles((dir, name) -> 
                name.endsWith(".txt") || name.endsWith(".html"));

            if (textFiles == null || textFiles.length == 0) {
                return null;
            }

            String[] queryTerms = query.toLowerCase().split("\\s+");
            List<String> matches = new ArrayList<>();

            for (File file : textFiles) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String lowerLine = line.toLowerCase();
                        boolean hasMatch = true;
                        for (String term : queryTerms) {
                            if (!lowerLine.contains(term)) {
                                hasMatch = false;
                                break;
                            }
                        }
                        if (hasMatch && line.length() > 50) {
                            matches.add(line.trim());
                            if (matches.size() >= 3) {
                                break;
                            }
                        }
                    }
                } catch (IOException e) {
                    log.debug("Error reading file {}: {}", file.getName(), e.getMessage());
                }
                if (!matches.isEmpty()) {
                    break;
                }
            }

            if (!matches.isEmpty()) {
                return String.join(" ", matches);
            }

        } catch (Exception e) {
            log.debug("Fallback search failed: {}", e.getMessage());
        }

        return null;
    }

    public boolean isZimFileAvailable() {
        return new File(config.getKiwix().getZimPath()).exists();
    }
}