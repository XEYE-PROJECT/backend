package com.xeye.backend.element.application.port.out;

import com.xeye.backend.element.domain.model.Element;
import com.xeye.backend.shared.paging.Page;
import com.xeye.backend.shared.paging.Paging;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ElementRepository {

    /** Todos los elementos de la lista, id ascendente (payloads de training y búsqueda). */
    List<Element> findByListId(Long listId);

    /** Página de elementos de la lista, id ascendente, con filtro de texto libre opcional (text/description). */
    Page<Element> findByListId(Long listId, String query, Paging paging);

    long countByListId(Long listId);

    Optional<Element> findById(Long id);

    Element save(Element element);

    /** Inserción por lotes (una importación): devuelve los elementos creados con sus ids. */
    List<Element> saveAll(List<Element> elements);

    void deleteById(Long id);

    void updateTrainedByListId(Long listId, boolean trained);

    /** Acotado por lista a propósito: un cuerpo de webhook no debe poder escribir en otra lista. */
    void updateGeneratedDescriptions(Long listId, Map<Long, String> byElementId);
}
