package com.xeye.backend.list.application.port.in;

import com.xeye.backend.list.domain.model.ItemList;

import java.util.List;
import java.util.Optional;

/**
 * Puerto interno para otros módulos (element, training, search): datos de listas sin las
 * comprobaciones de propiedad de cara al usuario; el llamante valida con {@link ItemList#userId()}.
 */
public interface ListQueryPort {

    Optional<ItemList> findById(Long listId);

    /**
     * Listas de todos los usuarios por páginas de clave (id > afterId, id ascendente) — para el
     * snapshot de bootstrap del módulo search, que las recorre hasta recibir menos de {@code limit}.
     */
    List<ItemList> findAfterId(long afterId, int limit);
}
