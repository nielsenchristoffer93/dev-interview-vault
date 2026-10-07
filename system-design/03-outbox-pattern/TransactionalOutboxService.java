package com.vault.outbox;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * THE FIX:
 * Transactional Outbox Pattern.
 * 1. Skriver både Order och OutboxRecord i EN och samma ACID databastransaktion.
 * 2. Ingen direkt nätverkskommunikation mot Kafka i anropskedjan.
 * 3. En separat CDC-process (t.ex. Debezium som läser PostgreSQL WAL)
 *    eller en outbox-worker läser outbox-tabellen och garanterar leverans.
 */
public class TransactionalOutboxService {

    public static class OutboxRecord {
        final String id;
        final String aggregateType;
        final String aggregateId;
        final String payload;

        public OutboxRecord(String id, String aggregateType, String aggregateId, String payload) {
            this.id = id;
            this.aggregateType = aggregateType;
            this.aggregateId = aggregateId;
            this.payload = payload;
        }

        public String getId() { return id; }
        public String getPayload() { return payload; }
    }

    private final ConcurrentHashMap<String, String> orderTable = new ConcurrentHashMap<>();
    private final List<OutboxRecord> outboxTable = new ArrayList<>();

    public synchronized void createOrderAtomically(String orderId, String payload) {
        // Simulerar Spring @Transactional över en och samma databasanslutning:
        try {
            // 1. Skriv affärsentitet
            orderTable.put(orderId, payload);

            // 2. Skriv outbox-händelse
            OutboxRecord outbox = new OutboxRecord(
                    UUID.randomUUID().toString(),
                    "Order",
                    orderId,
                    payload
            );
            outboxTable.add(outbox);

            // Båda committas atomärt i samma SQL commit!
        } catch (Exception e) {
            // Vid eventuellt fel rullas BÅDA tillbaka
            orderTable.remove(orderId);
            throw e;
        }
    }

    public synchronized boolean hasOrder(String orderId) {
        return orderTable.containsKey(orderId);
    }

    public synchronized List<OutboxRecord> getPendingOutboxRecords() {
        return new ArrayList<>(outboxTable);
    }
}
