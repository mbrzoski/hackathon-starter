package pl.aniolstroz.stt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import pl.aniolstroz.call.CallService;

/** OBS-02 end to end: a stream with no sound makes {@code audio} down, sound makes it ok again. Short timeout. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.events.heartbeat-interval-ms=3600000", "app.stt.provider=fake",
                "app.stt.silence.timeout-ms=600", "app.stt.silence.check-interval-ms=50",
                "app.stt.call.silence-ms=800"})
class AudioSilenceTest {

    private static final class Client extends AbstractWebSocketHandler {
        final BlockingQueue<JsonNode> events = new LinkedBlockingQueue<>();
        private final ObjectMapper mapper;
        WebSocketSession session;

        Client(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        protected void handleTextMessage(WebSocketSession s, TextMessage message) throws Exception {
            events.add(mapper.readTree(message.getPayload()));
        }

        JsonNode awaitStatus(String component, String state) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline) {
                JsonNode e = events.poll(100, TimeUnit.MILLISECONDS);
                if (e != null && "system.status".equals(e.get("type").asText())
                        && component.equals(e.at("/payload/component").asText())
                        && state.equals(e.at("/payload/state").asText())) {
                    return e;
                }
            }
            throw new AssertionError(component + " " + state + " did not arrive within 5 s");
        }
    }

    @LocalServerPort
    int port;

    @Autowired
    CallService calls;

    @Autowired
    ObjectMapper mapper;

    private final java.util.List<Client> clients = new java.util.ArrayList<>();

    private Client connect(String path) throws Exception {
        var client = new Client(mapper);
        client.session = new StandardWebSocketClient()
                .execute(client, "ws://localhost:" + port + path)
                .get(5, TimeUnit.SECONDS);
        clients.add(client);
        return client;
    }

    @Autowired
    pl.aniolstroz.settings.SettingsService settings;

    /** LIVE audio needs both consents (AUD-07). */
    @org.junit.jupiter.api.BeforeEach
    void consentGiven() {
        settings.save(new pl.aniolstroz.contracts.Settings(true, true, java.util.List.of(),
                pl.aniolstroz.contracts.Sensitivity.STANDARD, 30, ""));
    }

    @AfterEach
    void cleanUp() throws Exception {
        for (Client client : clients) {
            if (client.session.isOpen()) {
                client.session.close();
            }
        }
        calls.end();
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(calls.active()).isEmpty());
    }

    private static byte[] loudFrame() {
        byte[] frame = new byte[3200];
        frame[0] = (byte) 0xE8; // 1000 as a 16-bit little endian sample
        frame[1] = 0x03;
        return frame;
    }

    @Test
    void aStreamWithoutSoundMakesAudioDownAndSoundBringsItBack() throws Exception {
        Client events = connect("/ws/events?role=family");
        Thread.sleep(300);
        Client audio = connect("/ws/audio");
        audio.session.sendMessage(new TextMessage("{\"type\":\"start\"}"));
        events.awaitStatus("audio", "ok");

        audio.session.sendMessage(new BinaryMessage(new byte[3200])); // flat: a muted microphone

        JsonNode down = events.awaitStatus("audio", "down");
        assertThat(down.at("/payload/message").asText()).isEqualTo(AudioWebSocketHandler.NO_SOUND);
        assertThat(down.get("mode").asText()).isEqualTo("LIVE");

        audio.session.sendMessage(new BinaryMessage(loudFrame()));

        events.awaitStatus("audio", "ok");
    }

    @Test
    void aPauseIsNotSilence() throws Exception {
        Client events = connect("/ws/events?role=family");
        Thread.sleep(300);
        Client audio = connect("/ws/audio");
        audio.session.sendMessage(new TextMessage("{\"type\":\"start\"}"));
        events.awaitStatus("audio", "ok");

        audio.session.sendMessage(new TextMessage("{\"type\":\"pause\"}"));
        events.awaitStatus("audio", "degraded");
        Thread.sleep(1500); // well over the 600 ms timeout

        assertThat(events.events).noneMatch(e -> "audio".equals(e.at("/payload/component").asText())
                && "down".equals(e.at("/payload/state").asText()));
    }

    private static byte[] speechFrame() {
        byte[] frame = new byte[3200];
        for (int i = 0; i < frame.length; i += 2) {
            frame[i] = (byte) 0xE8;
            frame[i + 1] = 0x03;
        }
        return frame;
    }

    private static boolean isType(JsonNode event, String type) {
        return type.equals(event.get("type").asText());
    }

    private static JsonNode awaitType(Client events, String type) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            JsonNode e = events.events.poll(100, TimeUnit.MILLISECONDS);
            if (e != null && isType(e, type)) {
                return e;
            }
        }
        throw new AssertionError(type + " did not arrive within 5 s");
    }

    @Test
    void aCallOpensOnSpeechEndsAfterSilenceAndTheSessionStaysArmed() throws Exception {
        Client events = connect("/ws/events?role=family");
        Thread.sleep(300);
        Client audio = connect("/ws/audio");
        audio.session.sendMessage(new TextMessage("{\"type\":\"start\"}"));
        events.awaitStatus("audio", "ok");
        audio.session.sendMessage(new BinaryMessage(new byte[3200]));
        assertThat(calls.active()).isEmpty(); // listening is not a call

        for (int i = 0; i < 4; i++) {
            audio.session.sendMessage(new BinaryMessage(speechFrame()));
        }
        awaitType(events, "call.started");

        // Sound that is not speech (hiss, a hum) does not keep the call open: no speech for silence-ms ends it.
        for (int i = 0; i < 12; i++) {
            audio.session.sendMessage(new BinaryMessage(loudFrame()));
            Thread.sleep(100);
        }
        JsonNode ended = awaitType(events, "call.ended");
        assertThat(ended.at("/payload/hadAlert").asBoolean()).isFalse();
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(calls.active()).isEmpty());
        assertThat(audio.session.isOpen()).isTrue();

        // Still armed: the next speech opens a new call.
        for (int i = 0; i < 4; i++) {
            audio.session.sendMessage(new BinaryMessage(speechFrame()));
        }
        awaitType(events, "call.started");
    }
}
