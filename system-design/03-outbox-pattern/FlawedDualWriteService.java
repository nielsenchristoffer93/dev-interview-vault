package com.vault.outbox;

import java.util.concurrent.ConcurrentHashMap;

/**
 * THE FLAW:
 * Dual-Write Anti-pattern.
 * Skriver först till intern databas och försöker sedan publicera till Kafka.
 * Om Kafka kastar TimeoutException eller ConnectionRefused har ordern sparats
 * i databasen men meddelandet publiceras aldrig -> Permanent inkonsekvens!
 */
public class FlawedDualWriteService {

    public interface MessageBroker {
        void publish(String topic, String payload) throws Exception;
    }

    private final MessageBroker messageBroker;
    private final ConcurrentHashMap<String, String> database = new ConcurrentHashMap<>();

    public FlawedDualWriteService(MessageBroker messageBroker) {
        this.messageBroker = messageBroker;
    }

    public void createOrder(String orderId, String payload) throws Exception {
        // Steg 1: Skriv till databas
        database.put(orderId, payload);

        // Steg 2: Skriv till Message Broker
        // FLAW: Om denna kastar exception har Steg 1 redan committats!
        messageBroker.publish("orders-topic", payload);
    }

    public boolean existsInDatabase(String orderId) {
        return database.containsKey(orderId);
    }
}
