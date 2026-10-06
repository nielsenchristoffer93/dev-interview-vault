package com.vault.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MemoryVerificationTest {

    @Test
    @DisplayName("The Flaw: Obegränsad datastruktur växer linjärt och håller referenser")
    void testTheFlaw_unboundedGrowth() {
        MemoryLeakSimulation leak = new MemoryLeakSimulation();
        int itemsToCreate = 1000;

        for (int i = 0; i < itemsToCreate; i++) {
            leak.processRequest(1024); // 1 KB
        }

        // Alla 1000 referenser lever kvar i minnet och kan inte GC:as
        assertThat(leak.getCachedItemCount()).isEqualTo(1000);
    }

    @Test
    @DisplayName("The Fix: Bounded LRU Cache håller sig under maxgränsen och tillåter GC")
    void testTheFix_boundedCacheEvictsEldest() {
        int maxCapacity = 50;
        BoundedMemoryCache<Integer, byte[]> cache = new BoundedMemoryCache<>(maxCapacity);

        // Skjut in 500 element
        for (int i = 0; i < 500; i++) {
            cache.put(i, new byte[1024]);
        }

        // Trots 500 inmatningar behålls endast de 50 senaste
        assertThat(cache.size()).isEqualTo(maxCapacity);

        // De äldsta (t.ex. key 0-449) har evicerats och referenserna kapats för GC
        assertThat(cache.get(0)).isNull();
        assertThat(cache.get(499)).isNotNull();
    }
}
