package com.nexuslabs.vector.memory;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nexuslabs.vector.config.AppConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class WikiCache {

    private final Cache<String, String> cache;

    public WikiCache(AppConfig config) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(config.getCache().getWikiMaxSize())
                .expireAfterAccess(Duration.ofMinutes(config.getCache().getExpireAfterMinutes()))
                .build();
    }

    public String get(String key) {
        return cache.getIfPresent(key);
    }

    public void put(String key, String value) {
        cache.put(key, value);
    }

    public void invalidate(String key) {
        cache.invalidate(key);
    }

    public void clear() {
        cache.invalidateAll();
    }
}