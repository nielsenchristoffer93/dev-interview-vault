package com.vault.concurrency;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * TOPIC 7: Asynkrona pipelines (CompletableFuture & Async Composition)
 *
 * THE PROBLEM:
 * När en Controller behöver anropa tre oberoende tjänster (t.ex. UserService, OrderService, InventoryService)
 * och gör det sekventiellt, blir den totala svarstiden summan av alla tre:
 *    Tid = 100ms + 150ms + 200ms = 450ms.
 *
 * THE SOLUTION:
 * Kör anropen parallellt med CompletableFuture och sammanställ dem med allOf() eller thenCombine():
 *    Tid = max(100ms, 150ms, 200ms) = 200ms!
 * Dessutom ger CompletableFuture icke-blockerande felhantering med .exceptionally() eller .handle().
 */
public class AsyncPipelineLab {

    public record UserProfile(String userId, String name) {}
    public record OrderSummary(String userId, int totalOrders) {}
    public record UserDashboard(UserProfile profile, OrderSummary orders) {}

    // Kör parallell hämtning och sammanställer till en gemensam Dashboard
    public CompletableFuture<UserDashboard> fetchDashboardAsync(
            String userId,
            ExecutorService executor,
            long userDelayMs,
            long orderDelayMs
    ) {
        CompletableFuture<UserProfile> profileFuture = CompletableFuture.supplyAsync(() -> {
            simulateLatency(userDelayMs);
            return new UserProfile(userId, "Alice");
        }, executor);

        CompletableFuture<OrderSummary> ordersFuture = CompletableFuture.supplyAsync(() -> {
            simulateLatency(orderDelayMs);
            return new OrderSummary(userId, 42);
        }, executor);

        // Slå ihop de två parallella svaren när båda är klara
        return profileFuture.thenCombine(ordersFuture, UserDashboard::new);
    }

    // Parallell hämtning med fallback vid fel (.exceptionally)
    public CompletableFuture<String> fetchWithFallback(
            String serviceName,
            boolean simulateError,
            ExecutorService executor
    ) {
        return CompletableFuture.supplyAsync(() -> {
            if (simulateError) {
                throw new RuntimeException("Kunde inte nå " + serviceName);
            }
            return "SUCCESS_FROM_" + serviceName;
        }, executor).exceptionally(ex -> "FALLBACK_FOR_" + serviceName);
    }

    private void simulateLatency(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
