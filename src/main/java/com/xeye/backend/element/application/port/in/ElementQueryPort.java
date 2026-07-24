package com.xeye.backend.element.application.port.in;

import com.xeye.backend.element.domain.model.Element;

import java.util.List;
import java.util.Map;

/**
 * Puerto interno para el módulo training: cargar los elementos de una lista para el payload,
 * conmutar su flag {@code trained} al iniciar/completar un entrenamiento y guardar los
 * enriquecimientos LLM del worker (el siguiente entrenamiento los reutiliza sin volver a pagarlos).
 */
public interface ElementQueryPort {

    List<Element> findByListId(Long listId);

    void markAllTrained(Long listId, boolean trained);

    /** Persiste el enriquecimiento del worker para los elementos indicados de {@code listId}. */
    void saveGeneratedDescriptions(Long listId, Map<Long, String> byElementId);
}
