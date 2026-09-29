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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Provider local: un contenedor por training, igual que los de nube. El payload se escribe como
 * JSON en {@code input-dir} y el contenedor lo lee de {@code /data/input}, montado desde
 * {@code host-input-dir} — el mismo directorio tal como lo ve el daemon de docker; difieren
 * cuando el backend corre en contenedor, y esa es la única razón de que existan ambos ajustes.
 * Arranca detached ({@code -d --rm}); el resultado vuelve por el webhook, aquí nada lo espera.
 * <p>
 * El fichero del job contiene los textos de la lista y el token del webhook, así que: se crea
 * con permisos 600 (y se cede al uid del worker si este proceso es root), se borra en cuanto el
 * worker lo ha leído ({@link #release}: primer callback, lanzamiento fallido o run estancado) y,
 * como red de seguridad, los de más de un día se borran en cada lanzamiento. El secreto del
 * webhook no viaja: el job lleva su token por entrenamiento.
 */
@Component
@ConditionalOnProperty(name = "xeye.training.provider", havingValue = "docker")
public class DockerTrainingLauncher implements TrainingLauncher {

    private static final Logger log = LoggerFactory.getLogger(DockerTrainingLauncher.class);
    private static final int START_TIMEOUT_SECONDS = 60;
    private static final Duration JOB_FILE_RETENTION = Duration.ofDays(1);
    private static final Set<PosixFilePermission> OWNER_ONLY = PosixFilePermissions.fromString("rw-------");
    private static final Set<PosixFilePermission> OWNER_DIR = PosixFilePermissions.fromString("rwx------");

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

    /** El worker ya leyó el job (o nunca arrancó): el fichero con los textos de la lista sobra. */
    @Override
    public void release(Long trainingId) {
        Path file = Path.of(config.inputDir()).resolve(jobFileName(trainingId));
        try {
            if (Files.deleteIfExists(file)) {
                log.debug("Deleted job file of training {}", trainingId);
            }
        } catch (IOException ex) {
            log.warn("Could not delete job file {}: {}", file, ex.getMessage());
        }
    }

    private Result run(List<String> args) {
        log.debug("{}", String.join(" ", redacted(args)));
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
        String message = output.toLowerCase(Locale.ROOT);
        return message.contains("gpu") || message.contains("nvidia") || message.contains("cdi");
    }

    private record Result(int exitCode, String output) {

        boolean failed() {
            return exitCode != 0;
        }

        /** En un fallo de GPU docker imprime primero el id del contenedor y después el motivo. */
        String errorLine() {
            return output.lines()
                    .filter(line -> line.toLowerCase(Locale.ROOT).contains("error"))
                    .findFirst()
                    .orElseGet(() -> output.lines().reduce((first, second) -> second).orElse("?"));
        }
    }

    /** Los valores de {@code -e KEY=VALUE} (claves de LLM) nunca van al log. */
    private static List<String> redacted(List<String> args) {
        List<String> safe = new ArrayList<>(args.size());
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (i > 0 && "-e".equals(args.get(i - 1)) && arg.contains("=")) {
                safe.add(arg.substring(0, arg.indexOf('=')) + "=***");
            } else {
                safe.add(arg);
            }
        }
        return safe;
    }

    static String jobFileName(Long trainingId) {
        return "training-" + trainingId + ".json";
    }

    /**
     * Escribe el job de forma atómica (temporal 600 + rename) para que nunca exista un fichero
     * legible a medias ni con permisos abiertos; el directorio se crea solo para el propietario.
     */
    private Path writeJobFile(TrainingLaunchCommand command) {
        try {
            Path directory = Path.of(config.inputDir());
            createPrivateDirectory(directory);
            deleteStaleJobFiles(directory);
            Path file = directory.resolve(jobFileName(command.trainingId()));
            Path temp = directory.resolve(jobFileName(command.trainingId()) + ".tmp");
            Files.deleteIfExists(temp);
            if (posix()) {
                Files.createFile(temp, PosixFilePermissions.asFileAttribute(OWNER_ONLY));
            } else {
                Files.createFile(temp);
            }
            Files.writeString(temp, json.writeValueAsString(command), StandardCharsets.UTF_8);
            handOverToWorker(temp);
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return file;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not write the training job file: " + ex.getMessage(), ex);
        }
    }

    private static void createPrivateDirectory(Path directory) throws IOException {
        if (Files.isDirectory(directory)) {
            return;
        }
        if (posix()) {
            Files.createDirectories(directory, PosixFilePermissions.asFileAttribute(OWNER_DIR));
        } else {
            Files.createDirectories(directory);
        }
    }

    /**
     * Un fichero 600 solo lo lee su propietario: si este proceso corre como root (backend en
     * contenedor) se lo cede al uid del worker; si corre como el mismo usuario no hace falta y,
     * como usuario normal distinto, no se puede (el worker fallará al leerlo, con un error claro).
     */
    private void handOverToWorker(Path file) {
        String uid = config.workerUid();
        if (uid == null || uid.isBlank() || !posix()) {
            return;
        }
        try {
            UserPrincipal owner = Files.getOwner(file);
            UserPrincipal worker = file.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByName(uid.trim());
            if (!owner.equals(worker)) {
                Files.setOwner(file, worker);
            }
        } catch (IOException | UnsupportedOperationException ex) {
            log.debug("Job file stays owned by this process (could not chown to uid {}): {}", uid, ex.getMessage());
        }
    }

    private static boolean posix() {
        return FileSystems.getDefault().supportedFileAttributeViews().contains("posix");
    }

    /** Red de seguridad: un job de hace más de un día ya no necesita su fichero (contiene los textos de la lista). */
    private void deleteStaleJobFiles(Path directory) {
        Instant cutoff = Instant.now().minus(JOB_FILE_RETENTION);
        try (Stream<Path> files = Files.list(directory)) {
            files.filter(file -> file.getFileName().toString().matches("training-\\d+\\.json(\\.tmp)?"))
                    .filter(file -> lastModified(file).isBefore(cutoff))
                    .forEach(file -> {
                        try {
                            Files.deleteIfExists(file);
                        } catch (IOException ex) {
                            log.debug("Could not delete stale job file {}: {}", file, ex.getMessage());
                        }
                    });
        } catch (IOException | UncheckedIOException ex) {
            log.debug("Could not list job files in {}: {}", directory, ex.getMessage());
        }
    }

    private static Instant lastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (IOException ex) {
            return Instant.MAX;
        }
    }

    private List<String> dockerRunArgs(TrainingLaunchCommand command, String fileName, boolean withGpu) {
        List<String> args = new ArrayList<>(List.of(
                config.dockerBinary(), "run", "--rm", "-d",
                "--name", "xeye-training-" + command.trainingId(),
                "-v", config.hostInputDir() + ":/data/input:ro",
                "-e", "TRAINING_DATA_PATH=/data/input/" + fileName));
        // El worker exige https en el callback salvo que se le diga lo contrario: en local el
        // backend es http:// dentro de la red docker, y aquí quien lanza es el propio backend.
        if (command.callbackUrl() != null && command.callbackUrl().toLowerCase(Locale.ROOT).startsWith("http://")) {
            args.add("-e");
            args.add("CALLBACK_ALLOW_HTTP=true");
        }
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
