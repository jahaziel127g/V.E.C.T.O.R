package com.nexuslabs.vector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "vector")
public class AppConfig {

    private RateLimit rateLimit = new RateLimit();
    private Cache cache = new Cache();
    private Model model = new Model();
    private Ollama ollama = new Ollama();
    private Kiwix kiwix = new Kiwix();
    private Wikipedia wikipedia = new Wikipedia();

    public static class RateLimit {
        private int requestsPerMinute = 10;
        private int tokensPerRequest = 100;

        public int getRequestsPerMinute() { return requestsPerMinute; }
        public void setRequestsPerMinute(int requestsPerMinute) { this.requestsPerMinute = requestsPerMinute; }
        public int getTokensPerRequest() { return tokensPerRequest; }
        public void setTokensPerRequest(int tokensPerRequest) { this.tokensPerRequest = tokensPerRequest; }
    }

    public static class Cache {
        private int answerMaxSize = 1000;
        private int wikiMaxSize = 500;
        private int expireAfterMinutes = 30;

        public int getAnswerMaxSize() { return answerMaxSize; }
        public void setAnswerMaxSize(int answerMaxSize) { this.answerMaxSize = answerMaxSize; }
        public int getWikiMaxSize() { return wikiMaxSize; }
        public void setWikiMaxSize(int wikiMaxSize) { this.wikiMaxSize = wikiMaxSize; }
        public int getExpireAfterMinutes() { return expireAfterMinutes; }
        public void setExpireAfterMinutes(int expireAfterMinutes) { this.expireAfterMinutes = expireAfterMinutes; }
    }

    public static class Model {
        private String simpleModel = "gemma:1b";
        private String complexModel = "phi3.5:mini";
        private int idleTimeoutMinutes = 5;
        private int maxRamPercent = 85;

        public String getSimpleModel() { return simpleModel; }
        public void setSimpleModel(String simpleModel) { this.simpleModel = simpleModel; }
        public String getComplexModel() { return complexModel; }
        public void setComplexModel(String complexModel) { this.complexModel = complexModel; }
        public int getIdleTimeoutMinutes() { return idleTimeoutMinutes; }
        public void setIdleTimeoutMinutes(int idleTimeoutMinutes) { this.idleTimeoutMinutes = idleTimeoutMinutes; }
        public int getMaxRamPercent() { return maxRamPercent; }
        public void setMaxRamPercent(int maxRamPercent) { this.maxRamPercent = maxRamPercent; }
    }

    public static class Ollama {
        private String baseUrl = "http://localhost:11434";
        private int timeoutSeconds = 120;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public static class Kiwix {
        private String zimPath = "/var/lib/kiwix/wikipedia.zim";
        private String fallbackDataDir = "";
        private int maxResults = 5;

        public String getZimPath() { return zimPath; }
        public void setZimPath(String zimPath) { this.zimPath = zimPath; }
        public String getFallbackDataDir() { return fallbackDataDir; }
        public void setFallbackDataDir(String dir) { this.fallbackDataDir = dir; }
        public int getMaxResults() { return maxResults; }
        public void setMaxResults(int maxResults) { this.maxResults = maxResults; }
    }

    public static class Wikipedia {
        private int maxChars = 1500;
        private boolean enabled = true;

        public int getMaxChars() { return maxChars; }
        public void setMaxChars(int maxChars) { this.maxChars = maxChars; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }

    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }
    public Cache getCache() { return cache; }
    public void setCache(Cache cache) { this.cache = cache; }
    public Model getModel() { return model; }
    public void setModel(Model model) { this.model = model; }
    public Ollama getOllama() { return ollama; }
    public void setOllama(Ollama ollama) { this.ollama = ollama; }
    public Kiwix getKiwix() { return kiwix; }
    public void setKiwix(Kiwix kiwix) { this.kiwix = kiwix; }
    public Wikipedia getWikipedia() { return wikipedia; }
    public void setWikipedia(Wikipedia wikipedia) { this.wikipedia = wikipedia; }
}