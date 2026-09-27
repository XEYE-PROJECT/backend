package com.xeye.backend.list.application.port.out;

import com.xeye.backend.list.domain.model.ItemList;
import com.xeye.backend.shared.paging.Page;
import com.xeye.backend.shared.paging.Paging;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ListRepository {

    /**
     * Listas del usuario, id ascendente, filtradas por texto libre (nombre/descripción,
     * null = todas) y visibilidad (null = ambas).
     */
    Page<ItemList> findByUserId(Long userId, String query, Boolean isPublic, Paging paging);

    Optional<ItemList> findByIdAndUserId(Long id, Long userId);

    Optional<ItemList> findById(Long id);

    /** Paginación por clave para el snapshot del buscador: listas con id > afterId, id ascendente. */
    List<ItemList> findAfterId(long afterId, int limit);

    /** Nº de elementos de cada lista (solo aparecen las que tienen alguno). */
    Map<Long, Long> countElements(Collection<Long> listIds);

    ItemList save(ItemList list);

    void deleteById(Long id);
}
