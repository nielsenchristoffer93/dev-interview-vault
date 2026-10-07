package com.vault.resilience;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private final ResilientPaymentClient safeClient;
    private final UnprotectedPaymentClient unsafeClient;

    public PaymentController() {
        // Mock-gateway som lyckas
        ResilientPaymentClient.RemoteGateway workingGateway = tx -> "SUCCESS: Payment " + tx + " processed";
        this.safeClient = new ResilientPaymentClient(workingGateway);

        // Oskyddad klient som kastar fel för att demonstrera felet
        UnprotectedPaymentClient.RemoteGateway brokenGateway = tx -> {
            throw new RuntimeException("503 Service Unavailable from 3rd-party bank");
        };
        this.unsafeClient = new UnprotectedPaymentClient(brokenGateway);
    }

    @GetMapping("/safe")
    public ResponseEntity<String> paySafe(@RequestParam(defaultValue = "TX-100") String tx) {
        String result = safeClient.pay(tx);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/fail-fast")
    public ResponseEntity<String> simulateFailingPayment(@RequestParam(defaultValue = "TX-FAIL") String tx) {
        // Simulera transient nedströmshaveri skyddat av Circuit Breaker
        ResilientPaymentClient failingClient = new ResilientPaymentClient(transactionId -> {
            throw new RuntimeException("Connection Timeout to downstream payment provider");
        });
        String result = failingClient.pay(tx);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/unsafe")
    public ResponseEntity<String> payUnsafe(@RequestParam(defaultValue = "TX-RAW") String tx) throws Exception {
        // Kommer kasta exception direkt och blockera tråden vid timeouts
        return ResponseEntity.ok(unsafeClient.pay(tx));
    }
}
