package pl.aniolstroz.stt;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import pl.aniolstroz.call.CallAlreadyActiveException;
import pl.aniolstroz.call.CallService;
import pl.aniolstroz.call.CallState;
import pl.aniolstroz.call.NoActiveCallException;
import pl.aniolstroz.config.AppProperties;
import pl.aniolstroz.contracts.AudioControl;
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.TranscriptSegment;
import pl.aniolstroz.settings.ConsentChecker;
import pl.aniolstroz.settings.ProtectionChanged;
import pl.aniolstroz.settings.ProtectionService;

/**
 * /ws/audio: the senior device sends binary frames (PCM 16 kHz, mono, 16-bit little endian, 3200 bytes = 100 ms) and
 * JSON control messages {@code {"type": "start" | "stop" | "pause" | "resume"}}. {@code start} arms the session: the
 * device listens, but a LIVE call (and local recognition) only opens when speech is heard, and ends after
 * {@code app.stt.call.silence-ms} (10 s) without speech; the session stays armed for the next call. {@code stop}, a
 * closed socket or a transport error end the call and release the recognizer. The half second before speech is kept in
 * memory only, so the first words reach the recognizer too (AUD-02).
 *
 * <p>Refusals close the socket with a Polish reason: 1008 without consent (AUD-07), when the caretaker switched
 * protection off, or when a call is already active (BE-04); 1011 when speech recognition cannot start. Audio, its
 * contents and what is recognised are never logged and never stored (AUD-02).
 */
