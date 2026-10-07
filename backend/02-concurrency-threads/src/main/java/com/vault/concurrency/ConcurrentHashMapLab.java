package com.vault.concurrency;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * TOPIC 2: ConcurrentHashMap och "Check-then-Act"-fällan
 *
 * THE FLAW:
 * Utvecklare tror ofta att bara för att de använder ConcurrentHashMap är all kod trådsäker.
 * Men sammansatta operationer (check-then-act):
 *    if (!map.containsKey(key)) { map.put(key, compute()); }
 * är INTE atomära! Flera trådar kan se att nyckeln saknas och köra den tunga beräkningen parallellt.
 * Detta kallas även "Cache Stampede".
 *
 * THE FIX:
 * Använd map.computeIfAbsent(key, mappingFunction).
 * ConcurrentHashMap garanterar atomäritet på nod-/bucket-nivå så att funktionen
 * exekveras exakt EN gång per nyckel under samtidig last.
 */
public class ConcurrentHashMapLab {

    private final AtomicInteger expensiveLoadCounter = new AtomicInteger(0);

    public int getLoadCount() {
        return expensiveLoadCounter.get();
    }

    public void reset() {
        expensiveLoadCounter.set(0);
    }

    // THE FLAW: Check-then-act med separata anrop (Race Condition!)
    public static class UnsafeCache {
        private final Map<String, String> cache = new ConcurrentHashMap<>();
        private final Function<String, String> loader;

        public UnsafeCache(Function<String, String> loader) {
            this.loader = loader;
        }

        public String get(String key) {
            // FÄLLAN: containsKey() och put() är atomära var för sig,
            // men sekvensen mellan dem är det INTE!
            if (!cache.containsKey(key)) {
                String val = loader.apply(key);
                cache.put(key, val);
            }
            return cache.get(key);
        }
    }

    // THE FIX: Atomär beräkning via computeIfAbsent
    public static class SafeCache {
        private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
        private final Function<String, String> loader;

        public SafeCache(Function<String, String> loader) {
            this.loader = loader;
        }

        public String get(String key) {
            // ATOMÄRT: ConcurrentHashMap låser enbart den specifika bucketen
            // och garanterar att loader.apply() exekveras exakt 1 gång!
            return cache.computeIfAbsent(key, loader);
        }
    }

    // Simulerar ett tungt databasanrop (10 ms)
    public String simulateExpensiveDbCall(String key) {
        expensiveLoadCounter.incrementAndGet();
        try {
            Thread.sleep(10);
        } catch (InterruptedException ignored) {
        }
        return "DATA_FOR_" + key;
    }
}
