package com.xeye.backend.element.application.port.in;

import com.xeye.backend.element.application.command.CreateElementCommand;
import com.xeye.backend.element.application.command.UpdateElementCommand;
import com.xeye.backend.element.domain.model.Element;
import com.xeye.backend.shared.paging.Page;
import com.xeye.backend.shared.paging.Paging;

import java.util.List;

/** Puerto de entrada: elementos de las listas propias del llamante. */
public interface ElementUseCases {

    /** @param query texto libre sobre text/description (null = sin filtro) */
    Page<Element> listByList(Long userId, Long listId, String query, Paging paging);

    Element create(Long userId, Long listId, CreateElementCommand command);

    /** Crea todos los elementos de una vez (inserción por lotes), solicitando un único reentrenamiento. */
    List<Element> importElements(Long userId, Long listId, List<CreateElementCommand> commands);

    Element update(Long userId, Long elementId, UpdateElementCommand command);

    void delete(Long userId, Long elementId);
}
