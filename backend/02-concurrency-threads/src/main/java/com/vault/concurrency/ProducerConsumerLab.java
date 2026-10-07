package com.vault.concurrency;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TOPIC 8: Producer-Consumer (BlockingQueue vs wait/notify)
 *
 * THE ANTI-PATTERN:
 * Förr implementerades producent/konsument-mönstret med råa synchronized-block och
 * wait() / notifyAll() på delade objekt.
 * Det ledde till kända felkällor som:
 * - "Spurious wakeups" (trådar vaknar utan att villkoret uppfyllts).
 * - "Missed signals" (notify skickas innan wait hinner kallas).
 * - Onödig CPU-belastning och låskonflikter.
 *
 * THE BEST PRACTICE:
 * Använd java.util.concurrent.BlockingQueue (t.ex. ArrayBlockingQueue)!
 * - queue.put(item): Blockerar producenten om kön är full (naturlig backpressure).
 * - queue.take(): Blockerar konsumenten om kön är tom.
 * Trådkoordinationen hanteras helt automatiskt och lock-free/effektivt internt.
 */
public class ProducerConsumerLab {

    private final BlockingQueue<String> queue;
    private final AtomicInteger producedCount = new AtomicInteger(0);
    private final AtomicInteger consumedCount = new AtomicInteger(0);

    public ProducerConsumerLab(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    public void produce(String item, long timeoutMs) throws InterruptedException {
        boolean accepted = queue.offer(item, timeoutMs, TimeUnit.MILLISECONDS);
        if (accepted) {
            producedCount.incrementAndGet();
        } else {
            throw new IllegalStateException("Kön är full (Backpressure aktiverad)!");
        }
    }

    public String consume(long timeoutMs) throws InterruptedException {
        String item = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
        if (item != null) {
            consumedCount.incrementAndGet();
        }
        return item;
    }

    public int getProducedCount() {
        return producedCount.get();
    }

    public int getConsumedCount() {
        return consumedCount.get();
    }

    public int getQueueSize() {
        return queue.size();
    }
}
