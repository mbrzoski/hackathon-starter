package pl.aniolstroz.stt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
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
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.TranscriptSegment;
import pl.aniolstroz.settings.ConsentChecker;

/**
 * /ws/audio: the senior device sends binary frames (PCM 16 kHz, mono, 16-bit little endian, 3200 bytes = 100 ms) and
 * JSON control messages {@code {"type": "start" | "stop" | "pause" | "resume"}}. {@code start} opens a LIVE call and
 * starts local recognition; {@code stop}, a closed socket or a transport error end it and release the recognizer.
 *
 * <p>Refusals close the socket with a Polish reason: 1008 without consent (AUD-07) or when a call is already active
 * (BE-04), 1011 when speech recognition cannot start. Audio, its contents and what is recognised are never logged
 * and never stored (AUD-02).
 */
@Component
class AudioWebSocketHandler extends BinaryWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AudioWebSocketHandler.class);

    static final String NO_CONSENT = "Brak zgody. Dokończ konfigurację.";
    static final String CALL_ACTIVE = "Rozmowa już trwa. Zakończ ją i spróbuj ponownie.";
    static final String NOT_STARTED = "Najpierw wyślij start.";
    static final String BAD_COMMAND = "Nieprawidłowe polecenie.";
    static final String RUNNING = "Rozpoznawanie mowy działa";
    static final String LISTENING = "Ochrona działa";
    static final String PAUSED = "Ochrona wstrzymana przez użytkownika";
    private static final List<String> COMMANDS = List.of("start", "stop", "pause", "resume");

    private final CallService calls;
    private final SttProviderFactory factory;
    private final StatusPublisher status;
    private final ConsentChecker consent;
    private final Clock clock;
    private final RetryScheduler retries;
    private final Executor executor;
    private final ObjectMapper mapper;
    private final Map<String, LiveAudio> sessions = new ConcurrentHashMap<>();

    AudioWebSocketHandler(CallService calls, SttProviderFactory factory, StatusPublisher status,
            ConsentChecker consent, Clock clock, RetryScheduler retries,
            @Qualifier("sttExecutor") Executor executor, ObjectMapper mapper) {
        this.calls = calls;
        this.factory = factory;
        this.status = status;
        this.consent = consent;
        this.clock = clock;
        this.retries = retries;
        this.executor = executor;
        this.mapper = mapper;
    }

    /** One connection with a running call. */
    private static final class LiveAudio {
        final String callId;
        final SttSupervisor supervisor;
        volatile boolean paused;

        LiveAudio(String callId, SttSupervisor supervisor) {
            this.callId = callId;
            this.supervisor = supervisor;
        }
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
        String type = commandOf(payload);
        if (type == null) {
            reject(session, CloseStatus.POLICY_VIOLATION, BAD_COMMAND);
            return;
        }
        LiveAudio live = sessions.get(session.getId());
        switch (type) {
            case "start" -> start(session, live);
            case "stop" -> {
                finish(session.getId());
                session.close(CloseStatus.NORMAL);
            }
            case "pause" -> {
                if (live == null) {
                    reject(session, CloseStatus.POLICY_VIOLATION, NOT_STARTED);
                } else if (!live.paused) {
                    live.paused = true;
                    status.publish(pl.aniolstroz.contracts.Component.AUDIO, ComponentState.DEGRADED, PAUSED);
                }
            }
            case "resume" -> {
                if (live == null) {
                    reject(session, CloseStatus.POLICY_VIOLATION, NOT_STARTED);
                } else if (live.paused) {
                    live.paused = false;
                    status.publish(pl.aniolstroz.contracts.Component.AUDIO, ComponentState.OK, LISTENING);
                }
            }
            default -> reject(session, CloseStatus.POLICY_VIOLATION, BAD_COMMAND);
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
        live.supervisor.write(pcm);
    }

    private void start(WebSocketSession session, LiveAudio existing) throws IOException {
        if (existing != null) {
            reject(session, CloseStatus.POLICY_VIOLATION, CALL_ACTIVE);
            return;
        }
        if (!consent.hasConsent()) {
            reject(session, CloseStatus.POLICY_VIOLATION, NO_CONSENT);
            return;
        }
        try {
            factory.preflight();
        } catch (SttUnavailableException e) {
            // No call is created, but the family must see that protection does not work (rule 7).
            status.publish(pl.aniolstroz.contracts.Component.STT, ComponentState.DOWN, e.getMessage());
            reject(session, CloseStatus.SERVER_ERROR, e.getMessage());
            return;
        }
        CallState call;
        try {
            call = calls.start(Mode.LIVE);
        } catch (CallAlreadyActiveException e) {
            reject(session, CloseStatus.POLICY_VIOLATION, CALL_ACTIVE);
            return;
        }
        String callId = call.callId();
        var supervisor = new SttSupervisor(factory, segment -> forward(callId, segment), status, clock, retries,
                executor);
        try {
            supervisor.start();
        } catch (RuntimeException | Error e) {
            log.error("Speech recognition did not start: {}", e.getClass().getName());
            status.publish(pl.aniolstroz.contracts.Component.STT, ComponentState.DOWN,
                    SttSupervisor.DOWN);
            calls.end(callId);
            reject(session, CloseStatus.SERVER_ERROR, "Nie udało się uruchomić rozpoznawania mowy");
            return;
        }
        sessions.put(session.getId(), new LiveAudio(callId, supervisor));
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

    /** Stops recognition, then ends the call, so the last segments still belong to it. */
    private void finish(String sessionId) {
        LiveAudio live = sessions.remove(sessionId);
        if (live == null) {
            return;
        }
        try {
            live.supervisor.stop();
        } finally {
            calls.end(live.callId);
            if (live.paused) {
                status.publish(pl.aniolstroz.contracts.Component.AUDIO, ComponentState.OK, LISTENING);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        finish(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws IOException {
        log.debug("Transport error on /ws/audio: {}", exception.getClass().getSimpleName());
        finish(session.getId());
        session.close(CloseStatus.SERVER_ERROR);
    }

    @PreDestroy
    void closeAll() {
        for (String id : List.copyOf(sessions.keySet())) {
            finish(id);
        }
    }

    private void reject(WebSocketSession session, CloseStatus code, String reason) throws IOException {
        session.close(code.withReason(reason));
    }

    /** The command of a control message, or null if it is not one of start, stop, pause, resume. */
    private String commandOf(String json) {
        try {
            JsonNode type = mapper.readTree(json).get("type");
            return type != null && type.isTextual() && COMMANDS.contains(type.asText()) ? type.asText() : null;
        } catch (IOException e) {
            return null;
        }
    }
}
