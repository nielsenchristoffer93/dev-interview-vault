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
    private final ResilientPaymentClient safeTimeoutClient;
    private final UnprotectedPaymentClient unsafeClient;
    private final UnprotectedPaymentClient unsafeHangClient;

    public PaymentController(CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry) {
        // Hämta Spring-hanterade CircuitBreaker & Retry registrerade i application.yml
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("paymentService");
        Retry retry = retryRegistry.retry("paymentServiceRetry");

        // 1. Klient som lyckas normalt
        this.safeClient = new ResilientPaymentClient(
                tx -> "SUCCESS: Payment " + tx + " processed",
                cb,
                retry
        );

        // 2. Klient som simulerar transient fel (öppnar kretsbrytaren direkt)
        this.failingClient = new ResilientPaymentClient(
                tx -> { throw new RuntimeException("Downstream Gateway Timeout"); },
                cb,
                retry
        );

        // 3. Klient som simulerar långsam tjänst skyddad av TimeLimiter & Circuit Breaker (The Fix)
        this.safeTimeoutClient = new ResilientPaymentClient(
                tx -> {
                    // Simulerar långsam gateway (10s)
                    Thread.sleep(10000);
                    return "SUCCESS: Delayed payment " + tx;
                },
                cb,
                retry
        );

        // 4. Oskyddad klient med snabbt fel (The Flaw: HTTP 500 utan fallback)
        this.unsafeClient = new UnprotectedPaymentClient(
                tx -> { throw new RuntimeException("503 Service Unavailable"); }
        );

        // 5. Oskyddad klient med långsamt anrop (The Flaw: Thread Pool Starvation)
        this.unsafeHangClient = new UnprotectedPaymentClient(
                tx -> {
                    // Låser Tomcat-tråden i 10 sekunder!
                    Thread.sleep(10000);
                    return "FINISHED_AFTER_HANG: " + tx;
                }
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

    @GetMapping("/unsafe-hang")
    public ResponseEntity<String> payUnsafeHang(@RequestParam(defaultValue = "TX-HANG") String tx) throws Exception {
        // Blockerar Tomcat worker-tråden i 10 sekunder
        return ResponseEntity.ok(unsafeHangClient.pay(tx));
    }

    @GetMapping("/safe-timeout")
    public ResponseEntity<String> paySafeTimeout(@RequestParam(defaultValue = "TX-TIMEOUT") String tx) {
        // Avbryter efter 1000ms och frigör Tomcat-tråden
        return ResponseEntity.ok(safeTimeoutClient.payWithTimeout(tx, java.time.Duration.ofMillis(1000)));
    }
}
