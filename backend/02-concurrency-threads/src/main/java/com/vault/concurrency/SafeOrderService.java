package com.vault.concurrency;

import java.util.concurrent.atomic.AtomicLong;

/**
 * THE FIX:
 * 1. Använder AtomicLong som garanterar atomära operationer via CPU:ns Compare-And-Swap (CAS).
 * 2. Inga lås (lock-free) vilket minimerar trådblockering och kontextväxlingar.
 * 3. Garanterar minnessynlighet (memory visibility) i enlighet med Java Memory Model (Happens-Before).
 */
public class SafeOrderService {

    private final AtomicLong orderCount = new AtomicLong(0);

    public void createOrder() {
        // Lock-free atomär inkrementering via CPU CAS
        orderCount.incrementAndGet();
    }

    public long getOrderCount() {
        return orderCount.get();
    }
}
