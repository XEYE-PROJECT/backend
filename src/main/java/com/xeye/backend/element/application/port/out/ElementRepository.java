package com.xeye.backend.element.application.port.out;

import com.xeye.backend.element.domain.model.Element;

import java.util.List;
import java.util.Optional;

public interface ElementRepository {

    List<Element> findByListId(Long listId);

    Optional<Element> findById(Long id);

    Element save(Element element);

    void deleteById(Long id);

    void updateTrainedByListId(Long listId, boolean trained);

    /** Acotado por lista a propósito: un cuerpo de webhook no debe poder escribir en otra lista. */
    void updateGeneratedDescription(Long listId, Long elementId, String generatedDescription);
}
