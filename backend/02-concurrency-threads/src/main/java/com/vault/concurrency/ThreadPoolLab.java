package com.vault.concurrency;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * TOPIC 5: Trådpools-fällan (ThreadPoolExecutor & Backpressure)
 *
 * THE FLAW:
 * Executors.newFixedThreadPool(10) använder under huven en obegränsad LinkedBlockingQueue:
 *    new ThreadPoolExecutor(n, n, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>())
 * Om uppgifter strömmar in snabbare än de 10 trådarna hinner processa växer kön
 * obegränsat i minnet tills JVM:en dör med java.lang.OutOfMemoryError!
 *
 * THE FIX:
 * Skapa alltid ThreadPoolExecutor explicit:
 * 1. Bounded queue (ArrayBlockingQueue med kapacitetstak, t.ex. 100).
 * 2. Begränsat core och max threads.
 * 3. En medveten RejectedExecutionHandler:
 *    - CallerRunsPolicy: Den anropande tråden (t.ex. Tomcat HTTP-tråd) tvingas exekvera uppgiften själv.
 *      Detta skapar naturlig BACKPRESSURE och bromsar inkommande anrop!
 *    - AbortPolicy: Kastar RejectedExecutionException (fail-fast, t.ex. 429 Too Many Requests / 503).
 */
public class ThreadPoolLab {

    // THE FLAW: Oändlig kö -> Risk för OutOfMemoryError
    public static ExecutorService createUnsafeFixedPool(int threads) {
        return Executors.newFixedThreadPool(threads);
    }

    // THE FIX: Begränsad kö med CallerRunsPolicy för naturlig backpressure
    public static ThreadPoolExecutor createSafeBoundedPool(int coreThreads, int maxThreads, int queueCapacity) {
        return new ThreadPoolExecutor(
                coreThreads,
                maxThreads,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new ThreadPoolExecutor.CallerRunsPolicy() // Backpressure!
        );
    }

    // THE FIX B: Fail-Fast med AbortPolicy
    public static ThreadPoolExecutor createFailFastPool(int coreThreads, int maxThreads, int queueCapacity) {
        return new ThreadPoolExecutor(
                coreThreads,
                maxThreads,
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                new ThreadPoolExecutor.AbortPolicy() // Kastar RejectedExecutionException
        );
    }
}
