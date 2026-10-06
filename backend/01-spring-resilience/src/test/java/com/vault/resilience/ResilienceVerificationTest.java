package com.vault.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ResilienceVerificationTest {

    @Test
    @DisplayName("The Flaw: Oskyddad klient propagerar krasch direkt utan skyddsnät")
    void testTheFlaw_unprotectedFailsHard() {
        UnprotectedPaymentClient.RemoteGateway brokenGateway = tx -> {
            throw new RuntimeException("503 Service Unavailable");
        };

        UnprotectedPaymentClient client = new UnprotectedPaymentClient(brokenGateway);

        assertThrows(RuntimeException.class, () -> client.pay("tx-123"));
    }

    @Test
    @DisplayName("The Fix: ResilientPaymentClient bryter kretsen (OPEN) vid upprepade fel och svarar med Fallback")
    void testTheFix_circuitBreakerOpensAndActivatesFallback() {
        AtomicInteger networkCallCount = new AtomicInteger(0);

        ResilientPaymentClient.RemoteGateway failingGateway = tx -> {
            networkCallCount.incrementAndGet();
            throw new RuntimeException("Connection Timeout");
        };

        ResilientPaymentClient client = new ResilientPaymentClient(failingGateway);

        assertThat(client.getCircuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Anrop 1: Misslyckas (inkluderar retries)
        String res1 = client.pay("tx-1");
        assertThat(res1).contains("FALLBACK_QUEUED");

        // Anrop 2: Misslyckas
        String res2 = client.pay("tx-2");
        assertThat(res2).contains("FALLBACK_QUEUED");

        // Anrop 3: Misslyckas -> Tröskel nådd, kretsen övergår till OPEN
        String res3 = client.pay("tx-3");
        assertThat(res3).contains("FALLBACK_QUEUED");

        assertThat(client.getCircuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        int recordedCallsBeforeFailFast = networkCallCount.get();

        // Anrop 4: Skall omedelbart avvisas av kretsbrytaren utan att gateway ens anropas!
        String fastFailRes = client.pay("tx-4");
        assertThat(fastFailRes).contains("CIRCUIT_OPEN");

        // Bevis: Inga nya anrop skickades över nätverket!
        assertThat(networkCallCount.get()).isEqualTo(recordedCallsBeforeFailFast);
    }
}
