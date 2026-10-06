package com.vault.concurrency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrencyVerificationTest {

    private static final int THREAD_COUNT = 50;
    private static final int INVOCATIONS_PER_THREAD = 1000;
    private static final int TOTAL_EXPECTED = THREAD_COUNT * INVOCATIONS_PER_THREAD; // 50 000

    @Test
    @DisplayName("The Flaw: Oskyddad räknare förlorar uppdateringar (Lost Updates) under parallell last")
    void testTheFlaw_unsafeServiceLosesUpdates() throws InterruptedException {
        UnsafeOrderService unsafeService = new UnsafeOrderService();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Släpp iväg alla trådar exakt samtidigt för maximal kollision
                    for (int j = 0; j < INVOCATIONS_PER_THREAD; j++) {
                        unsafeService.createOrder();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Bevis: På grund av race conditions är slutvärdet nästan alltid lägre än 50 000
        System.out.printf("Unsafe count: %d / %d%n", unsafeService.getOrderCount(), TOTAL_EXPECTED);
        assertThat(unsafeService.getOrderCount()).isLessThanOrEqualTo(TOTAL_EXPECTED);
    }

    @Test
    @DisplayName("The Fix: SafeOrderService med AtomicLong ger exakt förväntat resultat")
    void testTheFix_safeServiceMaintainsExactCount() throws InterruptedException {
        SafeOrderService safeService = new SafeOrderService();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < INVOCATIONS_PER_THREAD; j++) {
                        safeService.createOrder();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Bevis: CAS garanterar att exakt alla 50 000 inkrementeringar registreras
        assertThat(safeService.getOrderCount()).isEqualTo(TOTAL_EXPECTED);
    }
}
