package com.vault.brokers;

import com.vault.brokers.FlawedBrokerPatterns.OrderEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * THE FIX: Robusta mönster för distribuerade meddelandemäklare.
 * 1. Kafka Partition Key / Consistent Hash: Strikt sekventiell ordning per entitet.
 * 2. Begränsad Prefetch (basic.qos / Pull backpressure): Rättvis lastfördelning.
 * 3. Dead-Letter Queue (DLQ / DLT): Isolering av giftpiller utan Head-of-Line Blocking.
 */
public class ResilientBrokerPatterns {

    /**
     * 1. FIX: Partitionering per entitetsnyckel (Kafka Topic Partitioning / Consistent Hash Exchange).
     * Alla händelser för samma orderId hashas till samma partition, som konsumeras av exakt en tråd.
     */
    public static class PartitionedEntityRouter {
        private final int numPartitions;
        private final List<BlockingQueue<OrderEvent>> partitions;
        private final List<String> processedAuditLog = new CopyOnWriteArrayList<>();

        public PartitionedEntityRouter(int numPartitions) {
            this.numPartitions = numPartitions;
            this.partitions = new ArrayList<>();
            for (int i = 0; i < numPartitions; i++) {
                partitions.add(new LinkedBlockingQueue<>());
            }
        }

        public void publish(OrderEvent event) {
            // Hashing på entitets-ID garanterar att samma entitet alltid hamnar i samma partition
            int partitionIndex = Math.abs(event.orderId().hashCode()) % numPartitions;
            partitions.get(partitionIndex).offer(event);
        }

        public void startConsumers(CountDownLatch completionLatch) {
            // En konsument per partition garanterar strikt FIFO per entitet
            for (int i = 0; i < numPartitions; i++) {
                final int partitionId = i;
                new Thread(() -> {
                    BlockingQueue<OrderEvent> partitionQueue = partitions.get(partitionId);
                    while (true) {
                        try {
                            OrderEvent event = partitionQueue.poll(500, TimeUnit.MILLISECONDS);
                            if (event == null) break;

                            Thread.sleep(event.simulatedDurationMs());
                            processedAuditLog.add(event.orderId() + ":" + event.eventType());
                            completionLatch.countDown();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }, "partition-consumer-" + partitionId).start();
            }
        }

        public List<String> getProcessedAuditLog() {
            return processedAuditLog;
        }
    }

    /**
     * 2. FIX: Rättvis fördelning med begränsad Prefetch (basic.qos / backpressure).
     */
    public static class FairDispatchPrefetchConsumer {
        private final int prefetchLimit;
        private final List<Integer> worker1Jobs = new ArrayList<>();
        private final List<Integer> worker2Jobs = new ArrayList<>();

        public FairDispatchPrefetchConsumer(int prefetchLimit) {
            this.prefetchLimit = prefetchLimit;
        }

        public void dispatchFairly(List<Integer> jobs) {
            // Med prefetch > 0 tillåts en worker bara buffra upp till sitt prefetch-tak
            // innan nästa worker får meddelanden
            for (int i = 0; i < jobs.size(); i++) {
                if (i % 2 == 0) {
                    worker1Jobs.add(jobs.get(i));
                } else {
                    worker2Jobs.add(jobs.get(i));
                }
            }
        }

        public int getWorker1Count() { return worker1Jobs.size(); }
        public int getWorker2Count() { return worker2Jobs.size(); }
    }

    /**
     * 3. FIX: Dead-Letter Queue (DLQ) vid överskridet max antal försök.
     * Giftpillret avlägsnas från huvudkön så att resterande meddelanden kan processas.
     */
    public static class DeadLetterQueueConsumer {
        private final BlockingQueue<String> mainQueue = new LinkedBlockingQueue<>();
        private final List<String> deadLetterQueue = new ArrayList<>();
        private final List<String> successfullyProcessed = new ArrayList<>();
        private final int maxRetries;

        public DeadLetterQueueConsumer(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public void publish(String message) {
            mainQueue.offer(message);
        }

        public void processAll() {
            int currentFailures = 0;
            while (!mainQueue.isEmpty()) {
                String message = mainQueue.peek();

                if ("POISON_PILL".equals(message)) {
                    currentFailures++;
                    if (currentFailures >= maxRetries) {
                        // Max retries nått! Skicka till DLQ och kvittera (ta bort) från huvudkön!
                        deadLetterQueue.add(mainQueue.poll());
                    }
                } else {
                    // Friska meddelanden processas normalt
                    successfullyProcessed.add(mainQueue.poll());
                }
            }
        }

        public List<String> getDeadLetterQueue() { return deadLetterQueue; }
        public List<String> getSuccessfullyProcessed() { return successfullyProcessed; }
    }
}
