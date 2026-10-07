package com.vault.idempotency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class IdempotencyVerificationTest {

    @Test
    @DisplayName("The Flaw: Utan idempotensnyckel orsakar 3 retries 3 separata debiteringar")
    void testTheFlaw_duplicateCharging() {
        FlawedPaymentEndpoint endpoint = new FlawedPaymentEndpoint((cust, amt) -> {});

        // Klienten försöker 3 gånger pga nätverksglitch
        endpoint.handlePayment("cust-100", new BigDecimal("500.00"));
        endpoint.handlePayment("cust-100", new BigDecimal("500.00"));
        endpoint.handlePayment("cust-100", new BigDecimal("500.00"));

        // Bevis: Kunden debiterades 3 gånger!
        assertThat(endpoint.getTotalDebitsPerformed()).isEqualTo(3);
    }

    @Test
    @DisplayName("The Fix: Med Idempotency-Key resulterar 3 anrop i exakt 1 debitering och samma svarsmeddelande")
    void testTheFix_idempotentDeduplication() {
        IdempotentPaymentHandler handler = new IdempotentPaymentHandler((cust, amt) -> {});
        String idempotencyKey = UUID.randomUUID().toString();

        // Anrop 1
        String response1 = handler.processPayment(idempotencyKey, "cust-100", new BigDecimal("500.00"));

        // Anrop 2 (Retry)
        String response2 = handler.processPayment(idempotencyKey, "cust-100", new BigDecimal("500.00"));

        // Anrop 3 (Retry)
        String response3 = handler.processPayment(idempotencyKey, "cust-100", new BigDecimal("500.00"));

        // Bevis: Banken anropades EXAKT en enda gång!
        assertThat(handler.getActualBankCalls()).isEqualTo(1);

        // Alla svar var identiska
        assertThat(response1).isEqualTo(response2).isEqualTo(response3);
        assertThat(response1).contains("SUCCESS");
    }
}
