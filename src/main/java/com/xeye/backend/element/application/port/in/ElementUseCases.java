package com.xeye.backend.element.application.port.in;

import com.xeye.backend.element.application.command.CreateElementCommand;
import com.xeye.backend.element.application.command.UpdateElementCommand;
import com.xeye.backend.element.domain.model.Element;

import java.util.List;

/** Puerto de entrada: elementos de las listas propias del llamante. */
public interface ElementUseCases {

    List<Element> listByList(Long userId, Long listId);

    Element create(Long userId, Long listId, CreateElementCommand command);

    /** Crea todos los elementos de una vez, solicitando un único reentrenamiento de la lista. */
    List<Element> importElements(Long userId, Long listId, List<CreateElementCommand> commands);

    Element update(Long userId, Long elementId, UpdateElementCommand command);

    void delete(Long userId, Long elementId);
}
