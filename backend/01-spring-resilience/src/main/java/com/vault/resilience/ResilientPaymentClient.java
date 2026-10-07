package com.vault.resilience;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * THE FIX:
 * 1. CircuitBreaker med Sliding Window: Efter 3 misslyckade anrop öppnas kretsen (OPEN).
 * 2. Fail-Fast: När kretsen är OPEN nekas anrop omedelbart utan att belasta nätverket.
 * 3. Graceful Degradation / Fallback: Returnerar säkert felsvar istället för att kasta exception.
 */
public class ResilientPaymentClient {

    public interface RemoteGateway {
        String process(String transactionId) throws Exception;
    }

    private final RemoteGateway remoteGateway;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ResilientPaymentClient(RemoteGateway remoteGateway) {
        this(remoteGateway, defaultCircuitBreaker(), defaultRetry());
    }

    public ResilientPaymentClient(RemoteGateway remoteGateway, CircuitBreaker circuitBreaker, Retry retry) {
        this.remoteGateway = remoteGateway;
        this.circuitBreaker = circuitBreaker;
        this.retry = retry;
    }

    public static CircuitBreaker defaultCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowSize(4)
                .minimumNumberOfCalls(3)
                .failureRateThreshold(50.0f)
                .waitDurationInOpenState(Duration.ofMillis(200))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();
        return CircuitBreaker.of("paymentService", config);
    }

    public static Retry defaultRetry() {
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(2)
                .waitDuration(Duration.ofMillis(50))
                .build();
        return Retry.of("paymentServiceRetry", config);
    }

    public String pay(String transactionId) {
        Supplier<String> supplier = () -> {
            try {
                return remoteGateway.process(transactionId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        // Dekorera: Retry -> CircuitBreaker -> Execution
        Supplier<String> decorated = CircuitBreaker.decorateSupplier(circuitBreaker,
                Retry.decorateSupplier(retry, supplier));

        try {
            return decorated.get();
        } catch (CallNotPermittedException e) {
            // Kretsen är OPEN! Fail-fast fallback
            return fallback(transactionId, "CIRCUIT_OPEN: Nedströmstjänst offline");
        } catch (Exception e) {
            return fallback(transactionId, "ERROR: " + e.getMessage());
        }
    }

    /**
     * Timeout-skyddad variant:
     * Klipper långsamma anrop efter angiven tid, frigör anropande tråd
     * och registrerar timeout som fel i Circuit Breaker.
     */
    public String payWithTimeout(String transactionId, Duration timeout) {
        Supplier<String> supplier = () -> {
            java.util.concurrent.CompletableFuture<String> future = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    return remoteGateway.process(transactionId);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            try {
                return future.get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                future.cancel(true);
                throw new RuntimeException("TIMEOUT: Externa anropet överskred " + timeout.toMillis() + "ms", e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        Supplier<String> decorated = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);

        try {
            return decorated.get();
        } catch (CallNotPermittedException e) {
            return fallback(transactionId, "CIRCUIT_OPEN: Nedströmstjänst offline");
        } catch (Exception e) {
            return fallback(transactionId, "TIMEOUT_FALLBACK: " + e.getMessage());
        }
    }

    public String fallback(String transactionId, String reason) {
        return "FALLBACK_QUEUED:" + transactionId + " (" + reason + ")";
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }
}
