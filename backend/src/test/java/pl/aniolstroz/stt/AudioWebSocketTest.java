package pl.aniolstroz.stt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import pl.aniolstroz.call.CallService;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.settings.ConsentChecker;
import pl.aniolstroz.settings.ProtectionService;

/** /ws/audio end to end with the fake recognizer: no Vosk, no model (TST-02). */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.events.heartbeat-interval-ms=3600000", "app.stt.provider=fake"})
class AudioWebSocketTest {

    /** The test decides what the recognizer "hears" and whether it can start. */
    static final class ControlledFactory implements SttProviderFactory {
        volatile List<String> script = List.of("dzień dobry", "mówi policja");
        volatile int framesPerUtterance = 2;
        volatile SttUnavailableException preflightError;
        final List<FakeSttProvider> created = new CopyOnWriteArrayList<>();

        @Override
        public void preflight() throws SttUnavailableException {
            if (preflightError != null) {
                throw preflightError;
            }
        }

        @Override
        public SttProvider create(SttStatusSink status) {
            var provider = new FakeSttProvider(script, framesPerUtterance);
            created.add(provider);
            return provider;
        }
    }

    static final ControlledFactory FACTORY = new ControlledFactory();
    static final AtomicBoolean CONSENT = new AtomicBoolean(true);

    @TestConfiguration(proxyBeanMethods = false)
    static class Config {
        @Bean
        @Primary
        SttProviderFactory controlledFactory() {
            return FACTORY;
        }

        @Bean
        @Primary
        ConsentChecker controlledConsent() {
            return CONSENT::get;
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    CallService calls;

    @Autowired
    ProtectionService protection;

    @Autowired
    ObjectMapper mapper;

    /** Collects text frames (events) or only the close status (audio). */
    static final class Client extends AbstractWebSocketHandler {
        final BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
        /** Every event that awaitEvent took from the queue, whether or not it matched. */
        final List<JsonNode> seen = new CopyOnWriteArrayList<>();
        final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
        private final ObjectMapper mapper;
        WebSocketSession session;

        Client(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        protected void handleTextMessage(WebSocketSession s, TextMessage message) throws Exception {
            events.add(mapper.readTree(message.getPayload()));
        }

        @Override
        public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
            closed.complete(status);
        }

        void send(String json) throws Exception {
            session.sendMessage(new TextMessage(json));
        }

        void sendFrames(int count) throws Exception {
            for (int i = 0; i < count; i++) {
                session.sendMessage(new BinaryMessage(new byte[3200]));
            }
        }

        CloseStatus awaitClose() throws Exception {
            return closed.get(5, TimeUnit.SECONDS);
        }

        /** Waits for an event for which the predicate holds; returns it. */
        JsonNode awaitEvent(java.util.function.Predicate<JsonNode> predicate) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                JsonNode event = events.poll(100, TimeUnit.MILLISECONDS);
                if (event != null) {
                    seen.add(event);
                }
                if (event != null && predicate.test(event)) {
                    return event;
                }
            }
            throw new AssertionError("expected event did not arrive within 5 s");
        }
    }

    private final List<Client> clients = new ArrayList<>();

    private Client connect(String path) throws Exception {
        var client = new Client(mapper);
        client.session = new StandardWebSocketClient()
                .execute(client, "ws://localhost:" + port + path)
                .get(5, TimeUnit.SECONDS);
        clients.add(client);
        return client;
    }

    private Client watchEvents() throws Exception {
        Client events = connect("/ws/events?role=family");
        Thread.sleep(300); // registered right after the handshake
        return events;
    }

