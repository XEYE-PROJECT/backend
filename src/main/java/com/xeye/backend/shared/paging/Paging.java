package com.xeye.backend.shared.paging;

/**
 * Ventana de paginación por offset ya saneada: {@code offset >= 0} y {@code 1 <= limit <= max}.
 * Los controladores la construyen con {@link #of} a partir de los query params y el máximo del
 * endpoint; los servicios nunca ven valores fuera de rango.
 */
public record Paging(int offset, int limit) {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 200;

    public Paging {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be >= 0");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
    }

    public static Paging of(Integer offset, Integer limit) {
        return of(offset, limit, MAX_LIMIT);
    }

    public static Paging of(Integer offset, Integer limit, int maxLimit) {
        int safeOffset = offset == null ? 0 : Math.max(0, offset);
        int wanted = limit == null ? Math.min(DEFAULT_LIMIT, maxLimit) : limit;
        int safeLimit = Math.max(1, Math.min(wanted, maxLimit));
        return new Paging(safeOffset, safeLimit);
    }

    /** Página de Spring Data equivalente (el offset se redondea hacia abajo al múltiplo del límite). */
    public int pageNumber() {
        return offset / limit;
    }
}
