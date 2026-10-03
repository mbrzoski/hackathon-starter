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

/**
 * Loads the Vosk model once, on first use, and shares it between calls (a model is big and thread safe for creating
 * recognizers). Closed when the application stops. Loading is guarded by a {@link ReentrantLock} (CC-03).
 *
 * <p>Vosk logging is set to warnings only before the model is loaded: at the default level the native library prints
 * progress lines, and nothing recognised may reach a log (AUD-02, OBS-05).
 */
public class VoskModelHolder {

    private static final Logger log = LoggerFactory.getLogger(VoskModelHolder.class);
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
                throw new SttUnavailableException(MISSING_MODEL);
            }
            try {
                LibVosk.setLogLevel(LogLevel.WARNINGS);
                model = new Model(modelPath.toString());
                log.info("Vosk model loaded");
                return model;
            } catch (IOException e) {
                throw new SttUnavailableException(MISSING_MODEL, e);
            } catch (UnsatisfiedLinkError | NoClassDefFoundError | ExceptionInInitializerError e) {
                // The native library of Vosk (or JNA) could not be loaded on this machine.
                throw new SttUnavailableException(NATIVE_FAILURE, e);
            }
        } finally {
            lock.unlock();
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
