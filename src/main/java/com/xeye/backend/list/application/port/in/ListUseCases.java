package com.xeye.backend.list.application.port.in;

import com.xeye.backend.list.application.command.CreateListCommand;
import com.xeye.backend.list.application.command.UpdateListCommand;
import com.xeye.backend.list.domain.model.ItemList;

import java.util.List;

/** Puerto de entrada: las listas propias de un usuario. */
public interface ListUseCases {

    List<ItemList> listForUser(Long userId);

    ItemList get(Long userId, Long listId);

    ItemList create(Long userId, CreateListCommand command);

    ItemList update(Long userId, Long listId, UpdateListCommand command);

    void delete(Long userId, Long listId);
}
