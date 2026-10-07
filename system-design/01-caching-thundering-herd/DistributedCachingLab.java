package com.vault.caching;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * DistributedCachingLab
 *
 * Demonstrates:
 * 1. Cache Stampede (Thundering Herd) under high concurrency & Single-Flight mutex fix
 * 2. Cache-Aside Race Condition (Evict vs Update)
 * 3. HTTP ETag Conditional Request validation (304 vs 200)
 */
public class DistributedCachingLab {

    // =========================================================================
    // 1. Thundering Herd & Single-Flight
    // =========================================================================

    public static class FlawedCacheService {
        private final Map<String, String> cache = new ConcurrentHashMap<>();
        public final AtomicInteger dbQueryCount = new AtomicInteger(0);

        public String getOrCompute(String key, Supplier<String> expensiveDbQuery) {
            String val = cache.get(key);
            if (val != null) {
                return val;
            }
            // FLAW: When key is missing/expired, every concurrent thread executes the expensive query!
            dbQueryCount.incrementAndGet();
            String computed = expensiveDbQuery.get();
            cache.put(key, computed);
            return computed;
        }

        public void put(String key, String val) {
            cache.put(key, val);
        }

        public void clear() {
            cache.clear();
        }
    }

    public static class SingleFlightCacheService {
        private final Map<String, String> cache = new ConcurrentHashMap<>();
        // In a distributed setup, this is implemented via Redis SET key:lock uuid NX EX 5
        private final Map<String, Object> keyLocks = new ConcurrentHashMap<>();
        public final AtomicInteger dbQueryCount = new AtomicInteger(0);

        public String getOrCompute(String key, Supplier<String> expensiveDbQuery) {
            // First check
            String val = cache.get(key);
            if (val != null) {
                return val;
            }

            // Single-Flight: Coalesce requests per key so only 1 thread hits the database
            Object lock = keyLocks.computeIfAbsent(key, k -> new Object());
            synchronized (lock) {
                // Double-checked locking
                val = cache.get(key);
                if (val != null) {
                    return val;
                }

                dbQueryCount.incrementAndGet();
                String computed = expensiveDbQuery.get();
                cache.put(key, computed);
                return computed;
            }
        }

        public void put(String key, String val) {
            cache.put(key, val);
        }

        public void clear() {
            cache.clear();
        }
    }

    // =========================================================================
    // 2. Cache Invalidation: Evict vs Update
    // =========================================================================

    public static class DatabaseAndCacheStore {
        public final Map<String, String> database = new ConcurrentHashMap<>();
        public final Map<String, String> cache = new ConcurrentHashMap<>();

        /**
         * FLAW: Directly updating cache on write.
         * If Thread A starts, then Thread B writes, but Thread A finishes cache update last,
         * cache ends up holding Thread A's outdated value while DB has Thread B's new value.
         */
        public void flawedUpdate(String key, String value) {
            database.put(key, value);
            cache.put(key, value);
        }

        /**
         * FIX: Cache-Aside eviction after commit.
         * Deletes the cache key so next read fetches the true latest value from DB.
         */
        public void safeEvictAfterCommit(String key, String value) {
            database.put(key, value);
            cache.remove(key); // Evict, don't update!
        }
    }

    // =========================================================================
    // 3. HTTP ETag & Conditional Validation
    // =========================================================================

    public static class HttpResponse {
        public final int status;
        public final String etag;
        public final String body;

        public HttpResponse(int status, String etag, String body) {
            this.status = status;
            this.etag = etag;
            this.body = body;
        }
    }

    public static class HttpCacheController {
        public static HttpResponse handleGet(String content, String ifNoneMatchHeader) {
            String currentEtag = computeEtag(content);

            if (ifNoneMatchHeader != null && ifNoneMatchHeader.equals(currentEtag)) {
                // 304 Not Modified: zero body payload, saving bandwidth & CPU
                return new HttpResponse(304, currentEtag, null);
            }

            return new HttpResponse(200, currentEtag, content);
        }

        public static String computeEtag(String content) {
            try {
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] hash = md.digest(content.getBytes());
                return "\"" + HexFormat.of().formatHex(hash).substring(0, 8) + "\"";
            } catch (Exception e) {
                return "\"" + Integer.toHexString(content.hashCode()) + "\"";
            }
        }
    }
}
