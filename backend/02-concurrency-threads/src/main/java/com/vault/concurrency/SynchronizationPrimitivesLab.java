package com.vault.concurrency;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TOPIC 6: Synkroniseringsverktyg (Semaphore, CyclicBarrier, CountDownLatch)
 *
 * 1. Semaphore:
 *    Håller ett antal "permits". Begränsar hur många trådar som får tillgång
 *    till en resurs samtidigt (t.ex. max 3 samtidiga anslutningar).
 *
 * 2. CyclicBarrier:
 *    N trådar måste alla anlända till en barriär (barrier.await()) innan
 *    någon av dem får fortsätta. Kan återanvändas i cykler (till skillnad från CountDownLatch).
 *
 * 3. CountDownLatch:
 *    En engångsräknare. En eller flera trådar väntar på att N händelser har skett (countDown()).
 */
public class SynchronizationPrimitivesLab {

    // 1. Throttling med Semaphore
    public static class ThrottledResource {
        private final Semaphore semaphore;
        private final AtomicInteger activeThreads = new AtomicInteger(0);
        private final AtomicInteger maxConcurrentObserved = new AtomicInteger(0);

        public ThrottledResource(int maxPermits) {
            this.semaphore = new Semaphore(maxPermits);
        }

        public void accessResource(long workMs) throws InterruptedException {
            semaphore.acquire();
            try {
                int current = activeThreads.incrementAndGet();
                maxConcurrentObserved.updateAndGet(max -> Math.max(max, current));
                Thread.sleep(workMs);
            } finally {
                activeThreads.decrementAndGet();
                semaphore.release();
            }
        }

        public int getMaxConcurrentObserved() {
            return maxConcurrentObserved.get();
        }
    }

    // 2. Faskoordinering med CyclicBarrier
    public static class MultiPhaseWorker {
        private final CyclicBarrier barrier;
        private final AtomicInteger phaseCount = new AtomicInteger(0);

        public MultiPhaseWorker(int parties) {
            this.barrier = new CyclicBarrier(parties, phaseCount::incrementAndGet);
        }

        public void doPhaseWork() throws InterruptedException, BrokenBarrierException {
            // Arbeta i Fas 1...
            barrier.await(); // Vänta in alla andra trådar innan Fas 2 börjar!
        }

        public int getCompletedPhases() {
            return phaseCount.get();
        }
    }
}
