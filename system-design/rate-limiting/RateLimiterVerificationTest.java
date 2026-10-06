package com.vault.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class RateLimiterVerificationTest {

    @Test
    @DisplayName("The Flaw: Fixed Window släpper igenom 20 anrop inom 2 sekunder över fönstergränsen")
    void testTheFlaw_fixedWindowBoundaryVulnerability() {
        FlawedFixedWindowLimiter limiter = new FlawedFixedWindowLimiter(10); // max 10/minut

        Instant endOfMinute = Instant.parse("2026-10-06T12:00:59Z");
        Instant startOfNextMinute = Instant.parse("2026-10-06T12:01:01Z");

        int allowedInFirstBurst = 0;
        for (int i = 0; i < 10; i++) {
            if (limiter.allowRequest(endOfMinute)) allowedInFirstBurst++;
        }

        int allowedInSecondBurst = 0;
        for (int i = 0; i < 10; i++) {
            if (limiter.allowRequest(startOfNextMinute)) allowedInSecondBurst++;
        }

        // Bevis på sårbarheten: 20 anrop tilläts inom 2 sekunder trots gräns på 10/min!
        assertThat(allowedInFirstBurst + allowedInSecondBurst).isEqualTo(20);
    }

    @Test
    @DisplayName("The Fix: Token Bucket tillåter burst upp till kapacitet (3) men nekar omedelbart 4:e anropet med 429")
    void testTheFix_tokenBucketEnforcesStrictCapacity() {
        // Kapacitet = 3 tokens, fyller på med 1 token per sekund
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, 1.0);

        // Första 3 i snabb följd (burst) ska lyckas
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();

        // 4:e anropet: Hinken är tom! Skall nekas (HTTP 429)
        assertThat(limiter.tryAcquire()).isFalse();
    }
}
