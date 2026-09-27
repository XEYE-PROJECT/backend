package com.xeye.backend.shared.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final List<String> delivered = new ArrayList<>();

    private final OutboxHandler okHandler = new OutboxHandler() {
        @Override
        public Set<String> types() {
            return Set.of("OK");
        }

        @Override
        public void handle(OutboxEvent event) {
            delivered.add(event.payload());
        }
    };

    private final OutboxHandler failingHandler = new OutboxHandler() {
        @Override
        public Set<String> types() {
            return Set.of("FAIL");
        }

        @Override
        public void handle(OutboxEvent event) {
            throw new IllegalStateException("search down");
        }
    };

    private static OutboxEvent event(long id, String type, String payload, int attempts) {
        return new OutboxEvent(id, type, payload, attempts, Instant.now(), null, Instant.now());
    }

    private OutboxRelay relay(int maxAttempts) {
        return new OutboxRelay(outbox, List.of(okHandler, failingHandler), new OutboxProperties(5000, 100, maxAttempts));
    }

    @Test
    void deliversInOrderDeletesOnSuccessAndCoalescesDuplicates() {
        when(outbox.findDue(any(), anyInt()))
                .thenReturn(List.of(event(1, "OK", "{\"a\":1}", 0), event(2, "OK", "{\"a\":1}", 0),
                        event(3, "OK", "{\"b\":2}", 0)))
                .thenReturn(List.of());

        relay(5).drain();

        assertEquals(List.of("{\"a\":1}", "{\"b\":2}"), delivered, "duplicate delivered once (the latest)");
        verify(outbox).delete(1L);
        verify(outbox).delete(2L);
        verify(outbox).delete(3L);
    }

    @Test
    void reschedulesFailuresWithBackoffAndGivesUpAfterMaxAttempts() {
        when(outbox.findDue(any(), anyInt()))
                .thenReturn(List.of(event(7, "FAIL", "{}", 0), event(8, "FAIL", "{\"x\":1}", 2)))
                .thenReturn(List.of());

        relay(3).drain();

        verify(outbox).reschedule(eq(7L), eq(1), any(), anyString());
        verify(outbox).markFailed(eq(8L), eq(3), anyString());
        verify(outbox, never()).delete(7L);
    }

    @Test
    void unknownTypesAreMarkedFailedInsteadOfLoopingForever() {
        when(outbox.findDue(any(), anyInt()))
                .thenReturn(List.of(event(9, "NOPE", "{}", 0)))
                .thenReturn(List.of());

        relay(3).drain();

        verify(outbox).markFailed(eq(9L), eq(1), anyString());
    }

    @Test
    void backoffGrowsExponentiallyAndIsCapped() {
        assertEquals(Duration.ofSeconds(2), OutboxRelay.backoff(1));
        assertEquals(Duration.ofSeconds(8), OutboxRelay.backoff(3));
        assertTrue(OutboxRelay.backoff(30).compareTo(Duration.ofMinutes(10)) <= 0);
    }
}
