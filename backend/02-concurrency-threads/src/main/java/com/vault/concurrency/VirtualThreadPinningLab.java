package com.vault.concurrency;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * TOPIC 4: Virtual Threads (Java 21) & "Carrier Thread Pinning"
 *
 * THE FLAW:
 * I Java 21 körs Virtual Threads ovanpå ett fåtal underliggande OS-trådar (Carrier Threads).
 * När en virtuell tråd blockerar på I/O (t.ex. HTTP eller SQL) monteras den normalt av
 * ("unmounted") så att carrier-tråden kan köra andra virtuella trådar.
 *
 * MEN: Om tråden gör blockerande I/O inuti ett 'synchronized'-block eller anropar
 * en native metod, blir den "PINNED" till carrier-tråden!
 * Då blockeras hela den underliggande OS-tråden, och hela syftet med Virtual Threads raseras.
 *
 * THE FIX:
 * Ersätt 'synchronized' med java.util.concurrent.locks.ReentrantLock!
 * ReentrantLock stödjer unmounting av virtuella trådar och ger dessutom stöd för
 * tryLock med timeout.
 */
public class VirtualThreadPinningLab {

    // THE FLAW: synchronized blockerar avmontering av Carrier Thread vid I/O
    public static class PinnedService {
        public synchronized String executeWithPinning(long sleepMs) {
            try {
                // I/O simulering under monitor lock -> PINNED!
                Thread.sleep(sleepMs);
            } catch (InterruptedException ignored) {
            }
            return "PINNED_COMPLETE";
        }
    }

    // THE FIX: ReentrantLock tillåter unmounting av Virtual Thread
    public static class UnpinnedService {
        private final ReentrantLock lock = new ReentrantLock();

        public String executeUnpinned(long sleepMs) {
            lock.lock();
            try {
                // Virtual thread monteras av snyggt från Carrier Thread
                Thread.sleep(sleepMs);
                return "UNPINNED_COMPLETE";
            } catch (InterruptedException ignored) {
                return "INTERRUPTED";
            } finally {
                lock.unlock();
            }
        }

        public boolean tryExecute(long timeoutMs) throws InterruptedException {
            if (lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)) {
                try {
                    return true;
                } finally {
                    lock.unlock();
                }
            }
            return false;
        }
    }
}
