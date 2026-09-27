package com.xeye.backend.shared.http;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Cortacircuitos mínimo para una dependencia HTTP: tras {@code failureThreshold} fallos
 * seguidos se abre durante {@code openFor} y toda llamada falla al instante con
 * {@link OpenCircuitException} (sin esperar timeouts); pasado ese tiempo deja pasar una
 * llamada de prueba (semiabierto): si va bien se cierra, si falla vuelve a abrirse. Sin
 * dependencias externas: un monolito de una instancia no necesita Resilience4j.
 */
public class CircuitBreaker {

    public static class OpenCircuitException extends RuntimeException {
        public OpenCircuitException(String message) {
            super(message);
        }
    }

    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final String name;
    private final int failureThreshold;
    private final Duration openFor;
    private final Clock clock;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private Instant openedAt;

    public CircuitBreaker(String name, int failureThreshold, Duration openFor) {
        this(name, failureThreshold, openFor, Clock.systemUTC());
    }

    CircuitBreaker(String name, int failureThreshold, Duration openFor, Clock clock) {
        this.name = name;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.openFor = openFor;
        this.clock = clock;
    }

    /** Ejecuta la llamada si el circuito lo permite; registra su éxito o fallo. */
    public <T> T call(Supplier<T> call) throws Exception {
        acquire();
        try {
            T result = call.get();
            onSuccess();
            return result;
        } catch (Exception ex) {
            onFailure();
            throw ex;
        }
    }

    public void run(Runnable call) throws Exception {
        call(() -> {
            call.run();
            return null;
        });
    }

    public synchronized boolean isOpen() {
        return state == State.OPEN && !clock.instant().isAfter(openedAt.plus(openFor));
    }

    private synchronized void acquire() {
        if (state == State.OPEN) {
            if (clock.instant().isAfter(openedAt.plus(openFor))) {
                state = State.HALF_OPEN;
            } else {
                throw new OpenCircuitException(name + " is unavailable (circuit open)");
            }
        } else if (state == State.HALF_OPEN) {
            // Una única llamada de prueba a la vez.
            throw new OpenCircuitException(name + " is unavailable (circuit half-open, probe in flight)");
        }
    }

    private synchronized void onSuccess() {
        state = State.CLOSED;
        consecutiveFailures = 0;
    }

    private synchronized void onFailure() {
        consecutiveFailures++;
        if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            openedAt = clock.instant();
        }
    }

    @FunctionalInterface
    public interface Supplier<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    public interface Runnable {
        void run() throws Exception;
    }
}
