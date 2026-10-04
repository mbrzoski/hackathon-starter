package pl.aniolstroz.stt;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import pl.aniolstroz.call.CallService;
import pl.aniolstroz.call.CallState;
import pl.aniolstroz.call.NoActiveCallException;
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.EventEnvelope.SystemStatusEvent;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.SystemStatus;
import pl.aniolstroz.contracts.TranscriptSegment;
import pl.aniolstroz.events.EventBus;

/**
 * REPLAY mode (BE-11): a recording from {@code recordings/<scenarioId>.wav} (PCM 16 kHz, mono, 16-bit, the format
 * of /ws/audio) is fed to the local recognizer in 100 ms frames at the pace of the recording (divided by
 * {@code speed}), so the call goes through the same STT, keyword, AI and risk path as a LIVE one. Recordings are the
 * only audio on disk (AUD-02 exception, described in the README); nothing heard is logged. One call at a time (BE-04).
 */
@Service
public class AudioReplayer {

    private static final Logger log = LoggerFactory.getLogger(AudioReplayer.class);
    private static final Pattern SAFE_ID = Pattern.compile("^[a-z0-9-]+$");
    static final int FRAME_BYTES = 3200;
    static final int SAMPLE_RATE = 16000;

    private final Path dir;
    private final CallService calls;
    private final SttProviderFactory factory;
    private final EventBus eventBus;
    private final Clock clock;
    private final RetryScheduler retries;
    private final Executor executor;
    private final Pacer pacer;

    private final ReentrantLock lock = new ReentrantLock();
    private Thread playback;

    /** Waits between frames; replaced in tests. */
    @FunctionalInterface
    public interface Pacer {
        void pause(long millis) throws InterruptedException;
    }

    @org.springframework.beans.factory.annotation.Autowired
    AudioReplayer(@Value("${app.stt.recordings-dir:recordings}") String dir, CallService calls,
            SttProviderFactory factory, EventBus eventBus, Clock clock, RetryScheduler retries,
            @Qualifier("sttExecutor") Executor executor) {
        this(Path.of(dir), calls, factory, eventBus, clock, retries, executor, Thread::sleep);
    }

    AudioReplayer(Path dir, CallService calls, SttProviderFactory factory, EventBus eventBus, Clock clock,
            RetryScheduler retries, Executor executor, Pacer pacer) {
        this.dir = dir;
        this.calls = calls;
        this.factory = factory;
        this.eventBus = eventBus;
        this.clock = clock;
        this.retries = retries;
        this.executor = executor;
        this.pacer = pacer;
    }

    /** True when there is a recording for the scenario. */
    public boolean has(String scenarioId) {
        return SAFE_ID.matcher(scenarioId).matches() && Files.isRegularFile(file(scenarioId));
    }

    /**
     * Starts the call (in {@code mode}: REPLAY, or MOCK when the AI answers are canned) and the playback.
     *
     * @throws RecordingNotFoundException no recording for the scenario
     * @throws SttUnavailableException speech recognition cannot run (for example the model is missing)
     * @throws pl.aniolstroz.call.CallAlreadyActiveException a call is already running
     */
    public void start(String scenarioId, Mode mode, double speed) throws SttUnavailableException {
        if (!(speed > 0)) {
            throw new IllegalArgumentException("speed must be positive");
        }
        if (!has(scenarioId)) {
            throw new RecordingNotFoundException(scenarioId);
        }
        byte[] pcm;
        try {
            pcm = readPcm(file(scenarioId));
        } catch (IOException e) {
            throw new RecordingNotFoundException(scenarioId);
        }
        factory.preflight();
        lock.lock();
        try {
            CallState call = calls.start(mode, scenarioId);
            var status = statusFor(mode);
            var supervisor = new SttSupervisor(factory, segment -> forward(call.callId(), segment), status, clock,
                    retries, executor);
            try {
                supervisor.start();
            } catch (RuntimeException | Error e) {
                log.error("Speech recognition did not start for the replay: {}", e.getClass().getName());
                status.publish(pl.aniolstroz.contracts.Component.STT, ComponentState.DOWN, SttSupervisor.DOWN);
                calls.end(call.callId());
                throw new SttUnavailableException(SttSupervisor.DOWN, e);
            }
            status.publish(pl.aniolstroz.contracts.Component.STT, ComponentState.OK, SttSupervisor.RUNNING);
            playback = Thread.ofVirtual().name("audio-replay-" + scenarioId)
                    .unstarted(() -> play(call, supervisor, pcm, speed));
            playback.start();
        } finally {
            lock.unlock();
        }
    }

