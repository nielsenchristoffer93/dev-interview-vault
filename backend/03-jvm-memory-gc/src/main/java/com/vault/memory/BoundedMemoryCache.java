package com.vault.memory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * THE FIX:
 * Bounded LRU Cache (Least Recently Used).
 * 1. Definierar en strikt maxkapacitet (t.ex. 100 element).
 * 2. När maxkapaciteten nås eviceras de äldsta oanvända objekten automatiskt.
 * 3. GC Roots kapas -> Garbage Collector kan direkt frigöra minnet i Young/Old Gen.
 */
public class BoundedMemoryCache<K, V> {

    private final int maxEntries;
    private final Map<K, V> cache;

    public BoundedMemoryCache(int maxEntries) {
        this.maxEntries = maxEntries;
        this.cache = new LinkedHashMap<K, V>(maxEntries, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > maxEntries;
            }
        };
    }

    public synchronized void put(K key, V value) {
        cache.put(key, value);
    }

    public synchronized V get(K key) {
        return cache.get(key);
    }

    public synchronized int size() {
        return cache.size();
    }
}
