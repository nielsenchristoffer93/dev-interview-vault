package com.vault.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final ResilientPaymentClient safeClient;
    private final ResilientPaymentClient failingClient;
    private final UnprotectedPaymentClient unsafeClient;

    public PaymentController(CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry) {
        // Hämta Spring-hanterade CircuitBreaker & Retry registrerade i application.yml
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentService");
        Retry retry = retryRegistry.retry("paymentServiceRetry");

        // Klient som lyckas (använder Spring-hanterade mätvärden)
        this.safeClient = new ResilientPaymentClient(
                tx -> "SUCCESS: Payment " + tx + " processed",
                cb,
                retry
        );

        // Klient som simulerar transient fel (delar samma Spring-hanterade CircuitBreaker för att öppna kretsen)
        this.failingClient = new ResilientPaymentClient(
                tx -> { throw new RuntimeException("Downstream Gateway Timeout"); },
                cb,
                retry
        );

        // Oskyddad klient utan Spring-skydd (The Flaw)
        this.unsafeClient = new UnprotectedPaymentClient(
                tx -> { throw new RuntimeException("503 Service Unavailable"); }
        );
    }

    @GetMapping("/safe")
    public ResponseEntity<String> paySafe(@RequestParam(defaultValue = "TX-100") String tx) {
        return ResponseEntity.ok(safeClient.pay(tx));
    }

    @GetMapping("/fail-fast")
    public ResponseEntity<String> simulateFailingPayment(@RequestParam(defaultValue = "TX-FAIL") String tx) {
        return ResponseEntity.ok(failingClient.pay(tx));
    }

    @GetMapping("/unsafe")
    public ResponseEntity<String> payUnsafe(@RequestParam(defaultValue = "TX-RAW") String tx) throws Exception {
        return ResponseEntity.ok(unsafeClient.pay(tx));
    }
}
