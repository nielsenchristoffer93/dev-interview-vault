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

    public static void main(String[] args) {
        System.out.println("🔥 Startar simulering av minnesläcka...");
        System.out.println("💡 Tips: Kör med '-Xmx32m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./oom.hprof'");
        MemoryLeakSimulation leak = new MemoryLeakSimulation();
        int count = 0;
        try {
            while (true) {
                leak.processRequest(1024 * 100); // 100 KB
                count++;
                if (count % 100 == 0) {
                    long freeMem = Runtime.getRuntime().freeMemory() / (1024 * 1024);
                    long totalMem = Runtime.getRuntime().totalMemory() / (1024 * 1024);
                    System.out.printf("Allokerat: %d objekt | Ledigt minne: %d MB / %d MB%n", count, freeMem, totalMem);
                }
            }
        } catch (OutOfMemoryError oom) {
            System.err.println("💥 OutOfMemoryError inträffade efter " + count + " allokeringar!");
            throw oom;
        }
    }
}
