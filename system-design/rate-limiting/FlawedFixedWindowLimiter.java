package com.vault.ratelimit;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * THE FLAW:
 * Naiv Fixed Window Counter.
 * Nollställer räknaren varje hel minut (t.ex. vid :00).
 * Sårbarhet: En angripare kan skicka 10 requests kl :59 och 10 requests kl :01.
 * Resultat: 20 anrop passerar inom 2 sekunder trots gräns på 10/minut!
 */
public class FlawedFixedWindowLimiter {

    private final int limitPerMinute;
    private final ConcurrentHashMap<Long, AtomicInteger> windowCounts = new ConcurrentHashMap<>();

    public FlawedFixedWindowLimiter(int limitPerMinute) {
        this.limitPerMinute = limitPerMinute;
    }

    public boolean allowRequest(Instant now) {
        long currentMinuteWindow = now.getEpochSecond() / 60;
        AtomicInteger counter = windowCounts.computeIfAbsent(currentMinuteWindow, k -> new AtomicInteger(0));

        return counter.incrementAndGet() <= limitPerMinute;
    }
}
