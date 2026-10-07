package com.vault.idempotency;

import java.math.BigDecimal;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * THE FIX:
 * Robust Idempotency-mönster:
 * 1. Kontrollerar atomärt om Idempotency-Key redan finns i cache (Redis SET NX EX simulering).
 * 2. Om nyckeln finns med status "SUCCESS" returneras det sparade svaret omedelbart utan att debitera.
 * 3. Om nyckeln är "PROCESSING" avvisas samtidiga parallella dubblettanrop (409 Conflict).
 * 4. Endast det första anropet genomför den faktiska debiteringen.
 */
public class IdempotentPaymentHandler {

    public interface BankClient {
        void debit(String customerId, BigDecimal amount);
    }

    public enum Status { PROCESSING, SUCCESS }

    public static class CachedResponse {
        final Status status;
        final String message;

        public CachedResponse(Status status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    private final BankClient bankClient;
    private final ConcurrentHashMap<String, CachedResponse> idempotencyStore = new ConcurrentHashMap<>();
    private final AtomicInteger actualBankCalls = new AtomicInteger(0);

    public IdempotentPaymentHandler(BankClient bankClient) {
        this.bankClient = bankClient;
    }

    public String processPayment(String idempotencyKey, String customerId, BigDecimal amount) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Header 'Idempotency-Key' is required");
        }

        // Atomär 'SET key PROCESSING NX' simulering
        CachedResponse existing = idempotencyStore.putIfAbsent(
                idempotencyKey,
                new CachedResponse(Status.PROCESSING, null)
        );

        if (existing != null) {
            if (existing.status == Status.SUCCESS) {
                // Returnera direkt det tidigare sparade resultatet!
                return existing.message;
            } else {
                throw new IllegalStateException("409 CONFLICT: Transaktion med denna Idempotency-Key pågår redan.");
            }
        }

        try {
            // Endast den första tråden når hit!
            bankClient.debit(customerId, amount);
            actualBankCalls.incrementAndGet();

            String successResponse = "SUCCESS: Debiterat " + amount + " SEK";
            idempotencyStore.put(idempotencyKey, new CachedResponse(Status.SUCCESS, successResponse));
            return successResponse;
        } catch (Exception e) {
            // Ta bort nyckeln vid fel så användaren kan försöka igen
            idempotencyStore.remove(idempotencyKey);
            throw e;
        }
    }

    public int getActualBankCalls() {
        return actualBankCalls.get();
    }
}
