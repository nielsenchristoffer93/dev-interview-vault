package com.vault.brokers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * THE FLAW: Vanliga produktionshaverier med Message Brokers (RabbitMQ & Kafka).
 * 1. Competing Consumers i RabbitMQ: Förlorad sekventiell ordning per entitet.
 * 2. Obegränsad Prefetch (prefetch=0): Konsumentsvält och ojämn lastfördelning.
 * 3. Poison Pill med requeue=true: Oändlig omleveransloop och Head-of-Line Blocking.
 */
public class FlawedBrokerPatterns {

    public record OrderEvent(String orderId, String eventType, long sequence, int simulatedDurationMs) {}

    /**
     * 1. FLAW: Competing Consumers bryter händelseordningen per entitet.
     * Både CREATED och CANCELLED läggs i samma kö. Flera workers plockar parallellt.
     */
    public static class FlawedCompetingConsumers {
        private final BlockingQueue<OrderEvent> sharedQueue = new LinkedBlockingQueue<>();
        private final List<String> processedAuditLog = new CopyOnWriteArrayList<>();

        public void publish(OrderEvent event) {
            sharedQueue.offer(event);
        }

        public void runWorkers(int numWorkers, CountDownLatch completionLatch) {
            for (int i = 0; i < numWorkers; i++) {
                final int workerId = i;
                new Thread(() -> {
                    while (true) {
                        try {
                            OrderEvent event = sharedQueue.poll(500, TimeUnit.MILLISECONDS);
                            if (event == null) break;

                            // Simulera variabel processtid (t.ex. nätverksanrop)
                            Thread.sleep(event.simulatedDurationMs());

                            // Logga ordningen händelsen faktiskt färdigställdes i
                            processedAuditLog.add(event.orderId() + ":" + event.eventType());
                            completionLatch.countDown();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }, "worker-" + workerId).start();
            }
        }

        public List<String> getProcessedAuditLog() {
            return processedAuditLog;
        }
    }

    /**
     * 2. FLAW: Prefetch Count = 0 (Unlimited Push) leder till konsumentsvält.
     */
    public static class FlawedPrefetchDispatcher {
        private final List<Integer> worker1BufferedJobs = new ArrayList<>();
        private final List<Integer> worker2BufferedJobs = new ArrayList<>();

        public void dispatchAll(List<Integer> jobs) {
            // RabbitMQ utan basic.qos pushar alla meddelanden direkt till första konsumenten
            for (int job : jobs) {
                worker1BufferedJobs.add(job); // Konsument 1 sväljer alla
            }
            // Konsument 2 får 0 jobb!
        }

        public int getWorker1Count() { return worker1BufferedJobs.size(); }
        public int getWorker2Count() { return worker2BufferedJobs.size(); }
    }

    /**
     * 3. FLAW: Poison Pill med omedelbar requeue skapar Head-of-Line Blocking.
     */
    public static class FlawedPoisonPillConsumer {
        private final BlockingDeque<String> queue = new LinkedBlockingDeque<>();
        private int redeliveryCount = 0;
        private final List<String> successfullyProcessed = new ArrayList<>();

        public void publish(String message) {
            queue.offer(message);
        }

        public void processNextMessage() {
            String message = queue.poll();
            if (message == null) return;

            if ("POISON_PILL".equals(message)) {
                redeliveryCount++;
                // Felet: requeue=true lägger tillbaka giftpillret längst fram i kön!
                queue.offerFirst(message); // Skapar oändlig loop och blockerar alla bakomliggande meddelanden
                throw new RuntimeException("Malformed payload!");
            } else {
                successfullyProcessed.add(message);
            }
        }

        public int getRedeliveryCount() { return redeliveryCount; }
        public List<String> getSuccessfullyProcessed() { return successfullyProcessed; }
    }
}
