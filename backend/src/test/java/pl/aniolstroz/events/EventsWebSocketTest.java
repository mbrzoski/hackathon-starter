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
import pl.aniolstroz.contracts.Actor;
import pl.aniolstroz.contracts.Alert;
import pl.aniolstroz.contracts.CallEnded;
import pl.aniolstroz.contracts.CallStarted;
import pl.aniolstroz.contracts.Component;
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.Decision;
import pl.aniolstroz.contracts.DecisionType;
import pl.aniolstroz.contracts.EventEnvelope.AlertCreatedEvent;
import pl.aniolstroz.contracts.EventEnvelope.AlertDecisionEvent;
import pl.aniolstroz.contracts.EventEnvelope.CallEndedEvent;
import pl.aniolstroz.contracts.EventEnvelope.CallStartedEvent;
import pl.aniolstroz.contracts.EventEnvelope.RiskUpdateEvent;
import pl.aniolstroz.contracts.EventEnvelope.SystemStatusEvent;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.RiskUpdate;
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
    void snapshotCarriesAlertsRiskAndDecisionsOfTheActiveCall() throws Exception {
        publishCallWithAlertAndDecision("call-full", "alert-full");

        Client client = connect("?role=senior");

        assertThat(snapshotKeys(client)).containsSubsequence(
                "call.started:call-full", "alert.created:alert-full", "risk.update:call-full",
                "alert.decision:alert-full");
        client.session.close();
    }

    @Test
    void snapshotKeepsEveryAlertOfTheActiveCallInOrder() throws Exception {
        eventBus.publish(new CallStartedEvent(Mode.SCRIPTED, AT, new CallStarted("call-two")));
        eventBus.publish(new AlertCreatedEvent(Mode.SCRIPTED, AT, alert("alert-medium", "call-two")));
        eventBus.publish(new AlertCreatedEvent(Mode.SCRIPTED, AT, alert("alert-high", "call-two")));

        Client client = connect("?role=family");

        assertThat(snapshotKeys(client)).containsSubsequence(
                "call.started:call-two", "alert.created:alert-medium", "alert.created:alert-high");
        client.session.close();
    }

    @Test
    void snapshotHasNothingOfACallThatEnded() throws Exception {
        publishCallWithAlertAndDecision("call-over", "alert-over");
        eventBus.publish(new CallEndedEvent(Mode.SCRIPTED, AT, new CallEnded("call-over", true)));

        Client client = connect("?role=senior");

        assertThat(snapshotKeys(client))
                .noneMatch(key -> key.startsWith("call.started") || key.startsWith("alert.")
                        || key.startsWith("risk.update"));
        client.session.close();
    }

    @Test
    void aNewCallStartsWithAnEmptySnapshotOfCallData() throws Exception {
        publishCallWithAlertAndDecision("call-old", "alert-old");
        eventBus.publish(new CallStartedEvent(Mode.SCRIPTED, AT, new CallStarted("call-new")));

        Client client = connect("?role=senior");

        assertThat(snapshotKeys(client)).contains("call.started:call-new")
                .noneMatch(key -> key.startsWith("alert.") || key.startsWith("risk.update"));
        client.session.close();
    }

    @Test
    void aDecisionAboutAnAlertThatIsNotCurrentIsNotPartOfTheSnapshot() throws Exception {
        eventBus.publish(new CallStartedEvent(Mode.SCRIPTED, AT, new CallStarted("call-current")));
        eventBus.publish(new AlertDecisionEvent(Mode.SCRIPTED, AT,
                new Decision("alert-from-an-earlier-call", Actor.FAMILY, DecisionType.FALSE_ALARM, AT)));

        Client client = connect("?role=family");

        assertThat(snapshotKeys(client)).noneMatch(key -> key.startsWith("alert.decision"));
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

    private void publishCallWithAlertAndDecision(String callId, String alertId) {
        eventBus.publish(new CallStartedEvent(Mode.SCRIPTED, AT, new CallStarted(callId)));
        eventBus.publish(new AlertCreatedEvent(Mode.SCRIPTED, AT, alert(alertId, callId)));
        eventBus.publish(new RiskUpdateEvent(Mode.SCRIPTED, AT,
                new RiskUpdate(callId, RiskLevel.HIGH, RiskLevel.NONE, List.of(StageId.AUTHORITY_CLAIM), 1)));
        eventBus.publish(new AlertDecisionEvent(Mode.SCRIPTED, AT,
                new Decision(alertId, Actor.SENIOR, DecisionType.HUNG_UP, AT)));
    }

    /**
     * The frames of the snapshot, as "type:id" keys in the order received. The snapshot is sent right after the
     * handshake, so a marker event published afterwards tells where it ends.
     */
    private List<String> snapshotKeys(Client client) throws InterruptedException {
        // Unique per call: an earlier marker may still be the latest backend status in later snapshots.
        String marker = "snapshot-end-" + java.util.UUID.randomUUID();
        Thread.sleep(300);
        eventBus.publish(new SystemStatusEvent(Mode.SCRIPTED, AT,
                new SystemStatus(Component.BACKEND, ComponentState.OK, marker, AT)));
        List<String> keys = new ArrayList<>();
        while (true) {
            JsonNode event = client.next();
            if (marker.equals(event.at("/payload/message").asText())) {
                return keys;
            }
            String type = event.get("type").asText();
            keys.add(switch (type) {
                case "system.status" -> type + ":" + event.at("/payload/component").asText();
                case "call.started", "risk.update" -> type + ":" + event.at("/payload/callId").asText();
                case "alert.created" -> type + ":" + event.at("/payload/alertId").asText();
                case "alert.decision" -> type + ":" + event.at("/payload/alertId").asText();
                default -> type;
            });
        }
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
