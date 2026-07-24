package com.xeye.backend.training.application.port.in;

import com.xeye.backend.training.application.command.TrainingUpdateCommand;

/**
 * Puerto interno de entrada para los callbacks de progreso/finalización (webhook y provider
 * mock). Al completar también marca los elementos {@code trained} y empuja el resultado a búsqueda.
 */
public interface TrainingCompletionHandler {

    void applyUpdate(TrainingUpdateCommand update);
}
