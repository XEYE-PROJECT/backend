package com.xeye.backend.shared.paging;

import java.util.List;
import java.util.function.Function;

/** Envoltorio JSON de todo listado paginado: {@code {items, total, offset, limit}}. */
public record PageResponse<T>(List<T> items, long total, int offset, int limit) {

    public static <T, R> PageResponse<R> from(Page<T> page, Function<T, R> mapper) {
        return new PageResponse<>(page.items().stream().map(mapper).toList(),
                page.total(), page.offset(), page.limit());
    }
}
