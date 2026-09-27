package com.xeye.backend.shared.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Predicate;

/**
 * Reintentos con backoff exponencial para llamadas salientes <em>idempotentes</em>. Solo se
 * reintenta lo que {@code retryable} acepta (por defecto, fallos de red y 5xx; nunca un 4xx,
 * que no se arregla repitiendo). Nunca se usa con {@code POST} que crean recursos (RunPod).
 */
public final class Retry {

    private static final Logger log = LoggerFactory.getLogger(Retry.class);

    private Retry() {
    }

    public static <T> T run(String what, int attempts, Duration initialBackoff, Predicate<Exception> retryable,
                            CircuitBreaker.Supplier<T> call) throws Exception {
        int max = Math.max(1, attempts);
        Duration wait = initialBackoff;
        for (int attempt = 1; ; attempt++) {
            try {
                return call.get();
            } catch (Exception ex) {
                if (attempt >= max || !retryable.test(ex)) {
                    throw ex;
                }
                log.debug("{} failed (attempt {}/{}): {}; retrying in {} ms",
                        what, attempt, max, ex.getMessage(), wait.toMillis());
                sleep(wait);
                wait = wait.multipliedBy(2);
            }
        }
    }

    public static void run(String what, int attempts, Duration initialBackoff, Predicate<Exception> retryable,
                           CircuitBreaker.Runnable call) throws Exception {
        run(what, attempts, initialBackoff, retryable, () -> {
            call.run();
            return null;
        });
    }

    private static void sleep(Duration wait) throws InterruptedException {
        Thread.sleep(wait.toMillis());
    }
}
