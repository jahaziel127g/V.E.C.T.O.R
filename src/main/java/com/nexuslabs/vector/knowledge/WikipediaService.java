package com.nexuslabs.vector.knowledge;

import com.nexuslabs.vector.config.AppConfig;
import com.nexuslabs.vector.memory.WikiCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
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

    public Optional<String> getContextForQuery(String query) {
        if (!config.getWikipedia().isEnabled()) {
            return Optional.empty();
        }

        String cacheKey = "wiki:" + query.toLowerCase().trim();
        String cached = wikiCache.get(cacheKey);
        if (cached != null) {
            log.debug("Wiki cache hit: {}", query);
            return Optional.of(cached);
        }

        String zimPath = config.getKiwix().getZimPath();
        File zimFile = new File(zimPath);

        if (!zimFile.exists()) {
            log.warn("ZIM file not found: {}", zimPath);
            return Optional.empty();
        }

        Optional<String> result = searchZim(query, zimPath);
        result.ifPresent(r -> wikiCache.put(cacheKey, r));
        return result;
    }

    private Optional<String> searchZim(String query, String zimPath) {
        log.info("Searching ZIM for: {}", query);

        try {
            ProcessBuilder searchPb = new ProcessBuilder("zimsearch", zimPath, query);
            searchPb.redirectErrorStream(true);
            Process searchProcess = searchPb.start();

            StringBuilder searchOutput = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(searchProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    searchOutput.append(line).append("\n");
                }
            }

            if (!searchProcess.waitFor(10, TimeUnit.SECONDS)) {
                searchProcess.destroy();
                log.warn("zimsearch timed out");
                return Optional.empty();
            }

            String output = searchOutput.toString();
            if (output.isBlank()) {
                log.debug("No search results for: {}", query);
                return Optional.empty();
            }

            String articlePath = extractFirstArticlePath(output);
            if (articlePath == null) {
                log.debug("No article path found");
                return Optional.empty();
            }

            return extractArticleContent(zimPath, articlePath, query);

        } catch (Exception e) {
            log.warn("ZIM search error: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String extractFirstArticlePath(String searchOutput) {
        String[] lines = searchOutput.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("article:")) {
                String path = line.substring(8).trim();
                if (!path.isEmpty()) {
                    return path;
                }
            }
        }
        return null;
    }

    private Optional<String> extractArticleContent(String zimPath, String articlePath, String query) {
        try {
            // Extract article name from path
            String articleName = articlePath;
            if (articleName.contains("/")) {
                String[] parts = articleName.split("/");
                articleName = parts[parts.length - 1];
            }

            ProcessBuilder dumpPb = new ProcessBuilder("zimdump", zimPath, articleName);
            dumpPb.redirectErrorStream(true);
            Process dumpProcess = dumpPb.start();

            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(dumpProcess.getInputStream()))) {
                String line;
                boolean foundContent = false;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("Content:")) {
                        foundContent = true;
                        continue;
                    }
                    if (foundContent && !line.trim().isEmpty()) {
                        content.append(line).append(" ");
                    }
                }
            }

            if (!dumpProcess.waitFor(15, TimeUnit.SECONDS)) {
                dumpProcess.destroy();
                return Optional.empty();
            }

            String rawContent = content.toString().trim();
            if (rawContent.isEmpty()) {
                return Optional.empty();
            }

            String cleaned = rawContent;
            int maxChars = config.getWikipedia().getMaxChars();
            if (cleaned.length() > maxChars) {
                cleaned = cleaned.substring(0, maxChars);
                int lastPeriod = cleaned.lastIndexOf('.');
                if (lastPeriod > maxChars / 2) {
                    cleaned = cleaned.substring(0, lastPeriod + 1);
                }
            }

            if (cleaned.length() > 50) {
                log.info("Found ZIM article for: {}", query);
                return Optional.of(cleaned);
            }

        } catch (Exception e) {
            log.debug("ZIM extract error: {}", e.getMessage());
        }

        return Optional.empty();
    }

    public boolean hasZimFile() {
        return new File(config.getKiwix().getZimPath()).exists();
    }
}
