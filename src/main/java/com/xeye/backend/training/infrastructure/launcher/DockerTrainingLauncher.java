package com.xeye.backend.training.infrastructure.launcher;

import com.xeye.backend.training.application.command.TrainingLaunchCommand;
import com.xeye.backend.training.application.port.out.TrainingLauncher;
import com.xeye.backend.training.config.TrainingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Provider local: un contenedor por training, igual que los de nube. El payload se escribe como
 * JSON en {@code input-dir} y el contenedor lo lee de {@code /data/input}, montado desde
 * {@code host-input-dir} — el mismo directorio tal como lo ve el daemon de docker; difieren
 * cuando el backend corre en contenedor, y esa es la única razón de que existan ambos ajustes.
 * Arranca detached ({@code -d --rm}); el resultado vuelve por el webhook, aquí nada lo espera.
 */
@Component
@ConditionalOnProperty(name = "xeye.training.provider", havingValue = "docker")
public class DockerTrainingLauncher implements TrainingLauncher {

    private static final Logger log = LoggerFactory.getLogger(DockerTrainingLauncher.class);
    private static final int START_TIMEOUT_SECONDS = 60;

    private final TrainingProperties.Docker config;
    private final ObjectMapper json;

    public DockerTrainingLauncher(TrainingProperties properties, ObjectMapper json) {
        this.config = properties.docker();
        this.json = json;
    }

    @Override
    public String launch(TrainingLaunchCommand command) {
        Path jobFile = writeJobFile(command);
        String fileName = jobFile.getFileName().toString();
        boolean wantsGpu = config.gpus() != null && !config.gpus().isBlank();

        log.info("Launching training {} for list {} in a container ({} elements, gpus={})",
                command.trainingId(), command.listId(), command.elements().size(),
                wantsGpu ? config.gpus() : "none");

        Result result = run(dockerRunArgs(command, fileName, wantsGpu));
        if (result.failed() && wantsGpu && looksLikeMissingGpu(result.output())) {
            // El daemon no puede dar una GPU (sin NVIDIA container toolkit o sin dispositivo).
            // Caer a CPU es mucho mejor que fallar el training.
            log.warn("Docker cannot provide a GPU ({}); rerunning training {} on CPU",
                    result.errorLine(), command.trainingId());
            result = run(dockerRunArgs(command, fileName, false));
        }
        if (result.failed()) {
            throw new IllegalStateException("`docker run` failed (exit " + result.exitCode() + "): "
                    + result.output());
        }

        // `docker run -d` imprime el id del contenedor.
        String containerId = result.output().lines().reduce((first, second) -> second).orElse("").trim();
        log.info("Training {} runs in container {}", command.trainingId(),
                containerId.substring(0, Math.min(12, containerId.length())));
        return containerId.isBlank() ? "docker-" + command.trainingId() : containerId;
    }

    private Result run(List<String> args) {
        log.debug("{}", String.join(" ", args));
        try {
            Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!process.waitFor(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException("`docker run` did not return within "
                        + START_TIMEOUT_SECONDS + "s");
            }
            return new Result(process.exitValue(), output);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not start the training container: " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while starting the training container", ex);
        }
    }

    private static boolean looksLikeMissingGpu(String output) {
        String message = output.toLowerCase();
        return message.contains("gpu") || message.contains("nvidia") || message.contains("cdi");
    }

    private record Result(int exitCode, String output) {

        boolean failed() {
            return exitCode != 0;
        }

        /** En un fallo de GPU docker imprime primero el id del contenedor y después el motivo. */
        String errorLine() {
            return output.lines()
                    .filter(line -> line.toLowerCase().contains("error"))
                    .findFirst()
                    .orElseGet(() -> output.lines().reduce((first, second) -> second).orElse("?"));
        }
    }

    private Path writeJobFile(TrainingLaunchCommand command) {
        try {
            Path directory = Path.of(config.inputDir());
            Files.createDirectories(directory);
            Path file = directory.resolve("training-" + command.trainingId() + ".json");
            Files.writeString(file, json.writeValueAsString(command), StandardCharsets.UTF_8);
            return file;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not write the training job file: " + ex.getMessage(), ex);
        }
    }

    private List<String> dockerRunArgs(TrainingLaunchCommand command, String fileName, boolean withGpu) {
        List<String> args = new ArrayList<>(List.of(
                config.dockerBinary(), "run", "--rm", "-d",
                "--name", "xeye-training-" + command.trainingId(),
                "-v", config.hostInputDir() + ":/data/input:ro",
                "-e", "TRAINING_DATA_PATH=/data/input/" + fileName));
        if (withGpu) {
            args.add("--gpus");
            args.add(config.gpus());
        }
        if (config.network() != null && !config.network().isBlank()) {
            args.add("--network");
            args.add(config.network());
        }
        for (String env : config.env()) {
            if (env != null && !env.isBlank()) {
                args.add("-e");
                args.add(env);
            }
        }
        args.add(config.image());
        // Comando explícito: el CMD por defecto de la imagen GPU es el handler de RunPod, y un
        // training lanzado desde aquí debe usar siempre el entrypoint de un solo uso.
        args.addAll(List.of("python", "-m", "app.entrypoints.cli"));
        return args;
    }
}