    /** Stops the playback; the call ends with the last recognised words. */
    public void stop() {
        Thread running;
        lock.lock();
        try {
            running = playback;
        } finally {
            lock.unlock();
        }
        if (running == null) {
            return;
        }
        running.interrupt();
        try {
            running.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void play(CallState call, SttSupervisor supervisor, byte[] pcm, double speed) {
        try {
            long frameMs = Math.max(1, Math.round(100 / speed));
            for (int off = 0; off < pcm.length; off += FRAME_BYTES) {
                supervisor.write(Arrays.copyOfRange(pcm, off, Math.min(pcm.length, off + FRAME_BYTES)));
                pacer.pause(frameMs);
            }
        } catch (InterruptedException e) {
            // stop(): end the call with what was recognised so far
        } finally {
            try {
                supervisor.stop(); // flushes the last final segment into the call
            } finally {
                calls.end(call.callId());
                lock.lock();
                try {
                    if (playback == Thread.currentThread()) {
                        playback = null;
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
    }

    private void forward(String callId, SttSegment segment) {
        try {
            calls.addSegment(new TranscriptSegment(callId, "s0", segment.tStartMs(), segment.tEndMs(),
                    segment.text(), segment.isFinal(), segment.speaker(), segment.sttConfidence()));
        } catch (NoActiveCallException e) {
            // The replay was stopped while the segment was on its way.
        } catch (RuntimeException e) {
            log.error("Passing a replayed segment to the call failed: {}", e.getClass().getName());
        }
    }

    /** STT statuses of a replay carry the replay's mode, not LIVE (rule 6). */
    private StatusPublisher statusFor(Mode mode) {
        return (component, state, message) -> {
            var now = clock.instant();
            eventBus.publish(new SystemStatusEvent(mode, now, new SystemStatus(component, state, message, now)));
        };
    }

    private Path file(String scenarioId) {
        return dir.resolve(scenarioId + ".wav");
    }

    /** The PCM of a 16 kHz mono 16-bit WAV file; anything else is refused. */
    static byte[] readPcm(Path wav) throws IOException {
        byte[] bytes = Files.readAllBytes(wav);
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (bytes.length < 12 || !new String(bytes, 0, 4).equals("RIFF") || !new String(bytes, 8, 4).equals("WAVE")) {
            throw new IOException("not a WAV file");
        }
        int off = 12;
        boolean formatOk = false;
        while (off + 8 <= bytes.length) {
            String id = new String(bytes, off, 4);
            int size = b.getInt(off + 4);
            if (id.equals("fmt ")) {
                int format = b.getShort(off + 8);
                int channels = b.getShort(off + 10);
                int rate = b.getInt(off + 12);
                int bits = b.getShort(off + 22);
                formatOk = format == 1 && channels == 1 && rate == SAMPLE_RATE && bits == 16;
            } else if (id.equals("data")) {
                if (!formatOk) {
                    throw new IOException("the recording must be PCM 16 kHz, mono, 16-bit");
                }
                return Arrays.copyOfRange(bytes, off + 8, Math.min(bytes.length, off + 8 + size));
            }
            off += 8 + size + (size % 2);
        }
        throw new IOException("no data in the recording");
    }
}
