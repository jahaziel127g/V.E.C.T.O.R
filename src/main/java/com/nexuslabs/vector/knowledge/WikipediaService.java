package com.nexuslabs.vector.knowledge;

import com.nexuslabs.vector.config.AppConfig;
import com.nexuslabs.vector.memory.WikiCache;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Optional;

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

        File zimFile = new File(config.getKiwix().getZimPath());
        if (!zimFile.exists()) {
            log.warn("ZIM file not found: {}", zimFile.getPath());
            return Optional.empty();
        }

        Optional<String> result = searchZim(query, zimFile.getPath());
        result.ifPresent(r -> wikiCache.put(cacheKey, r));
        return result;
    }

    private Optional<String> searchZim(String query, String zimPath) {
        log.info("Searching ZIM for: {}", query);

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "zimsearch", zimPath, query
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount < 5) {
                    output.append(line).append("\n");
                    lineCount++;
                }
            }

            if (!p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                p.destroy();
                return Optional.empty();
            }

            String result = extractArticleContent(zimPath, query);
            if (result != null && result.length() > 50) {
                log.info("Found ZIM article for: {}", query);
                return Optional.of(result);
            }

        } catch (Exception e) {
            log.warn("ZIM search error: {}", e.getMessage());
        }

        return Optional.empty();
    }

    private String extractArticleContent(String zimPath, String query) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "zimdump", zimPath, "-q", query.replace(" ", "_")
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();

            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String cleaned = Jsoup.parse(line).text();
                    if (cleaned.length() > 20) {
                        content.append(cleaned).append(" ");
                    }
                }
            }

            p.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

            String result = content.toString().trim();
            int max = config.getWikipedia().getMaxChars();
            if (result.length() > max) {
                result = result.substring(0, max);
                int lastPeriod = result.lastIndexOf('.');
                if (lastPeriod > max / 2) {
                    result = result.substring(0, lastPeriod + 1);
                }
            }
            return result;

        } catch (Exception e) {
            log.debug("ZIM dump error: {}", e.getMessage());
            return null;
        }
    }

    public boolean hasZimFile() {
        return new File(config.getKiwix().getZimPath()).exists();
    }
}