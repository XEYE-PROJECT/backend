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

    /** Todas las listas de todos los usuarios — para el snapshot de bootstrap del módulo search. */
    List<ItemList> findAll();
}
