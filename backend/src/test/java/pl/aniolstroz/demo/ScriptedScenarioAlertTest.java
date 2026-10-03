package pl.aniolstroz.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import pl.aniolstroz.alerts.AlertStore;
import pl.aniolstroz.call.CallService;
import pl.aniolstroz.call.CallServices;
import pl.aniolstroz.call.CallEndedHook;
import pl.aniolstroz.call.RetainAlertedCallHook;
import pl.aniolstroz.contracts.Alert;
import pl.aniolstroz.contracts.EventEnvelope;
import pl.aniolstroz.contracts.EventEnvelope.AlertCreatedEvent;
import pl.aniolstroz.contracts.EventEnvelope.CallEndedEvent;
import pl.aniolstroz.contracts.EventEnvelope.RiskUpdateEvent;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.events.EventBus;

/**
 * A bundled SCRIPTED scenario played end to end through the player, the call, the keyword layer, the risk engine,
 * the alert templates and the database. Only the keyword layer runs here (no AI yet), so this is also the baseline.
 */
class ScriptedScenarioAlertTest {

    private final List<EventEnvelope> events = new CopyOnWriteArrayList<>();
    private final CountDownLatch callEnded = new CountDownLatch(1);
    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbc;
    private ScriptedPlayer player;
    private CallService calls;

    @BeforeEach
    void setUp() {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = JdbcClient.create(dataSource);

        ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        EventBus bus = mock(EventBus.class);
        doAnswer(invocation -> {
            EventEnvelope event = invocation.getArgument(0);
            events.add(event);
            if (event instanceof CallEndedEvent) {
                callEnded.countDown();
            }
            return null;
        }).when(bus).publish(any());

        AlertStore store = new AlertStore(jdbc, mapper, new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        CallEndedHook hook = new RetainAlertedCallHook(store, bus, Clock.systemUTC());
        calls = CallServices.create(bus, Clock.systemUTC(), hook);
        player = new ScriptedPlayer(new ScenarioRepository(mapper), calls, duration -> { }, snapshot -> { throw new AssertionError("no AI in this test"); });
    }

    @AfterEach
    void tearDown() {
        player.stop();
        dataSource.destroy();
    }

    private void play(String scenarioId) throws InterruptedException {
        player.start(scenarioId, 1.0);
        assertThat(callEnded.await(10, TimeUnit.SECONDS)).as("call ended").isTrue();
    }

    private List<Alert> alerts() {
        return events.stream().filter(AlertCreatedEvent.class::isInstance)
                .map(e -> ((AlertCreatedEvent) e).payload()).toList();
    }

    @Test
    void classicFakePoliceScenarioEndsWithAHighAlertStoredWithItsExcerpt() throws InterruptedException {
        play("01-fake-police-classic");

        assertThat(alerts()).isNotEmpty();
        Alert last = alerts().get(alerts().size() - 1);
        assertThat(last.level()).isEqualTo(RiskLevel.HIGH);
        assertThat(last.mode()).isEqualTo(Mode.SCRIPTED);
        assertThat(last.stages()).isNotEmpty().allSatisfy(h -> assertThat(h.validated()).isTrue());
        assertThat(last.shortText()).isNotBlank();
        assertThat(last.advice()).isNotBlank();

        RiskUpdateEvent lastRisk = (RiskUpdateEvent) events.stream().filter(RiskUpdateEvent.class::isInstance)
                .reduce((a, b) -> b).orElseThrow();
        assertThat(lastRisk.payload().level()).isEqualTo(RiskLevel.HIGH);

        CallEndedEvent ended = (CallEndedEvent) events.get(events.size() - 1);
        assertThat(ended.payload().hadAlert()).isTrue();
        assertThat(jdbc.sql("SELECT count(*) FROM alerts WHERE level = 'high'").query(Integer.class).single()).isPositive();
        assertThat(jdbc.sql("SELECT count(*) FROM alert_segments").query(Integer.class).single()).isPositive();
        assertThat(calls.active()).isEmpty();
    }

    @ParameterizedTest(name = "{0} raises no alert")
    @CsvSource({
            "02-fake-police-paraphrase",
            "04-real-grandson",
            "05-real-police-bike"
    })
    void scenariosWithoutKeywordCombinationsEndWithoutAlertAndStoreNothing(String scenarioId)
            throws InterruptedException {
        play(scenarioId);

        assertThat(alerts()).isEmpty();
        assertThat(((CallEndedEvent) events.get(events.size() - 1)).payload().hadAlert()).isFalse();
        assertThat(jdbc.sql("SELECT count(*) FROM alerts").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM alert_segments").query(Integer.class).single()).isZero();
    }

    @Test
    void callbackTrapScenarioGetsTheIsolationAdvice() throws InterruptedException {
        play("12-callback-trap");

        assertThat(alerts()).isNotEmpty();
        assertThat(alerts()).anySatisfy(a -> {
            assertThat(a.templateId()).isEqualTo("medium-isolation");
            assertThat(a.advice()).contains("Rozłącz się i odczekaj minutę");
        });
    }
}
