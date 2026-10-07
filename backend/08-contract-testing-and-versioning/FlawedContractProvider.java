package com.vault.contract;

import java.util.Map;

/**
 * FlawedContractProvider
 *
 * FLAW:
 * 1. Unannounced breaking change: renames amountInCents -> amount, breaks existing consumer.
 * 2. Unannounced deprecation: drops support without RFC 8594 Sunset / Deprecation headers.
 */
public class FlawedContractProvider {

    public String handlePaymentResponseV2Broken(String paymentId) {
        // FLAW: broke contract by renaming amountInCents to amount (float) and dropping status!
        return String.format("""
                {
                    "id": "%s",
                    "amount": 50.00
                }
                """, paymentId);
    }

    public Map<String, String> getHeadersWithoutSunset() {
        // FLAW: No Sunset or Deprecation headers provided
        return Map.of("Content-Type", "application/json");
    }
}