    private void awaitNoActiveCall() {
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(calls.active()).isEmpty());
    }

    private static boolean isType(JsonNode event, String type) {
        return type.equals(event.get("type").asText());
    }

    private static boolean isStatus(JsonNode event, String component, String state) {
        return isType(event, "system.status") && component.equals(event.at("/payload/component").asText())
                && state.equals(event.at("/payload/state").asText());
    }

    @BeforeEach
    void reset() {
        FACTORY.script = List.of("dzień dobry", "mówi policja");
        FACTORY.framesPerUtterance = 2;
        FACTORY.preflightError = null;
        FACTORY.created.clear();
        CONSENT.set(true);
        protection.set(true);
    }

    @AfterEach
    void cleanUp() throws Exception {
        for (Client client : clients) {
            if (client.session.isOpen()) {
                client.session.close();
            }
        }
        clients.clear();
        calls.end(); // a call that a test started directly (not through /ws/audio)
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(calls.active()).isEmpty());
    }

    @Test
    void startStreamsInterimAndFinalSegmentsAndStopEndsTheCall() throws Exception {
        Client events = watchEvents();
        Client audio = connect("/ws/audio");

        audio.send("{\"type\":\"start\"}");

        JsonNode started = events.awaitEvent(e -> isType(e, "call.started"));
        assertThat(started.get("mode").asText()).isEqualTo("LIVE");
        events.awaitEvent(e -> isStatus(e, "stt", "ok"));
        events.awaitEvent(e -> isStatus(e, "audio", "ok"));
        assertThat(calls.active()).isPresent();
        assertThat(calls.active().get().mode()).isEqualTo(Mode.LIVE);

        audio.sendFrames(4);

        JsonNode interim = events.awaitEvent(e -> isType(e, "transcript.segment"));
        assertThat(interim.at("/payload/text").asText()).isEqualTo("dzień dobry");
        assertThat(interim.at("/payload/isFinal").asBoolean()).isFalse();
        assertThat(interim.at("/payload/speaker").asText()).isEqualTo("unknown");
        assertThat(interim.get("mode").asText()).isEqualTo("LIVE");
        JsonNode first = events.awaitEvent(
                e -> isType(e, "transcript.segment") && e.at("/payload/isFinal").asBoolean());
        assertThat(first.at("/payload/text").asText()).isEqualTo("dzień dobry");
        assertThat(first.at("/payload/segId").asText()).isEqualTo("s1");
        JsonNode second = events.awaitEvent(
                e -> isType(e, "transcript.segment") && e.at("/payload/isFinal").asBoolean());
        assertThat(second.at("/payload/text").asText()).isEqualTo("mówi policja");
        assertThat(second.at("/payload/segId").asText()).isEqualTo("s2");

        audio.send("{\"type\":\"stop\"}");

        events.awaitEvent(e -> isType(e, "call.ended"));
        assertThat(audio.awaitClose().getCode()).isEqualTo(1000);
        awaitNoActiveCall(); // call.ended is published just before the slot is cleared
        assertThat(FACTORY.created).hasSize(1);
        assertThat(FACTORY.created.get(0).isStopped()).isTrue();
    }

    @Test
    void pauseStopsAudioReachingTheRecognizerAndResumeRestoresIt() throws Exception {
        FACTORY.script = List.of("pierwszy", "drugi", "trzeci");
        FACTORY.framesPerUtterance = 1;
        Client events = watchEvents();
        Client audio = connect("/ws/audio");
        audio.send("{\"type\":\"start\"}");
        events.awaitEvent(e -> isType(e, "call.started"));

        audio.send("{\"type\":\"pause\"}");
        JsonNode paused = events.awaitEvent(e -> isStatus(e, "audio", "degraded"));
        assertThat(paused.at("/payload/message").asText()).isEqualTo("Ochrona wstrzymana przez użytkownika");
        audio.sendFrames(3); // dropped
        audio.send("{\"type\":\"resume\"}");
        events.awaitEvent(e -> isStatus(e, "audio", "ok"));
        audio.sendFrames(1);

        JsonNode segment = events.awaitEvent(e -> isType(e, "transcript.segment"));
        assertThat(segment.at("/payload/text").asText()).isEqualTo("pierwszy");
    }

    @Test
    void closingTheSocketWithoutStopEndsTheCallAndReleasesTheRecognizer() throws Exception {
        Client events = watchEvents();
        Client audio = connect("/ws/audio");
        audio.send("{\"type\":\"start\"}");
        events.awaitEvent(e -> isType(e, "call.started"));

        audio.session.close();

        events.awaitEvent(e -> isType(e, "call.ended"));
        awaitNoActiveCall(); // call.ended is published just before the slot is cleared
        await().atMost(5, TimeUnit.SECONDS).until(() -> FACTORY.created.get(0).isStopped());
    }

    @Test
    void withProtectionSwitchedOffStartIsRefusedWith1008AndNoCallStarts() throws Exception {
        protection.set(false);
        Client audio = connect("/ws/audio");

        audio.send("{\"type\":\"start\"}");

        CloseStatus status = audio.awaitClose();
        assertThat(status.getCode()).isEqualTo(1008);
        assertThat(status.getReason()).isEqualTo(AudioWebSocketHandler.PROTECTION_OFF);
        assertThat(calls.active()).isEmpty();
        assertThat(FACTORY.created).isEmpty();
    }

    @Test
    void aLostConnectionMakesAudioDownBecauseNothingListensAnyMore() throws Exception {
        Client events = watchEvents();
        Client audio = connect("/ws/audio");
        audio.send("{\"type\":\"start\"}");
        events.awaitEvent(e -> isStatus(e, "audio", "ok"));

        audio.session.close(); // no stop: the tablet lost the network, the page was closed

        events.awaitEvent(e -> isType(e, "call.ended")); // the call ends first, then the status is published
        JsonNode down = events.awaitEvent(e -> isStatus(e, "audio", "down"));
        assertThat(down.at("/payload/message").asText()).isEqualTo(AudioWebSocketHandler.CONNECTION_LOST);
        assertThat(down.get("mode").asText()).isEqualTo("LIVE");
    }

    @Test
    void aCleanStopLeavesNoAudioDownBehind() throws Exception {
        Client events = watchEvents();
        Client audio = connect("/ws/audio");
        audio.send("{\"type\":\"start\"}");
        events.awaitEvent(e -> isStatus(e, "audio", "ok"));

        audio.send("{\"type\":\"stop\"}");

        events.awaitEvent(e -> isType(e, "call.ended"));
        assertThat(audio.awaitClose().getCode()).isEqualTo(1000);
        awaitNoActiveCall();
        assertThat(events.events).noneMatch(e -> isStatus(e, "audio", "down"));
    }

    @Test
    void aControlMessageWithAFieldThatIsNotInTheContractIsRefused() throws Exception {
        Client audio = connect("/ws/audio");

        audio.send("{\"type\":\"start\",\"callId\":\"x\"}");

        assertThat(audio.awaitClose().getCode()).isEqualTo(1008);
        assertThat(calls.active()).isEmpty();
        assertThat(FACTORY.created).isEmpty();
    }

    @Test
    void switchingProtectionOffEndsTheRunningCallAndSaysSo() throws Exception {
        Client events = watchEvents();
        Client audio = connect("/ws/audio");
        audio.send("{\"type\":\"start\"}");
        events.awaitEvent(e -> isType(e, "call.started"));
        events.awaitEvent(e -> isStatus(e, "audio", "ok"));

        protection.set(false);

        CloseStatus status = audio.awaitClose();
        assertThat(status.getCode()).isEqualTo(1008);
        assertThat(status.getReason()).isEqualTo(AudioWebSocketHandler.PROTECTION_OFF);
        events.awaitEvent(e -> isType(e, "call.ended"));
        JsonNode degraded = events.awaitEvent(e -> isStatus(e, "audio", "degraded"));
        assertThat(degraded.at("/payload/message").asText()).isEqualTo(AudioWebSocketHandler.PROTECTION_OFF);
        awaitNoActiveCall();
        // A deliberate switch-off is not a lost connection.
        assertThat(events.seen).noneMatch(e -> isStatus(e, "audio", "down"));
    }

    @Test
    void withoutConsentTheSessionIsClosedWith1008AndNoCallStarts() throws Exception {
        CONSENT.set(false);
        Client audio = connect("/ws/audio");

        audio.send("{\"type\":\"start\"}");

        CloseStatus status = audio.awaitClose();
        assertThat(status.getCode()).isEqualTo(1008);
        assertThat(status.getReason()).startsWith("Brak zgody");
        assertThat(calls.active()).isEmpty();
        assertThat(FACTORY.created).isEmpty();
    }

    @Test
    void withoutTheModelSttIsDownTheSessionIsClosedWith1011AndNoCallStarts() throws Exception {
        FACTORY.preflightError = new SttUnavailableException("Brak modelu rozpoznawania mowy");
        Client events = watchEvents();
        Client audio = connect("/ws/audio");

        audio.send("{\"type\":\"start\"}");

        JsonNode down = events.awaitEvent(e -> isStatus(e, "stt", "down"));
        assertThat(down.at("/payload/message").asText()).isEqualTo("Brak modelu rozpoznawania mowy");
        assertThat(down.get("mode").asText()).isEqualTo("LIVE");
        CloseStatus status = audio.awaitClose();
        assertThat(status.getCode()).isEqualTo(1011);
        assertThat(status.getReason()).isEqualTo("Brak modelu rozpoznawania mowy");
        assertThat(calls.active()).isEmpty();
        assertThat(events.events).noneMatch(e -> isType(e, "call.started"));
    }

    @Test
    void aSecondStartWhileACallIsActiveIsRejectedInPolish() throws Exception {
        Client first = connect("/ws/audio");
        first.send("{\"type\":\"start\"}");
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(calls.active()).isPresent());
        String callId = calls.active().get().callId();
        Client second = connect("/ws/audio");

        second.send("{\"type\":\"start\"}");

        CloseStatus status = second.awaitClose();
        assertThat(status.getCode()).isEqualTo(1008);
        assertThat(status.getReason()).startsWith("Rozmowa już trwa");
        assertThat(calls.active().get().callId()).isEqualTo(callId);
        assertThat(first.session.isOpen()).isTrue();
    }

    @Test
    void aCallOfAnotherModeBlocksLiveStartToo() throws Exception {
        calls.start(Mode.SCRIPTED);
        Client audio = connect("/ws/audio");

        audio.send("{\"type\":\"start\"}");

        assertThat(audio.awaitClose().getCode()).isEqualTo(1008);
        assertThat(calls.active().get().mode()).isEqualTo(Mode.SCRIPTED);
    }

    @Test
    void audioBeforeStartAndUnknownCommandsAreRefused() throws Exception {
        Client noStart = connect("/ws/audio");
        noStart.sendFrames(1);
        assertThat(noStart.awaitClose().getCode()).isEqualTo(1008);

        Client unknown = connect("/ws/audio");
        unknown.send("{\"type\":\"explode\"}");
        assertThat(unknown.awaitClose().getCode()).isEqualTo(1008);

        Client garbage = connect("/ws/audio");
        garbage.send("not json");
        assertThat(garbage.awaitClose().getCode()).isEqualTo(1008);
        assertThat(calls.active()).isEmpty();
    }

    @Test
    void messagesOver64KbAreRefusedByTheContainer() throws Exception {
        Client audio = connect("/ws/audio");
        audio.send("{\"type\":\"start\"}");
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(calls.active()).isPresent());

        audio.session.sendMessage(new BinaryMessage(new byte[70 * 1024]));

        assertThat(audio.awaitClose().getCode()).isEqualTo(1009);
    }
}
