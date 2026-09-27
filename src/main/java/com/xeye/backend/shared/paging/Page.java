package com.xeye.backend.shared.paging;

import java.util.List;
import java.util.function.Function;

/** Una página de resultados: los elementos, el total sin paginar y la ventana pedida. */
public record Page<T>(List<T> items, long total, int offset, int limit) {

    public <R> Page<R> map(Function<T, R> mapper) {
        return new Page<>(items.stream().map(mapper).toList(), total, offset, limit);
    }

    public static <T> Page<T> empty(Paging paging) {
        return new Page<>(List.of(), 0, paging.offset(), paging.limit());
    }
}
