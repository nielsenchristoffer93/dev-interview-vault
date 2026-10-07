package com.vault.concurrency;

import org.springframework.stereotype.Service;

/**
 * THE FLAW:
 * En typisk Spring @Service singleton med internt föränderligt tillstånd.
 * När flera trådar anropar createOrder() samtidigt sker Lost Updates
 * på grund av icke-atomär read-modify-write (orderCount++).
 */
@Service
public class UnsafeOrderService {

    private int orderCount = 0;

    public void createOrder() {
        // Icke-atomär operation!
        // 1. Läs orderCount från minne till CPU-register
        // 2. Öka värdet i registret med 1
        // 3. Skriv tillbaka till minne
        orderCount++;
    }

    public int getOrderCount() {
        return orderCount;
    }

    public void reset() {
        this.orderCount = 0;
    }
}
