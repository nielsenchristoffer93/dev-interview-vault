package com.vault.concurrency;

/**
 * THE FLAW:
 * En typisk Spring @Service singleton med internt föränderligt tillstånd.
 * När flera trådar anropar increment() samtidigt sker Lost Updates
 * på grund av icke-atomär read-modify-write (count++).
 */
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
}
