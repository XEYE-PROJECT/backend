package com.xeye.backend.training.application.port.out;

import com.xeye.backend.training.application.command.TrainingLaunchCommand;

/** Puerto de salida que envía un job de training. Devuelve el id de la instancia del worker. */
public interface TrainingLauncher {

    String launch(TrainingLaunchCommand command);
}
