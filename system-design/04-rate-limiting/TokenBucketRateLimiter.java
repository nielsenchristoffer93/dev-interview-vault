package com.vault.ratelimit;

/**
 * THE FIX:
 * Token Bucket Algorithm.
 * 1. Hinken har en maxkapacitet (t.ex. 5 tokens för bursts).
 * 2. Tokens fylls på kontinuerligt baserat på förfluten tid (refillRatePerSecond).
 * 3. Varje request drar 1 token.
 * 4. Inga fönstergränser eller spikar vid minutskiften!
 */
public class TokenBucketRateLimiter {

    private final long capacity;
    private final double refillRatePerSecond;

    private double tokens;
    private long lastRefillTimestampNanos;

    public TokenBucketRateLimiter(long capacity, double refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.tokens = capacity;
        this.lastRefillTimestampNanos = System.nanoTime();
    }

    public synchronized boolean tryAcquire() {
        refill();

        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true; // 200 OK
        }

        return false; // 429 Too Many Requests
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillTimestampNanos) / 1_000_000_000.0;
        double tokensToAdd = elapsedSeconds * refillRatePerSecond;

        if (tokensToAdd > 0) {
            this.tokens = Math.min(capacity, this.tokens + tokensToAdd);
            this.lastRefillTimestampNanos = now;
        }
    }

    public synchronized double getAvailableTokens() {
        refill();
        return tokens;
    }
}
