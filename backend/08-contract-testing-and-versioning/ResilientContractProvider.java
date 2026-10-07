package com.vault.contract;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * ResilientContractProvider & Tolerant Consumer
 *
 * FIX:
 * 1. Additive schema changes: provides both old amountInCents and new amount, preserving status.
 * 2. Proper RFC 8594 Sunset & Deprecation headers to announce end of life.
 * 3. Tolerant Reader pattern: Consumer configures ObjectMapper to ignore unknown fields.
 */
public class ResilientContractProvider {

    // Consumer's expected DTO
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentConfirmation(String paymentId, String status, Long amountInCents) {}

    // 1. Resilient additive response
    public String handlePaymentResponseAdditive(String paymentId) {
        return String.format("""
                {
                    "paymentId": "%s",
                    "id": "%s",
                    "status": "CONFIRMED",
                    "amountInCents": 5000,
                    "amount": 50.00,
                    "currency": "SEK",
                    "extraFutureField": "ignored-by-tolerant-consumer"
                }
                """, paymentId, paymentId);
    }

    // 2. RFC 8594 Sunset & Deprecation headers
    public Map<String, String> getDeprecationHeaders() {
        return Map.of(
                "Content-Type", "application/json",
                "Deprecation", "true",
                "Sunset", "Wed, 11 Nov 2026 00:00:00 GMT",
                "Link", "<https://api.example.com/docs/v2-migration>; rel=\"deprecation\""
        );
    }

    // 3. Consumer parsing with Tolerant Reader pattern
    public static PaymentConfirmation parseAsTolerantConsumer(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper.readValue(json, PaymentConfirmation.class);
    }

    // Strict consumer parsing (the anti-pattern that crashes on new fields)
    public static PaymentConfirmation parseAsStrictConsumer(String json) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        return mapper.readValue(json, PaymentConfirmation.class);
    }
}
