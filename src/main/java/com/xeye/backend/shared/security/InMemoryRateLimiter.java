package com.xeye.backend.shared.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limitador de ventana fija por clave (IP, cuenta…) en memoria. Suficiente para el monolito de
 * una instancia; con varias instancias iría a Redis. Las ventanas vencidas se purgan de forma
 * perezosa cada cierto número de accesos para no crecer sin límite.
 */
public class InMemoryRateLimiter {

    private final int limit;
    private final long windowSeconds;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicInteger accesses = new AtomicInteger();

    public InMemoryRateLimiter(int limit, long windowSeconds) {
        this.limit = limit;
        this.windowSeconds = windowSeconds;
    }

    /**
     * Cuenta un acceso de {@code key}.
     * @return 0 si está dentro del límite; si no, los segundos que faltan para que se abra la ventana
     */
    public long tryAcquire(String key, Instant now) {
        if (limit <= 0) {
            return 0;
        }
        long bucket = Math.floorDiv(now.getEpochSecond(), windowSeconds);
        Window window = windows.compute(key, (k, existing) ->
                existing == null || existing.bucket != bucket ? new Window(bucket) : existing);
        int count = window.count.incrementAndGet();
        if (accesses.incrementAndGet() % 1000 == 0) {
            windows.entrySet().removeIf(e -> e.getValue().bucket < bucket);
        }
        if (count > limit) {
            return (bucket + 1) * windowSeconds - now.getEpochSecond();
        }
        return 0;
    }

    private static final class Window {
        final long bucket;
        final AtomicInteger count = new AtomicInteger();

        Window(long bucket) {
            this.bucket = bucket;
        }
    }
}
