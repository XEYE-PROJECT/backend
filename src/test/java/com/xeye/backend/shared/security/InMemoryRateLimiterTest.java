package com.xeye.backend.shared.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRateLimiterTest {

    @Test
    void allowsUpToLimitThenBlocksUntilNextWindow() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(3, 60);
        Instant t = Instant.ofEpochSecond(600);
        assertEquals(0, limiter.tryAcquire("ip", t));
        assertEquals(0, limiter.tryAcquire("ip", t));
        assertEquals(0, limiter.tryAcquire("ip", t));
        long retry = limiter.tryAcquire("ip", t.plusSeconds(10));
        assertTrue(retry > 0 && retry <= 60, "retry=" + retry);
        assertEquals(0, limiter.tryAcquire("other-ip", t));
        assertEquals(0, limiter.tryAcquire("ip", t.plusSeconds(60)));
    }

    @Test
    void zeroLimitDisables() {
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(0, 60);
        for (int i = 0; i < 100; i++) {
            assertEquals(0, limiter.tryAcquire("ip", Instant.now()));
        }
    }
}
