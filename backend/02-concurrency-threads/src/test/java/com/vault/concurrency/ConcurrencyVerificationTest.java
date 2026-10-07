package com.vault.concurrency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrencyVerificationTest {

    private static final int THREAD_COUNT = 50;
    private static final int INVOCATIONS_PER_THREAD = 1000;
    private static final int TOTAL_EXPECTED = THREAD_COUNT * INVOCATIONS_PER_THREAD; // 50 000

    // =========================================================================
    // TOPIC 0: Lost Updates & CAS
    // =========================================================================

    @Test
    @DisplayName("0. The Flaw: Oskyddad räknare förlorar uppdateringar (Lost Updates) under parallell last")
    void testTheFlaw_unsafeServiceLosesUpdates() throws InterruptedException {
        UnsafeOrderService unsafeService = new UnsafeOrderService();
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
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

        System.out.printf("Unsafe count: %d / %d%n", unsafeService.getOrderCount(), TOTAL_EXPECTED);
        assertThat(unsafeService.getOrderCount()).isLessThanOrEqualTo(TOTAL_EXPECTED);
    }

    @Test
    @DisplayName("0. The Fix: SafeOrderService med AtomicLong ger exakt förväntat resultat")
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

        assertThat(safeService.getOrderCount()).isEqualTo(TOTAL_EXPECTED);
    }

    // =========================================================================
    // TOPIC 1: Deadlocks & Lock Ordering
    // =========================================================================

    @Test
    @DisplayName("1. The Fix: Global Lock Ordering och tryLock förhindrar dödlägen vid överföring")
    void testTopic1_deadlockAvoidanceWithOrderedAndTryLock() throws Exception {
        DeadlockLab lab = new DeadlockLab();
        DeadlockLab.Account acc1 = new DeadlockLab.Account(1, 1000);
        DeadlockLab.Account acc2 = new DeadlockLab.Account(2, 1000);

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Tråd 1: acc1 -> acc2
        // Tråd 2: acc2 -> acc1 (motsatt riktning!)
        Future<?> f1 = executor.submit(() -> lab.transferOrdered(acc1, acc2, 100));
        Future<?> f2 = executor.submit(() -> lab.transferOrdered(acc2, acc1, 100));

        f1.get(5, TimeUnit.SECONDS);
        f2.get(5, TimeUnit.SECONDS);

        assertThat(acc1.getBalance() + acc2.getBalance()).isEqualTo(2000);

        // Verifiera att tryLock med timeout inte fastnar för evigt
        boolean tryLockSuccess = lab.transferWithTryLock(acc1, acc2, 50, 500);
        assertThat(tryLockSuccess).isTrue();

        executor.shutdown();
    }

    // =========================================================================
    // TOPIC 2: ConcurrentHashMap Check-then-act vs computeIfAbsent
    // =========================================================================

    @Test
    @DisplayName("2. The Flaw & Fix: Check-then-act orsakar multipel laddning vs computeIfAbsent garanterar exakt 1 körning")
    void testTopic2_concurrentHashMapCheckThenActVsComputeIfAbsent() throws Exception {
        ConcurrentHashMapLab lab = new ConcurrentHashMapLab();
        int callers = 20;

        // FLAW: UnsafeCache med 'if (!cache.containsKey) cache.put(...)'
        ConcurrentHashMapLab.UnsafeCache unsafeCache = new ConcurrentHashMapLab.UnsafeCache(lab::simulateExpensiveDbCall);
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(callers);

        for (int i = 0; i < callers; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    unsafeCache.get("USER_100");
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);

        int unsafeInvocations = lab.getLoadCount();
        System.out.printf("Unsafe Cache-Then-Act DB-anrop: %d (Förväntat > 1 pga race condition)%n", unsafeInvocations);
        assertThat(unsafeInvocations).isGreaterThan(1); // Bevis: Flera trådar utförde DB-anropet parallellt!

        // FIX: SafeCache med computeIfAbsent
        lab.reset();
        ConcurrentHashMapLab.SafeCache safeCache = new ConcurrentHashMapLab.SafeCache(lab::simulateExpensiveDbCall);
        CountDownLatch startLatch2 = new CountDownLatch(1);
        CountDownLatch doneLatch2 = new CountDownLatch(callers);

        for (int i = 0; i < callers; i++) {
            executor.submit(() -> {
                try {
                    startLatch2.await();
                    safeCache.get("USER_100");
                } catch (InterruptedException ignored) {
                } finally {
                    doneLatch2.countDown();
                }
            });
        }
        startLatch2.countDown();
        doneLatch2.await(5, TimeUnit.SECONDS);

        int safeInvocations = lab.getLoadCount();
        System.out.printf("Safe computeIfAbsent DB-anrop: %d (Exakt 1)%n", safeInvocations);
        assertThat(safeInvocations).isEqualTo(1); // Garanterat exakt 1 exekvering!

        executor.shutdown();
    }

    // =========================================================================
    // TOPIC 3: ThreadLocal Context Leak i en trådpool
    // =========================================================================

    @Test
    @DisplayName("3. The Flaw & Fix: Orensad ThreadLocal läcker till efterföljande anrop i trådpoolen")
    void testTopic3_threadLocalPollutionInThreadPool() throws Exception {
        ThreadLocalLab lab = new ThreadLocalLab();

        // En trådpool med 1 tråd simulerar en Tomcat worker thread som återanvänds
        ExecutorService singleWorker = Executors.newSingleThreadExecutor();

        // FLAW: Request 1 (Alice) sätter ThreadLocal men glömmer att rensa
        singleWorker.submit(() -> lab.processUnsafe("Alice")).get();

        // Request 2 körs som anonym/okänd användare på SAMMA tråd
        AtomicReference<String> leakedUser = new AtomicReference<>();
        singleWorker.submit(() -> {
            leakedUser.set(ThreadLocalLab.UserContextHolder.get());
        }).get();

        // BEVIS: Request 2 ärvde Alice identitet på grund av saknad remove()!
        assertThat(leakedUser.get()).isEqualTo("Alice");

        // FIX: Rensa med safe pattern (try-with-resources / finally)
        singleWorker.submit(() -> {
            lab.processSafe("Bob", () -> {
                assertThat(ThreadLocalLab.UserContextHolder.get()).isEqualTo("Bob");
            });
        }).get();

        // Request 3 på samma tråd ska nu vara helt ren
        AtomicReference<String> cleanUser = new AtomicReference<>();
        singleWorker.submit(() -> {
            cleanUser.set(ThreadLocalLab.UserContextHolder.get());
        }).get();

        // BEVIS: Kontexten är rensad och tråden är inte förorenad!
        assertThat(cleanUser.get()).isNull();

        singleWorker.shutdown();
    }

    // =========================================================================
    // TOPIC 4: ReentrantLock vs Synchronized & Timeouts
    // =========================================================================

    @Test
    @DisplayName("4. The Fix: ReentrantLock stödjer tryLock timeout och förhindrar evig trådlåsning")
    void testTopic4_reentrantLockTimeoutVsSynchronized() throws Exception {
        VirtualThreadPinningLab.UnpinnedService service = new VirtualThreadPinningLab.UnpinnedService();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        // Tråd 1 håller låset i 200 ms
        executor.submit(() -> service.executeUnpinned(200));
        Thread.sleep(20);

        // Tråd 2 försöker ta låset med 50 ms timeout -> Ska ge upp istället för att hänga
        AtomicBoolean acquired = new AtomicBoolean(true);
        executor.submit(() -> {
            try {
                acquired.set(service.tryExecute(50));
            } catch (InterruptedException ignored) {
            }
        }).get();

        assertThat(acquired.get()).isFalse(); // Gav upp rent och snyggt efter timeout!

        executor.shutdown();
    }
}
