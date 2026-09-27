package com.xeye.backend.shared.http;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryTest {

    @Test
    void retriesTransientFailuresUpToTheLimit() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String result = Retry.run("op", 3, Duration.ofMillis(1), ex -> true, () -> {
            if (calls.incrementAndGet() < 3) {
                throw new IllegalStateException("flaky");
            }
            return "done";
        });
        assertEquals("done", result);
        assertEquals(3, calls.get());
    }

    @Test
    void doesNotRetryWhatThePredicateRejects() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> Retry.run("op", 5, Duration.ofMillis(1),
                ex -> !(ex instanceof IllegalArgumentException), () -> {
                    calls.incrementAndGet();
                    throw new IllegalArgumentException("4xx");
                }));
        assertEquals(1, calls.get());
    }

    @Test
    void givesUpAfterTheLastAttempt() {
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> Retry.run("op", 2, Duration.ofMillis(1), ex -> true, () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("always");
        }));
        assertEquals(2, calls.get());
    }
}
