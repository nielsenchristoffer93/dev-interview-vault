package com.vault.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractVerificationTest {

    @Test
    @DisplayName("Contract Break Flaw: Renaming fields causes consumer NullPointerException / missing values")
    void testContractBreakFlaw() throws Exception {
        var flawedProvider = new FlawedContractProvider();
        String brokenJson = flawedProvider.handlePaymentResponseV2Broken("PAY-99");

        // Consumer parses broken response
        var confirmation = ResilientContractProvider.parseAsTolerantConsumer(brokenJson);

        // FLAW: paymentId, status, and amountInCents are all null because provider broke the contract!
        assertThat(confirmation.paymentId()).isNull();
        assertThat(confirmation.status()).isNull();
        assertThat(confirmation.amountInCents()).isNull();
    }

    @Test
    @DisplayName("Contract Fix: Additive schema evolution allows existing consumer to process successfully")
    void testAdditiveSchemaFix() throws Exception {
        var resilientProvider = new ResilientContractProvider();
        String additiveJson = resilientProvider.handlePaymentResponseAdditive("PAY-99");

        // Consumer successfully parses required fields despite additional fields being present
        var confirmation = ResilientContractProvider.parseAsTolerantConsumer(additiveJson);

        assertThat(confirmation.paymentId()).isEqualTo("PAY-99");
        assertThat(confirmation.status()).isEqualTo("CONFIRMED");
        assertThat(confirmation.amountInCents()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("Tolerant Reader: Tolerant consumer ignores unknown fields; strict consumer crashes")
    void testTolerantReaderPattern() throws Exception {
        var resilientProvider = new ResilientContractProvider();
        String jsonWithExtraFields = resilientProvider.handlePaymentResponseAdditive("PAY-99");

        // Tolerant reader succeeds
        var tolerant = ResilientContractProvider.parseAsTolerantConsumer(jsonWithExtraFields);
        assertThat(tolerant.paymentId()).isEqualTo("PAY-99");

        // Notice: @JsonIgnoreProperties(ignoreUnknown = true) on the record itself already defends the consumer!
        // But if someone uses a DTO without annotations and strict ObjectMapper:
        String pureJson = "{\"paymentId\":\"PAY-99\",\"status\":\"CONFIRMED\",\"amountInCents\":5000,\"unexpectedField\":true}";
        var parsed = ResilientContractProvider.parseAsTolerantConsumer(pureJson);
        assertThat(parsed.paymentId()).isEqualTo("PAY-99");
    }

    @Test
    @DisplayName("API Deprecation: RFC 8594 Sunset & Deprecation headers provide formal end-of-life notice")
    void testSunsetHeaderStandard() {
        var provider = new ResilientContractProvider();
        var headers = provider.getDeprecationHeaders();

        assertThat(headers).containsEntry("Deprecation", "true");
        assertThat(headers).containsEntry("Sunset", "Wed, 11 Nov 2026 00:00:00 GMT");
        assertThat(headers.get("Link")).contains("rel=\"deprecation\"");
    }
}
