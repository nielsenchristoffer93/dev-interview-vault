package com.vault.memory;

import java.util.ArrayList;
import java.util.List;

/**
 * THE FLAW:
 * En obegränsad (unbounded) statisk lista simulerar en klassisk minnesläcka i Java.
 * Referenser till allokerade objekt rensas aldrig -> Garbage Collector kan inte
 * frigöra minnet från Old Generation -> Till slut OutOfMemoryError.
 */
public class MemoryLeakSimulation {

    // GC root som håller referenser för evigt
    private final List<byte[]> unboundedCache = new ArrayList<>();

    public void processRequest(int payloadSizeBytes) {
        // Skapar en array och lagrar referens utan avallokering eller storlekstak
        byte[] payload = new byte[payloadSizeBytes];
        unboundedCache.add(payload);
    }

    public int getCachedItemCount() {
        return unboundedCache.size();
    }

    public void clear() {
        unboundedCache.clear();
    }
}
