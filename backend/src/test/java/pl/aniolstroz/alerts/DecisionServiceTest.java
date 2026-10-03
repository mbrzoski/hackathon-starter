package pl.aniolstroz.alerts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import pl.aniolstroz.contracts.Actor;
import pl.aniolstroz.contracts.Alert;
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.Decision;
import pl.aniolstroz.contracts.DecisionType;
import pl.aniolstroz.contracts.EventEnvelope;
import pl.aniolstroz.contracts.EventEnvelope.AlertDecisionEvent;
import pl.aniolstroz.contracts.EventEnvelope.SystemStatusEvent;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.SpeakerRole;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;
import pl.aniolstroz.contracts.TriggeredBy;
import pl.aniolstroz.events.EventBus;

class DecisionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T21:30:00Z");
    private static final String QUOTE = "Proszę wypłacić gotówkę z konta.";

    /** Stands in for the active call: holds alerts and records requests to ignore stages. */
    private static final class FakeLive implements LiveCallAccess {
        final List<Alert> alerts = new ArrayList<>();
        final List<Object[]> ignoreRequests = new ArrayList<>();

        @Override
        public List<Alert> alerts() {
            return List.copyOf(alerts);
        }

        @Override
        public boolean ignoreStages(String callId, Set<StageId> stages) {
            ignoreRequests.add(new Object[] {callId, stages});
            return true;
        }
    }

    @TempDir
    Path dir;

    private final List<EventEnvelope> events = new ArrayList<>();
    private final FakeLive live = new FakeLive();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbc;
    private AlertStore alertStore;
    private Path labels;
    private DecisionService service;

    @BeforeEach
    void setUp() {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = JdbcClient.create(dataSource);
        alertStore = new AlertStore(jdbc, mapper, new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        labels = dir.resolve("data").resolve("labels.jsonl");
        service = newService(labels);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    private DecisionService newService(Path labelsFile) {
        EventBus bus = mock(EventBus.class);
        doAnswer(invocation -> events.add(invocation.getArgument(0))).when(bus).publish(any());
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new DecisionService(alertStore, new DecisionStore(jdbc), new LabelWriter(labelsFile, mapper), live,
                bus, clock);
    }

    private static Alert alert(String id, Instant createdAt, Mode mode) {
        List<StageHit> stages = List.of(
                new StageHit(StageId.AUTHORITY_CLAIM, "s1", "Mówi policja.", SpeakerRole.UNCLEAR, HitSource.KEYWORDS, true),
                new StageHit(StageId.MONEY_REQUEST, "s3", QUOTE, SpeakerRole.UNCLEAR, HitSource.KEYWORDS, true));
        return new Alert(id, "call-" + id, RiskLevel.HIGH, stages, "high-general", "Krótko.", "Rada.",
                TriggeredBy.KEYWORDS, createdAt, mode);
    }

    private Alert storedAlert(String id) {
        Alert alert = alert(id, NOW.minusSeconds(60), Mode.SCRIPTED);
        alertStore.save(alert, List.of());
        return alert;
    }

    private static DecisionCommand command(Actor actor, DecisionType decision) {
        return new DecisionCommand(actor, decision, Set.of());
    }

    private int count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }

    private List<JsonNode> labelLines() throws IOException {
        if (!Files.exists(labels)) {
            return List.of();
        }
        List<JsonNode> lines = new ArrayList<>();
        for (String line : Files.readAllLines(labels)) {
            lines.add(mapper.readTree(line));
        }
        return lines;
    }

    @Test
    void storesTheDecisionWithServerSetAlertIdAndTime() {
        storedAlert("a-1");

        Decision decision = service.record("a-1", command(Actor.SENIOR, DecisionType.HUNG_UP));

        assertThat(decision).isEqualTo(new Decision("a-1", Actor.SENIOR, DecisionType.HUNG_UP, NOW));
        var row = jdbc.sql("SELECT * FROM decisions").query().singleRow();
        assertThat(row).containsEntry("alert_id", "a-1").containsEntry("actor", "senior")
                .containsEntry("decision", "hung_up").containsEntry("decided_at", "2026-10-03T21:30:00Z")
                .containsEntry("mode", "SCRIPTED");
    }

    @Test
    void anAlertCanHaveSeveralDecisions() {
        storedAlert("a-1");

        service.record("a-1", command(Actor.SENIOR, DecisionType.CALLED_TRUSTED));
        service.record("a-1", command(Actor.FAMILY, DecisionType.CONFIRMED_SCAM));
        service.record("a-1", command(Actor.SENIOR, DecisionType.FALSE_ALARM));

        assertThat(count("decisions")).isEqualTo(3);
        List<AlertHistoryEntry> history = service.recent(20);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).decisions()).extracting(Decision::decision)
                .containsExactly(DecisionType.CALLED_TRUSTED, DecisionType.CONFIRMED_SCAM, DecisionType.FALSE_ALARM);
    }

    @Test
    void publishesAlertDecisionWithTheModeOfTheAlert() {
        alertStore.save(alert("a-1", NOW, Mode.MOCK), List.of());

        Decision decision = service.record("a-1", command(Actor.SENIOR, DecisionType.HUNG_UP));

        assertThat(events).hasSize(1);
        AlertDecisionEvent event = (AlertDecisionEvent) events.get(0);
        assertThat(event.payload()).isEqualTo(decision);
        assertThat(event.mode()).isEqualTo(Mode.MOCK);
        assertThat(event.at()).isEqualTo(NOW);
    }

    @Test
    void unknownAlertIsRejectedAndNothingIsStoredPublishedOrLabelled() {
        assertThatThrownBy(() -> service.record("nope", command(Actor.SENIOR, DecisionType.FALSE_ALARM)))
                .isInstanceOf(AlertNotFoundException.class);

        assertThat(count("decisions")).isZero();
        assertThat(events).isEmpty();
        assertThat(labels).doesNotExist();
    }

    @Test
    void alertOfTheActiveCallIsDecidableBeforeItReachesTheDatabase() {
        live.alerts.add(alert("live-1", NOW, Mode.SCRIPTED));

        Decision decision = service.record("live-1", command(Actor.SENIOR, DecisionType.HUNG_UP));

        assertThat(decision.alertId()).isEqualTo("live-1");
        assertThat(count("decisions")).isEqualTo(1);
        assertThat(count("alerts")).isZero();
    }

    @Test
    void falseAlarmAndConfirmedScamAreWrittenAsOneJsonLineEach() throws IOException {
        storedAlert("a-1");

        service.record("a-1", command(Actor.SENIOR, DecisionType.FALSE_ALARM));
        service.record("a-1", command(Actor.FAMILY, DecisionType.CONFIRMED_SCAM));

        List<JsonNode> lines = labelLines();
        assertThat(lines).hasSize(2);
        JsonNode first = lines.get(0);
        assertThat(first.get("alertId").asText()).isEqualTo("a-1");
        assertThat(first.get("callId").asText()).isEqualTo("call-a-1");
        assertThat(first.get("decision").asText()).isEqualTo("false_alarm");
        assertThat(first.get("actor").asText()).isEqualTo("senior");
        assertThat(first.get("at").asText()).isEqualTo("2026-10-03T21:30:00Z");
        assertThat(first.get("mode").asText()).isEqualTo("SCRIPTED");
        assertThat(first.get("stages")).extracting(JsonNode::asText).containsExactly("AUTHORITY_CLAIM", "MONEY_REQUEST");
        assertThat(lines.get(1).get("decision").asText()).isEqualTo("confirmed_scam");
        assertThat(lines.get(1).get("actor").asText()).isEqualTo("family");
    }

    @Test
    void labelsCarryStageIdsOnlyNeverTheQuotedCallText() throws IOException {
        storedAlert("a-1");

        service.record("a-1", command(Actor.SENIOR, DecisionType.FALSE_ALARM));

        assertThat(Files.readString(labels)).doesNotContain("wypłacić").doesNotContain("policja");
    }

    @Test
    void otherDecisionsAreNotLabels() {
        storedAlert("a-1");

        service.record("a-1", command(Actor.SENIOR, DecisionType.HUNG_UP));
        service.record("a-1", command(Actor.SENIOR, DecisionType.CALLED_TRUSTED));

        assertThat(labels).doesNotExist();
    }

    @Test
    void labelFileFailureIsReportedButTheDecisionIsStillStored() throws IOException {
        Path directoryInTheWay = dir.resolve("labels-dir");
        Files.createDirectories(directoryInTheWay);
        service = newService(directoryInTheWay);
        storedAlert("a-1");

        Decision decision = service.record("a-1", command(Actor.SENIOR, DecisionType.FALSE_ALARM));

        assertThat(decision).isNotNull();
        assertThat(count("decisions")).isEqualTo(1);
        assertThat(events).hasSize(2);
        SystemStatusEvent status = (SystemStatusEvent) events.get(0);
        assertThat(status.payload().component()).isEqualTo(pl.aniolstroz.contracts.Component.BACKEND);
        assertThat(status.payload().state()).isEqualTo(ComponentState.DEGRADED);
        assertThat(events.get(1)).isInstanceOf(AlertDecisionEvent.class);
    }

    @Test
    void familyCannotHangUpAndSeniorCannotConfirmAScam() {
        storedAlert("a-1");

        assertThatThrownBy(() -> service.record("a-1", command(Actor.FAMILY, DecisionType.HUNG_UP)))
                .isInstanceOf(InvalidDecisionException.class);
        assertThatThrownBy(() -> service.record("a-1", command(Actor.FAMILY, DecisionType.CALLED_TRUSTED)))
                .isInstanceOf(InvalidDecisionException.class);
        assertThatThrownBy(() -> service.record("a-1", command(Actor.SENIOR, DecisionType.CONFIRMED_SCAM)))
                .isInstanceOf(InvalidDecisionException.class);

        assertThat(count("decisions")).isZero();
        assertThat(events).isEmpty();
    }

    @Test
    void ignoredStagesFromTheSeniorAreRejected() {
        storedAlert("a-1");

        assertThatThrownBy(() -> service.record("a-1",
                new DecisionCommand(Actor.SENIOR, DecisionType.FALSE_ALARM, Set.of(StageId.AUTHORITY_CLAIM))))
                .isInstanceOf(InvalidDecisionException.class);

        assertThat(count("decisions")).isZero();
        assertThat(live.ignoreRequests).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void familyIgnoredStagesAreHandedToTheCallOfThatAlert() {
        storedAlert("a-1");

        service.record("a-1", new DecisionCommand(Actor.FAMILY, DecisionType.FALSE_ALARM,
                Set.of(StageId.AUTHORITY_CLAIM, StageId.MONEY_REQUEST)));

        assertThat(live.ignoreRequests).hasSize(1);
        assertThat(live.ignoreRequests.get(0)[0]).isEqualTo("call-a-1");
        assertThat((Set<StageId>) live.ignoreRequests.get(0)[1])
                .containsExactlyInAnyOrder(StageId.AUTHORITY_CLAIM, StageId.MONEY_REQUEST);
    }

    @Test
    void noIgnoreRequestWithoutIgnoredStages() {
        storedAlert("a-1");

        service.record("a-1", command(Actor.FAMILY, DecisionType.CONFIRMED_SCAM));

        assertThat(live.ignoreRequests).isEmpty();
    }

    @Test
    void recentReturnsNewestFirstWithinTheLimitAndMergesTheActiveCall() {
        alertStore.save(alert("old", NOW.minus(Duration.ofHours(2)), Mode.SCRIPTED), List.of());
        alertStore.save(alert("mid", NOW.minus(Duration.ofHours(1)), Mode.SCRIPTED), List.of());
        live.alerts.add(alert("live", NOW, Mode.SCRIPTED));
        service.record("mid", command(Actor.SENIOR, DecisionType.HUNG_UP));

        List<AlertHistoryEntry> history = service.recent(2);

        assertThat(history).extracting(e -> e.alert().alertId()).containsExactly("live", "mid");
        assertThat(history.get(0).decisions()).isEmpty();
        assertThat(history.get(1).decisions()).hasSize(1);
        assertThat(service.recent(20)).hasSize(3);
    }

    @Test
    void anAlertThatIsBothLiveAndStoredAppearsOnce() {
        Alert alert = storedAlert("a-1");
        live.alerts.add(alert);

        assertThat(service.recent(20)).hasSize(1);
    }
}
