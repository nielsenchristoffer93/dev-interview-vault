package com.vault.outbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class OutboxVerificationTest {

    @Test
    @DisplayName("The Flaw: Dual-Write lämnar föräldralös data i DB när broker kastar fel")
    void testTheFlaw_dualWriteLeavesInconsistentState() {
        FlawedDualWriteService service = new FlawedDualWriteService((topic, payload) -> {
            throw new RuntimeException("Kafka Broker Unavailable");
        });

        assertThrows(RuntimeException.class, () ->
                service.createOrder("ORD-101", "{\"item\": \"Laptop\"}"));

        // Bevis på felet: Ordern finns i DB trots att eventet aldrig skickades!
        assertThat(service.existsInDatabase("ORD-101")).isTrue();
    }

    @Test
    @DisplayName("The Fix: Transactional Outbox garanterar att entitet och event sparas atomärt tillsammans")
    void testTheFix_outboxSavesBothAtomically() {
        TransactionalOutboxService outboxService = new TransactionalOutboxService();

        outboxService.createOrderAtomically("ORD-202", "{\"item\": \"Phone\"}");

        // Bevis på fixen: Både order och outbox-post existerar och är synkroniserade
        assertThat(outboxService.hasOrder("ORD-202")).isTrue();
        assertThat(outboxService.getPendingOutboxRecords()).hasSize(1);
        assertThat(outboxService.getPendingOutboxRecords().get(0).getPayload()).contains("Phone");
    }
}
