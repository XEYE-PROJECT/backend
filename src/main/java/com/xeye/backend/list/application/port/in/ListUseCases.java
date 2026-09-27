package com.xeye.backend.list.application.port.in;

import com.xeye.backend.list.application.command.CreateListCommand;
import com.xeye.backend.list.application.command.UpdateListCommand;
import com.xeye.backend.list.domain.model.ItemList;
import com.xeye.backend.shared.paging.Page;
import com.xeye.backend.shared.paging.Paging;

/** Puerto de entrada: las listas propias de un usuario. */
public interface ListUseCases {

    /** Una lista con su nº de elementos (calculado en la misma consulta de listado). */
    record ListedList(ItemList list, long elementCount) {
    }

    /** @param query texto libre sobre nombre/descripción (null = sin filtro); @param isPublic null = ambas */
    Page<ListedList> listForUser(Long userId, String query, Boolean isPublic, Paging paging);

    ListedList get(Long userId, Long listId);

    ItemList create(Long userId, CreateListCommand command);

    ItemList update(Long userId, Long listId, UpdateListCommand command);

    void delete(Long userId, Long listId);
}
