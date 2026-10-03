package pl.aniolstroz.stt;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import pl.aniolstroz.config.Trace;

/**
 * Loads the Vosk model once, on first use, and shares it between calls (a model is big and thread safe for creating
 * recognizers). Closed when the application stops. Loading is guarded by a {@link ReentrantLock} (CC-03).
 *
 * <p>Vosk logging is set to warnings only before the model is loaded: at the default level the native library prints
 * progress lines, and nothing recognised may reach a log (AUD-02, OBS-05).
 */
public class VoskModelHolder {

    private static final Logger log = LoggerFactory.getLogger(VoskModelHolder.class);
    static {
        // The native Vosk library answers in UTF-8, but JNA decodes native strings with the platform encoding unless told
        // otherwise: on a Polish Windows (native.encoding=Cp1250) "dzień" arrives as "dzieĹ„", which breaks the keywords
        // and the text sent to Claude. JNA reads this property once, when it is first used, so it is set before any Vosk call.
        System.setProperty("jna.encoding", "UTF-8");
    }

    static final String MISSING_MODEL = "Brak modelu rozpoznawania mowy";
    static final String NATIVE_FAILURE = "Nie udało się uruchomić rozpoznawania mowy";

    private final Path modelPath;
    private final ReentrantLock lock = new ReentrantLock();
    private Model model;

    public VoskModelHolder(Path modelPath) {
        this.modelPath = modelPath;
    }

    /** The shared model, loaded on the first call. */
    public Model model() throws SttUnavailableException {
        lock.lock();
        try {
            if (model != null) {
                return model;
            }
            if (!Files.isDirectory(modelPath)) {
                Trace.flow("vosk | model directory {} does not exist", modelPath.toAbsolutePath());
                throw new SttUnavailableException(MISSING_MODEL);
            }
            try {
                // The native library prints progress lines to stderr: wanted when diagnosing a run, noise otherwise.
                LibVosk.setLogLevel(Trace.content() ? LogLevel.INFO : LogLevel.WARNINGS);
                Trace.flow("vosk | loading the model from {} ({} MB on disk)", modelPath.toAbsolutePath(), sizeInMb(modelPath));
                long began = System.nanoTime();
                model = new Model(modelPath.toString());
                log.info("Vosk model loaded");
                Trace.flow("vosk | model loaded in {} ms, shared by all calls", (System.nanoTime() - began) / 1_000_000L);
                return model;
            } catch (IOException e) {
                Trace.flow("vosk | the model could not be read: {}", e.getClass().getName());
                throw new SttUnavailableException(MISSING_MODEL, e);
            } catch (UnsatisfiedLinkError | NoClassDefFoundError | ExceptionInInitializerError e) {
                // The native library of Vosk (or JNA) could not be loaded on this machine.
                Trace.flow("vosk | the native library could not be loaded: {}", e.getClass().getName());
                throw new SttUnavailableException(NATIVE_FAILURE, e);
            }
        } finally {
            lock.unlock();
        }
    }

    private static long sizeInMb(Path dir) {
        try (var files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).mapToLong(f -> f.toFile().length()).sum() / (1024 * 1024);
        } catch (IOException | RuntimeException e) {
            return -1;
        }
    }

    @PreDestroy
    public void close() {
        lock.lock();
        try {
            if (model != null) {
                model.close();
                model = null;
            }
        } finally {
            lock.unlock();
        }
    }
}