@Component
class AudioWebSocketHandler extends BinaryWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AudioWebSocketHandler.class);

    static final String NO_CONSENT = "Brak zgody. Dokończ konfigurację.";
    static final String CALL_ACTIVE = "Rozmowa już trwa. Zakończ ją i spróbuj ponownie.";
    static final String PROTECTION_OFF = "Ochrona została wyłączona przez opiekuna w panelu.";
    static final String WAITING = "Ochrona włączona, czekam na urządzenie nasłuchujące";
    static final String NOT_STARTED = "Najpierw wyślij start.";
    static final String BAD_COMMAND = "Nieprawidłowe polecenie.";
    static final String RUNNING = "Rozpoznawanie mowy działa";
    static final String LISTENING = "Ochrona działa";
    static final String PAUSED = "Ochrona wstrzymana przez użytkownika";
    static final String NO_SOUND = "Brak dźwięku z mikrofonu. Ochrona nie działa";
    static final String CONNECTION_LOST = "Połączenie z mikrofonem zostało przerwane. Ochrona nie działa";

    private final CallService calls;
    private final SttProviderFactory factory;
    private final StatusPublisher status;
    private final ConsentChecker consent;
    private final ProtectionService protection;
    private final Clock clock;
    private final RetryScheduler retries;
    private final Executor executor;
    private final ObjectReader controlReader;
    private final Duration silenceTimeout;
    private final Duration callSilence;
    private final double speechRms;
    private final int speechFrames;
    private final Map<String, LiveAudio> sessions = new ConcurrentHashMap<>();

    AudioWebSocketHandler(CallService calls, SttProviderFactory factory, StatusPublisher status,
            ConsentChecker consent, ProtectionService protection, Clock clock, RetryScheduler retries,
            @Qualifier("sttExecutor") Executor executor, ObjectMapper mapper, AppProperties properties) {
        this.calls = calls;
        this.factory = factory;
        this.status = status;
        this.consent = consent;
        this.protection = protection;
        this.clock = clock;
        this.retries = retries;
        this.executor = executor;
        // Strict, like the schema AudioControl (additionalProperties: false, CON-04).
        this.controlReader = mapper.readerFor(AudioControl.class).with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.silenceTimeout = Duration.ofMillis(properties.stt().silence().timeoutMs());
        this.callSilence = Duration.ofMillis(properties.stt().call().silenceMs());
        this.speechRms = properties.stt().call().speechRms();
        this.speechFrames = properties.stt().call().speechFrames();
    }

    /** Frames kept before speech opens a call (500 ms), so the first words are not lost. Memory only (AUD-02). */
    static final int PRE_ROLL_FRAMES = 5;

    /** One armed connection; {@code callId} and {@code supervisor} are set while a call runs (guarded by lock). */
    private static final class LiveAudio {
        final WebSocketSession session;
        final SilenceWatch silence;
        final ReentrantLock lock = new ReentrantLock();
        final ArrayDeque<byte[]> preRoll = new ArrayDeque<>();
        volatile boolean paused;
        String callId;
        SttSupervisor supervisor;
        int speechRun;
        Instant lastSpeechAt;

        LiveAudio(WebSocketSession session, SilenceWatch silence) {
            this.session = session;
            this.silence = silence;
        }
    }

    /** A running call taken off its session, to be stopped outside the lock. */
    private record RunningCall(String callId, SttSupervisor supervisor) {
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            control(session, message.getPayload());
        } catch (IOException e) {
            log.debug("Closing /ws/audio failed: {}", e.getClass().getSimpleName());
        }
    }

    private void control(WebSocketSession session, String payload) throws IOException {
        AudioControl.Command command = commandOf(payload);
        if (command == null) {
            reject(session, CloseStatus.POLICY_VIOLATION, BAD_COMMAND);
            return;
        }
        LiveAudio live = sessions.get(session.getId());
        switch (command) {
            case START -> start(session, live);
            case STOP -> {
                finish(session.getId(), false);
                session.close(CloseStatus.NORMAL);
            }
            case PAUSE -> {
                if (live == null) {
                    reject(session, CloseStatus.POLICY_VIOLATION, NOT_STARTED);
                } else if (!live.paused) {
                    live.paused = true;
                    live.silence.pause();
                    status.publish(pl.aniolstroz.contracts.Component.AUDIO, ComponentState.DEGRADED, PAUSED);
                }
            }
            case RESUME -> {
                if (live == null) {
                    reject(session, CloseStatus.POLICY_VIOLATION, NOT_STARTED);
                } else if (live.paused) {
                    live.lock.lock();
                    try {
                        live.lastSpeechAt = clock.instant(); // a pause is not silence of the call either
                    } finally {
                        live.lock.unlock();
                    }
                    live.paused = false;
                    live.silence.resume();
                    status.publish(pl.aniolstroz.contracts.Component.AUDIO, ComponentState.OK, LISTENING);
                }
            }
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws IOException {
        LiveAudio live = sessions.get(session.getId());
        if (live == null) {
            reject(session, CloseStatus.POLICY_VIOLATION, NOT_STARTED);
            return;
        }
        if (live.paused) {
            return; // paused: the audio goes nowhere (AUD-02)
        }
        ByteBuffer payload = message.getPayload();
        byte[] pcm = new byte[payload.remaining()];
        payload.get(pcm);
        if (live.silence.onFrame(pcm) == SilenceWatch.Change.RECOVERED) {
            status.publish(pl.aniolstroz.contracts.Component.AUDIO, ComponentState.OK, LISTENING);
        }
        boolean speech = rms(pcm) >= speechRms;
        SttSupervisor target;
        List<byte[]> toWrite;
        live.lock.lock();
        try {
            if (live.supervisor != null) {
                if (speech) {
                    live.lastSpeechAt = clock.instant();
                }
                target = live.supervisor;
                toWrite = List.of(pcm);
            } else {
                live.preRoll.addLast(pcm);
                if (live.preRoll.size() > PRE_ROLL_FRAMES) {
                    live.preRoll.removeFirst();
                }
                live.speechRun = speech ? live.speechRun + 1 : 0;
                if (live.speechRun < speechFrames || !openCall(live)) {
                    return;
                }
                target = live.supervisor;
                toWrite = List.copyOf(live.preRoll);
                live.preRoll.clear();
                live.speechRun = 0;
            }
        } finally {
            live.lock.unlock();
        }
        toWrite.forEach(target::write);
    }

    /**
     * Speech on an armed session: opens a LIVE call and starts recognition. False when no call could open (another
     * call is running, for example a demo), so the session stays armed and tries again on the next speech. Caller
     * holds the session lock.
     */
    private boolean openCall(LiveAudio live) {
        CallState call;
        try {
            call = calls.start(Mode.LIVE);
        } catch (CallAlreadyActiveException e) {
            live.speechRun = 0;
            return false;
        }
        String callId = call.callId();
        var supervisor = new SttSupervisor(factory, segment -> forward(callId, segment), status, clock, retries,
                executor);
        try {
            supervisor.start();
        } catch (RuntimeException | Error e) {
            log.error("Speech recognition did not start: {}", e.getClass().getName());
            status.publish(pl.aniolstroz.contracts.Component.STT, ComponentState.DOWN, SttSupervisor.DOWN);
            calls.end(callId);
            live.speechRun = 0;
            return false;
        }
        live.callId = callId;
        live.supervisor = supervisor;
        live.lastSpeechAt = clock.instant();
        return true;
    }

    /** Takes the running call off the session, if any. The caller stops it outside the lock. */
    private static RunningCall takeCall(LiveAudio live) {
        live.lock.lock();
        try {
            if (live.supervisor == null) {
                return null;
            }
            var running = new RunningCall(live.callId, live.supervisor);
            live.callId = null;
            live.supervisor = null;
            live.speechRun = 0;
            live.preRoll.clear();
            return running;
        } finally {
            live.lock.unlock();
        }
    }

    /** Stops recognition, then ends the call, so the last segments still belong to it. */
    private void endCall(RunningCall running) {
        try {
            running.supervisor().stop();
        } finally {
            calls.end(running.callId());
        }
    }

    /** RMS of 16-bit little endian samples, in 0..32768. Only a number is kept, never the audio (AUD-02). */
    static double rms(byte[] pcm) {
        long sum = 0;
        int count = pcm.length / 2;
        for (int i = 0; i + 1 < pcm.length; i += 2) {
            int sample = (short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8));
            sum += (long) sample * sample;
        }
        return count == 0 ? 0 : Math.sqrt((double) sum / count);
    }

    /**
     * OBS-02: every few seconds, checks that sound still arrives. A stream without frames, or with only flat ones,
     * for the whole timeout makes {@code audio} down, so a muted microphone never shows "Ochrona działa". A call
     * without speech for {@code app.stt.call.silence-ms} ends; the session stays armed (a pause does not count).
     */
    @Scheduled(fixedDelayString = "${app.stt.silence.check-interval-ms:1000}")
    void checkSilence() {
        for (LiveAudio live : sessions.values()) {
            if (live.silence.check() == SilenceWatch.Change.SILENT) {
                status.publish(pl.aniolstroz.contracts.Component.AUDIO, ComponentState.DOWN, NO_SOUND);
            }
            RunningCall quiet = null;
            live.lock.lock();
            try {
                if (live.supervisor != null && !live.paused
                        && Duration.between(live.lastSpeechAt, clock.instant()).compareTo(callSilence) >= 0) {
                    quiet = takeCall(live);
                }
            } finally {
                live.lock.unlock();
            }
            if (quiet != null) {
                endCall(quiet);
            }
        }
    }

    private void start(WebSocketSession session, LiveAudio existing) throws IOException {
        if (existing != null) {
            reject(session, CloseStatus.POLICY_VIOLATION, CALL_ACTIVE);
            return;
        }
        if (!protection.enabled()) {
            reject(session, CloseStatus.POLICY_VIOLATION, PROTECTION_OFF);
            return;
        }
        if (!consent.hasConsent()) {
            reject(session, CloseStatus.POLICY_VIOLATION, NO_CONSENT);
            return;
        }
        try {
            factory.preflight();
        } catch (SttUnavailableException e) {
            // No call can open, but the family must see that protection does not work (rule 7).
            status.publish(pl.aniolstroz.contracts.Component.STT, ComponentState.DOWN, e.getMessage());
            reject(session, CloseStatus.SERVER_ERROR, e.getMessage());
            return;
        }
        // One listening device at a time, and none while another call (for example a demo) runs (BE-04).
        if (!sessions.isEmpty() || calls.active().isPresent()) {
            reject(session, CloseStatus.POLICY_VIOLATION, CALL_ACTIVE);
            return;
        }
        sessions.put(session.getId(), new LiveAudio(session, new SilenceWatch(clock, silenceTimeout)));
        status.publish(pl.aniolstroz.contracts.Component.STT, ComponentState.OK, RUNNING);
        status.publish(pl.aniolstroz.contracts.Component.AUDIO, ComponentState.OK, LISTENING);
    }

    /** Runs on a virtual thread, in order. A segment of a call that already ended is of no use. */
    private void forward(String callId, SttSegment segment) {
        try {
            calls.addSegment(new TranscriptSegment(callId, "s0", segment.tStartMs(), segment.tEndMs(),
                    segment.text(), segment.isFinal(), segment.speaker(), segment.sttConfidence()));
        } catch (NoActiveCallException e) {
            // The call ended while the segment was on its way.
        } catch (RuntimeException e) {
            log.error("Passing a segment to the call failed: {}", e.getClass().getName());
        }
    }

    /**
     * Stops recognition, then ends the call, so the last segments still belong to it.
     *
     * @param lost true when the connection ended without {@code stop} (the tablet lost the network, the page was
     *        closed): the call ends, but nothing listens any more, so {@code audio} goes down (OBS-01)
     */
    private void finish(String sessionId, boolean lost) {
        LiveAudio live = sessions.remove(sessionId);
        if (live == null) {
            return;
        }
        RunningCall running = takeCall(live);
        try {
            if (running != null) {
                endCall(running);
            }
        } finally {
            if (lost) {
                status.publish(pl.aniolstroz.contracts.Component.AUDIO, ComponentState.DOWN, CONNECTION_LOST);
            } else if (live.paused) {
                status.publish(pl.aniolstroz.contracts.Component.AUDIO, ComponentState.OK, LISTENING);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        finish(session.getId(), true); // after a clean stop the session is already gone, so this does nothing
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws IOException {
        log.debug("Transport error on /ws/audio: {}", exception.getClass().getSimpleName());
        finish(session.getId(), true);
        session.close(CloseStatus.SERVER_ERROR);
    }

    /**
     * The caretaker switched protection off or on. Off ends every running call and closes its socket with the reason;
     * the audio status says so, because nothing listens any more (rule 7). On: the device is asked to come back.
     */
    @EventListener
    void onProtectionChanged(ProtectionChanged event) {
        if (event.enabled()) {
            if (sessions.isEmpty()) {
                status.publish(pl.aniolstroz.contracts.Component.AUDIO, ComponentState.DEGRADED, WAITING);
            }
            return;
        }
        for (LiveAudio live : List.copyOf(sessions.values())) {
            finish(live.session.getId(), false); // not a lost connection: the status below says why
            try {
                reject(live.session, CloseStatus.POLICY_VIOLATION, PROTECTION_OFF);
            } catch (IOException e) {
                log.debug("Closing /ws/audio failed: {}", e.getClass().getSimpleName());
            }
        }
        status.publish(pl.aniolstroz.contracts.Component.AUDIO, ComponentState.DEGRADED, PROTECTION_OFF);
    }

    @PreDestroy
    void closeAll() {
        for (String id : List.copyOf(sessions.keySet())) {
            finish(id, false); // the application is stopping: nobody is left to tell
        }
    }

    private void reject(WebSocketSession session, CloseStatus code, String reason) throws IOException {
        session.close(code.withReason(reason));
    }

    /**
     * The command of a control message, or null if the message is not an {@code AudioControl} of the contract: an
     * unknown command, a missing type or an unknown field is refused (CON-04, CON-06).
     */
    private AudioControl.Command commandOf(String json) {
        try {
            AudioControl control = controlReader.readValue(json);
            return control == null ? null : control.type();
        } catch (IOException e) {
            return null;
        }
    }
}
