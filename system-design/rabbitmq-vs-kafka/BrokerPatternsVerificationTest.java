package com.vault.brokers;

import com.vault.brokers.FlawedBrokerPatterns.OrderEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("rabbitmq-vs-kafka: Message Broker Architecture Lab")
public class BrokerPatternsVerificationTest {

    @Test
    @DisplayName("The Flaw: Competing Consumers i RabbitMQ vänder på ordningen (CANCELLED före CREATED)")
    void testFlaw_CompetingConsumersProcessEventsOutOfOrder() throws InterruptedException {
        FlawedBrokerPatterns.FlawedCompetingConsumers broker = new FlawedBrokerPatterns.FlawedCompetingConsumers();
        CountDownLatch latch = new CountDownLatch(2);

        // Skicka CREATED först (tar 80ms) och CANCELLED efteråt (tar 10ms)
        broker.publish(new OrderEvent("ORD-99", "CREATED", 1, 80));
        broker.publish(new OrderEvent("ORD-99", "CANCELLED", 2, 10));

        // Starta 2 konkurrerande workers
        broker.runWorkers(2, latch);
        latch.await(2, TimeUnit.SECONDS);

        List<String> auditLog = broker.getProcessedAuditLog();

        // Bevis på felet: Worker 2 hann bli klar med CANCELLED före CREATED!
        assertThat(auditLog).containsExactly("ORD-99:CANCELLED", "ORD-99:CREATED");
    }

    @Test
    @DisplayName("The Fix: Kafka Partition Key tvingar strikt sekventiell ordning per entitet")
    void testFix_PartitionKeyPreservesStrictEntityOrdering() throws InterruptedException {
        ResilientBrokerPatterns.PartitionedEntityRouter router = new ResilientBrokerPatterns.PartitionedEntityRouter(4);
        CountDownLatch latch = new CountDownLatch(2);

        // Skicka samma händelser: CREATED (80ms) och CANCELLED (10ms)
        router.publish(new OrderEvent("ORD-99", "CREATED", 1, 80));
        router.publish(new OrderEvent("ORD-99", "CANCELLED", 2, 10));

        router.startConsumers(latch);
        latch.await(2, TimeUnit.SECONDS);

        List<String> auditLog = router.getProcessedAuditLog();

        // Bevis på fixen: Båda händelserna routades till samma partition och kördes i rätt ordning!
        assertThat(auditLog).containsExactly("ORD-99:CREATED", "ORD-99:CANCELLED");
    }

    @Test
    @DisplayName("The Flaw: Prefetch=0 (Unlimited Push) sväljer alla meddelanden till Worker 1 och svälter Worker 2")
    void testFlaw_UnlimitedPrefetchStarvesOtherConsumers() {
        FlawedBrokerPatterns.FlawedPrefetchDispatcher dispatcher = new FlawedBrokerPatterns.FlawedPrefetchDispatcher();
        List<Integer> jobs = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        dispatcher.dispatchAll(jobs);

        // Bevis på felet: Worker 1 fick 10 jobb, Worker 2 fick 0 (total konsumentsvält)
        assertThat(dispatcher.getWorker1Count()).isEqualTo(10);
        assertThat(dispatcher.getWorker2Count()).isZero();
    }

    @Test
    @DisplayName("The Fix: Begränsad Prefetch (basic.qos) fördelar lasten jämnt och förhindrar svält")
    void testFix_BoundedPrefetchDistributesWorkFairly() {
        ResilientBrokerPatterns.FairDispatchPrefetchConsumer consumer =
                new ResilientBrokerPatterns.FairDispatchPrefetchConsumer(2);
        List<Integer> jobs = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        consumer.dispatchFairly(jobs);

        // Bevis på fixen: Båda arbetarna får 5 jobb var
        assertThat(consumer.getWorker1Count()).isEqualTo(5);
        assertThat(consumer.getWorker2Count()).isEqualTo(5);
    }

    @Test
    @DisplayName("The Flaw: Poison Pill med requeue=true skapar oändlig loop och blockerar kön")
    void testFlaw_PoisonPillRequeueCausesInfiniteLoopAndHeadOfLineBlocking() {
        FlawedBrokerPatterns.FlawedPoisonPillConsumer consumer = new FlawedBrokerPatterns.FlawedPoisonPillConsumer();
        consumer.publish("POISON_PILL");
        consumer.publish("VALID_1");

        // Försök processa 3 gånger
        assertThrows(RuntimeException.class, consumer::processNextMessage);
        assertThrows(RuntimeException.class, consumer::processNextMessage);
        assertThrows(RuntimeException.class, consumer::processNextMessage);

        // Bevis: Giftpillret ligger kvar och försöktes 3 gånger, det giltiga meddelandet nåddes aldrig!
        assertThat(consumer.getRedeliveryCount()).isEqualTo(3);
        assertThat(consumer.getSuccessfullyProcessed()).isEmpty();
    }

    @Test
    @DisplayName("The Fix: Dead-Letter Queue (DLQ) isolerar giftpillret och låter friska meddelanden passera")
    void testFix_DeadLetterQueueIsolatesPoisonPillAndUnblocksQueue() {
        ResilientBrokerPatterns.DeadLetterQueueConsumer consumer =
                new ResilientBrokerPatterns.DeadLetterQueueConsumer(3);

        consumer.publish("POISON_PILL");
        consumer.publish("VALID_1");
        consumer.publish("VALID_2");

        consumer.processAll();

        // Bevis på fixen:
        // 1. Giftpillret flyttades säkert till Dead-Letter Queue efter max retries
        assertThat(consumer.getDeadLetterQueue()).containsExactly("POISON_PILL");
        // 2. Head-of-Line Blocking bröts: De två friska meddelandena processades framgångsrikt!
        assertThat(consumer.getSuccessfullyProcessed()).containsExactly("VALID_1", "VALID_2");
    }
}
