package com.nexuslabs.vector.knowledge;

import com.nexuslabs.vector.config.AppConfig;
import com.nexuslabs.vector.memory.WikiCache;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
public class WikipediaService {

    private static final Logger log = LoggerFactory.getLogger(WikipediaService.class);

    private final WikiCache wikiCache;
    private final AppConfig config;
    private volatile boolean kiwixStarted = false;

    public WikipediaService(WikiCache wikiCache, AppConfig config) {
        this.wikiCache = wikiCache;
        this.config = config;
    }

    private boolean isKiwixRunning() {
        try {
            int kiwixPort = config.getKiwix().getPort();
            URL url = new URL("http://localhost:" + kiwixPort);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(1500); // 1.5 seconds
            connection.setRequestMethod("HEAD");
            int responseCode = connection.getResponseCode();
            return (responseCode == HttpURLConnection.HTTP_OK);
        } catch (IOException e) {
            return false;
        }
    }

    private void startKiwixServer() {
        if (kiwixStarted) {
            return; // Already started
        }
        String zimPath = config.getKiwix().getZimPath();
        File zimFile = new File(zimPath);
        if (!zimFile.exists()) {
            log.warn("Cannot start Kiwix: ZIM file not found at {}", zimPath);
            return;
        }
        int kiwixPort = config.getKiwix().getPort();
        try {
            ProcessBuilder pb = new ProcessBuilder("kiwix-serve", "--port=" + kiwixPort, zimPath);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            // Consume output to avoid blocking (we don't need to log it)
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        // Optionally log at debug level if needed
                        // log.debug("Kiwix output: {}", line);
                    }
                } catch (IOException e) {
                    log.debug("Error reading Kiwix output: {}", e.getMessage());
                }
            }).start();
            kiwixStarted = true;
            log.info("Kiwix server started on port {} for ZIM: {}", kiwixPort, zimPath);
        } catch (IOException e) {
            log.warn("Failed to start Kiwix server: {}", e.getMessage());
        }
    }

    private void ensureKiwixRunning() {
        if (isKiwixRunning()) {
            kiwixStarted = true;
            return;
        }
        if (!kiwixStarted) {
            startKiwixServer();
            // Wait up to 10 seconds for Kiwix to be ready
            for (int i = 0; i < 10; i++) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (isKiwixRunning()) {
                    log.info("Kiwix server is now running");
                    return;
                }
            }
            log.warn("Kiwix server did not start within 10 seconds");
        }
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

        // Ensure Kiwix server is running for API access
        ensureKiwixRunning();

        // Try to get context from Kiwix HTTP API
        Optional<String> apiResult = searchWithKiwixApi(query);
        if (apiResult.isPresent()) {
            wikiCache.put(cacheKey, apiResult.get());
            return apiResult;
        }

        // Fallback to direct ZIM file search
        File zimFile = new File(config.getKiwix().getZimPath());
        if (!zimFile.exists()) {
            log.warn("ZIM file not found: {}", zimFile.getPath());
            return Optional.empty();
        }

        Optional<String> result = searchZim(query, zimFile.getPath());
        result.ifPresent(r -> wikiCache.put(cacheKey, r));
        return result;
    }

    private Optional<String> searchWithKiwixApi(String query) {
        try {
            int kiwixPort = config.getKiwix().getPort();
            // Use the Kiwix HTTP API to search
            String apiUrl = "http://localhost:" + kiwixPort + "/api?action=query&list=search&srsearch=" + 
                               java.net.URLEncoder.encode(query, StandardCharsets.UTF_8) +
                               "&format=json&srlimit=1";
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000); // 3 seconds
            connection.setRequestMethod("GET");
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    
                    // Parse JSON response to extract page ID
                    String jsonResponse = response.toString();
                    int pageIdStart = jsonResponse.indexOf("\"pageid\":");
                    if (pageIdStart != -1) {
                        pageIdStart += 9; // Length of "\"pageid\":"
                        int pageIdEnd = jsonResponse.indexOf(",", pageIdStart);
                        if (pageIdEnd == -1) {
                            pageIdEnd = jsonResponse.indexOf("}", pageIdStart);
                        }
                        if (pageIdEnd != -1) {
                            String pageIdStr = jsonResponse.substring(pageIdStart, pageIdEnd).trim();
                            int pageId = Integer.parseInt(pageIdStr);
                            
                            // Get the content of the page
                            String contentUrl = "http://localhost:" + kiwixPort + 
                                                   "/api?action=query&prop=extracts&exintro&explaintext&pageids=" + 
                                                   pageId + "&format=json";
                            URL contentUrlObj = new URL(contentUrl);
                            HttpURLConnection contentConnection = (HttpURLConnection) contentUrlObj.openConnection();
                            contentConnection.setConnectTimeout(3000);
                            contentConnection.setRequestMethod("GET");
                            
                            if (contentConnection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                                try (BufferedReader contentReader = new BufferedReader(
                                            new InputStreamReader(contentConnection.getInputStream(), StandardCharsets.UTF_8))) {
                                    StringBuilder contentResponse = new StringBuilder();
                                    String contentLine;
                                    while ((contentLine = contentReader.readLine()) != null) {
                                        contentResponse.append(contentLine);
                                    }
                                    
                                    String contentJson = contentResponse.toString();
                                    int extractStart = contentJson.indexOf("\"extract\":\"");
                                    if (extractStart != -1) {
                                        extractStart += 11; // Length of "\"extract\":\""
                                        int extractEnd = contentJson.indexOf("\"", extractStart);
                                        if (extractEnd != -1) {
                                            String extract = contentJson.substring(extractStart, extractEnd);
                                            // Unescape JSON string
                                            extract = extract.replace("\\\\", "\\").replace("\\\"", "\"").replace("\\n", " ").replace("\\t", " ");
                                            
                                            // Clean up HTML entities and limit length
                                            String cleaned = Jsoup.parse(extract).text();
                                            if (cleaned.length() > config.getWikipedia().getMaxChars()) {
                                                cleaned = cleaned.substring(0, config.getWikipedia().getMaxChars());
                                            }
                                            
                                            log.info("Found ZIM article for: {}", query);
                                            return Optional.of(cleaned);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Kiwix API search failed: {}", e.getMessage());
        }
        return Optional.empty();
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