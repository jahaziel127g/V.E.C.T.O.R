package com.nexuslabs.vector.processing;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nexuslabs.vector.config.AppConfig;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimiter {

    private final Cache<String, TokenBucket> buckets;
    private final int maxRequests;

    public RateLimiter(AppConfig config) {
        this.maxRequests = config.getRateLimit().getRequestsPerMinute();
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(Duration.ofMinutes(2))
                .maximumSize(10000)
                .build();
    }

    public boolean isAllowed(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            identifier = "default";
        }

        TokenBucket bucket = buckets.get(identifier, k -> new TokenBucket(maxRequests));

        synchronized (bucket) {
            if (bucket.tokens > 0) {
                bucket.tokens--;
                bucket.lastUpdate = System.currentTimeMillis();
                return true;
            }

            long elapsed = System.currentTimeMillis() - bucket.lastUpdate;
            if (elapsed >= 60000) {
                bucket.tokens = maxRequests - 1;
                bucket.lastUpdate = System.currentTimeMillis();
                return true;
            }

            return false;
        }
    }

    private static class TokenBucket {
        int tokens;
        long lastUpdate;

        TokenBucket(int tokens) {
            this.tokens = tokens;
            this.lastUpdate = System.currentTimeMillis();
        }
    }
}