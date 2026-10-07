package com.vault.caching;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedCachingVerificationTest {

    @Test
    @DisplayName("Thundering Herd Flaw: 20 concurrent cache misses trigger 20 expensive database queries")
    void testThunderingHerdFlaw() throws Exception {
        var cacheService = new DistributedCachingLab.FlawedCacheService();
        int threadCount = 20;
        var executor = Executors.newFixedThreadPool(threadCount);
        var latch = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                return cacheService.getOrCompute("hot_catalog_key", () -> {
                    try { Thread.sleep(20); } catch (InterruptedException ignored) {}
                    return "DatabaseCatalogResult";
                });
            }));
        }

        latch.countDown(); // Release all threads simultaneously
        for (var f : futures) {
            assertThat(f.get()).isEqualTo("DatabaseCatalogResult");
        }
        executor.shutdown();

        // FLAW: Database query count exploded to near threadCount!
        assertThat(cacheService.dbQueryCount.get()).isGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("Single-Flight Fix: 20 concurrent cache misses coalesce into exactly 1 database query")
    void testSingleFlightFix() throws Exception {
        var singleFlightService = new DistributedCachingLab.SingleFlightCacheService();
        int threadCount = 20;
        var executor = Executors.newFixedThreadPool(threadCount);
        var latch = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                return singleFlightService.getOrCompute("hot_catalog_key", () -> {
                    try { Thread.sleep(20); } catch (InterruptedException ignored) {}
                    return "DatabaseCatalogResult";
                });
            }));
        }

        latch.countDown();
        for (var f : futures) {
            assertThat(f.get()).isEqualTo("DatabaseCatalogResult");
        }
        executor.shutdown();

        // FIX: Exactly 1 database query executed across all 20 threads!
        assertThat(singleFlightService.dbQueryCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Cache Invalidation: Eviction prevents dirty read race conditions after DB update")
    void testCacheEvictionOverUpdate() {
        var store = new DistributedCachingLab.DatabaseAndCacheStore();
        store.database.put("item-1", "OriginalValue");
        store.cache.put("item-1", "OriginalValue");

        // Write with eviction
        store.safeEvictAfterCommit("item-1", "NewCommittedValue");

        // Cache must be empty (evicted), forcing caller to read fresh DB value
        assertThat(store.database.get("item-1")).isEqualTo("NewCommittedValue");
        assertThat(store.cache.get("item-1")).isNull();
    }

    @Test
    @DisplayName("HTTP Caching: Returns 304 Not Modified when client ETag matches server content")
    void testHttpEtagConditionalRequest() {
        String content = "{\"products\": [{\"id\": 1, \"name\": \"Keyboard\"}]}";
        String etag = DistributedCachingLab.HttpCacheController.computeEtag(content);

        // 1. First request without ETag -> 200 OK with content & ETag
        var response1 = DistributedCachingLab.HttpCacheController.handleGet(content, null);
        assertThat(response1.status).isEqualTo(200);
        assertThat(response1.body).isEqualTo(content);
        assertThat(response1.etag).isEqualTo(etag);

        // 2. Second request with matching If-None-Match -> 304 Not Modified with null body
        var response2 = DistributedCachingLab.HttpCacheController.handleGet(content, etag);
        assertThat(response2.status).isEqualTo(304);
        assertThat(response2.body).isNull();
        assertThat(response2.etag).isEqualTo(etag);

        // 3. Request after content changes -> 200 OK with new body & new ETag
        String updatedContent = content + " /* updated */";
        var response3 = DistributedCachingLab.HttpCacheController.handleGet(updatedContent, etag);
        assertThat(response3.status).isEqualTo(200);
        assertThat(response3.body).isEqualTo(updatedContent);
        assertThat(response3.etag).isNotEqualTo(etag);
    }
}
