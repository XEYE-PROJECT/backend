package com.xeye.backend.shared.http;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CircuitBreakerTest {

    /** Reloj manual para avanzar el tiempo sin dormir. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }
    }

    private final MutableClock clock = new MutableClock();
    private final CircuitBreaker breaker = new CircuitBreaker("test", 3, Duration.ofSeconds(30), clock);

    private void fail() {
        assertThrows(IllegalStateException.class, () -> breaker.run(() -> {
            throw new IllegalStateException("boom");
        }));
    }

    @Test
    void opensAfterConsecutiveFailuresAndFailsFast() throws Exception {
        fail();
        fail();
        assertFalse(breaker.isOpen());
        fail();
        assertTrue(breaker.isOpen());
        AtomicInteger calls = new AtomicInteger();
        assertThrows(CircuitBreaker.OpenCircuitException.class, () -> breaker.run(calls::incrementAndGet));
        assertEquals(0, calls.get(), "an open circuit never reaches the dependency");
    }

    @Test
    void aSuccessResetsTheFailureCount() throws Exception {
        fail();
        fail();
        breaker.run(() -> { });
        fail();
        fail();
        assertFalse(breaker.isOpen());
    }

    @Test
    void halfOpenProbeClosesOnSuccessAndReopensOnFailure() throws Exception {
        fail();
        fail();
        fail();
        clock.advance(Duration.ofSeconds(31));
        assertFalse(breaker.isOpen());
        // Sonda fallida: vuelve a abrirse.
        fail();
        assertTrue(breaker.isOpen());
        clock.advance(Duration.ofSeconds(31));
        // Sonda con éxito: se cierra y vuelve a contar desde cero.
        assertEquals("ok", breaker.call(() -> "ok"));
        assertFalse(breaker.isOpen());
        fail();
        fail();
        assertFalse(breaker.isOpen());
    }
}
