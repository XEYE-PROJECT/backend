package com.xeye.backend.training.application.port.in;

import com.xeye.backend.training.domain.model.Training;

import java.util.List;
import java.util.Optional;

/**
 * Puerto interno para el módulo search: el training activo ({@code in_use}) de una lista,
 * si lo hay, y los modelos de embedding disponibles (búsqueda los precalienta al arrancar).
 */
public interface TrainingQueryPort {

    Optional<Training> findInUseByListId(Long listId);

    List<String> availableEmbeddingModels();
}
