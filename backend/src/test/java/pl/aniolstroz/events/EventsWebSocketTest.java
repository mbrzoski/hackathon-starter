package pl.aniolstroz.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import pl.aniolstroz.contracts.Alert;
import pl.aniolstroz.contracts.CallStarted;
import pl.aniolstroz.contracts.Component;
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.EventEnvelope.AlertCreatedEvent;
import pl.aniolstroz.contracts.EventEnvelope.CallStartedEvent;
import pl.aniolstroz.contracts.EventEnvelope.SystemStatusEvent;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.SpeakerRole;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;
import pl.aniolstroz.contracts.SystemStatus;
import pl.aniolstroz.contracts.TriggeredBy;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.events.heartbeat-interval-ms=3600000")
class EventsWebSocketTest {

    private static final Instant AT = Instant.parse("2026-10-03T21:00:00Z");

    @LocalServerPort
    int port;

    @Autowired
    EventBus eventBus;

    @Autowired
    HeartbeatService heartbeat;

    @Autowired
    ObjectMapper mapper;

    /** Collects text frames and the close status of one client. */
    static final class Client extends TextWebSocketHandler {
        final BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        final CompletableFuture<CloseStatus> closed = new CompletableFuture<>();
        private final ObjectMapper mapper;
        WebSocketSession session;

        Client(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        protected void handleTextMessage(WebSocketSession s, TextMessage message) throws Exception {
            messages.add(mapper.readTree(message.getPayload()));
        }

        @Override
        public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
            closed.complete(status);
        }

        JsonNode next() throws InterruptedException {
            JsonNode node = messages.poll(5, TimeUnit.SECONDS);
            assertThat(node).as("a message within 5 s").isNotNull();
            return node;
        }
    }

    private Client connect(String query) throws Exception {
        var client = new Client(mapper);
        client.session = new StandardWebSocketClient()
                .execute(client, "ws://localhost:" + port + "/ws/events" + query)
                .get(5, TimeUnit.SECONDS);
        return client;
    }

    private static SystemStatusEvent status(Component component, ComponentState state) {
        return new SystemStatusEvent(Mode.SCRIPTED, AT, new SystemStatus(component, state, "test", AT));
    }

    @Test
    void invalidOrMissingRoleIsClosedWith1008() throws Exception {
        for (String query : List.of("?role=hacker", "", "?role=")) {
            Client client = connect(query);

            assertThat(client.closed.get(5, TimeUnit.SECONDS).getCode()).as(query).isEqualTo(1008);
        }
    }

    @Test
    void publishedEventReachesAllClients() throws Exception {
        Client senior = connect("?role=senior");
        Client family = connect("?role=family");
        // Registration happens right after the handshake; wait until both are subscribed.
        Thread.sleep(300);

        eventBus.publish(status(Component.AUDIO, ComponentState.DOWN));

        for (Client client : List.of(senior, family)) {
            JsonNode event = drainUntil(client, "audio");
            assertThat(event.get("type").asText()).isEqualTo("system.status");
            assertThat(event.get("mode").asText()).isEqualTo("SCRIPTED");
            assertThat(event.at("/payload/state").asText()).isEqualTo("down");
        }
        senior.session.close();
        family.session.close();
    }

    @Test
    void newClientGetsSnapshotOfStateOnConnect() throws Exception {
        eventBus.publish(status(Component.STT, ComponentState.DEGRADED));
        eventBus.publish(new CallStartedEvent(Mode.SCRIPTED, AT, new CallStarted("call-snap")));
        eventBus.publish(new AlertCreatedEvent(Mode.SCRIPTED, AT, alert("alert-snap", "call-snap")));

        Client client = connect("?role=audit");

        List<String> seen = new ArrayList<>();
        while (!(seen.contains("system.status:stt") && seen.contains("call.started:call-snap")
                && seen.contains("alert.created:alert-snap"))) {
            JsonNode event = client.next();
            String type = event.get("type").asText();
            String key = switch (type) {
                case "system.status" -> type + ":" + event.at("/payload/component").asText();
                case "call.started" -> type + ":" + event.at("/payload/callId").asText();
                case "alert.created" -> type + ":" + event.at("/payload/alertId").asText();
                default -> type;
            };
            seen.add(key);
        }
        client.session.close();
    }

    @Test
    void heartbeatPublishesBackendOk() throws Exception {
        Client client = connect("?role=family");
        Thread.sleep(300);

        heartbeat.beat();

        JsonNode event = drainUntil(client, "backend");
        assertThat(event.at("/payload/state").asText()).isEqualTo("ok");
        client.session.close();
    }

    /** Skips snapshot frames until a system.status for the given component arrives. */
    private static JsonNode drainUntil(Client client, String component) throws InterruptedException {
        while (true) {
            JsonNode event = client.next();
            if ("system.status".equals(event.get("type").asText())
                    && component.equals(event.at("/payload/component").asText())) {
                return event;
            }
        }
    }

    static Alert alert(String alertId, String callId) {
        var hit = new StageHit(StageId.AUTHORITY_CLAIM, "s1", "mówi policja", SpeakerRole.CALLER, HitSource.LLM, true);
        return new Alert(alertId, callId, RiskLevel.HIGH, List.of(hit), "high-v1", "Uwaga.", "Rozłącz się.",
                TriggeredBy.LLM, AT, Mode.SCRIPTED);
    }
}
