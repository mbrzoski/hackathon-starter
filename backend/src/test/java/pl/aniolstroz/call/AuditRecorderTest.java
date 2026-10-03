package pl.aniolstroz.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import pl.aniolstroz.ai.ClassifierResult;
import pl.aniolstroz.audit.AuditEntry;
import pl.aniolstroz.audit.AuditRecord;
import pl.aniolstroz.audit.AuditService;
import pl.aniolstroz.config.AppProperties.Audit.Pricing;
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.EventEnvelope;
import pl.aniolstroz.contracts.EventEnvelope.SystemStatusEvent;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.SpeakerRole;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;
import pl.aniolstroz.events.EventBus;

/** The seam between the call and the audit: lifecycle events and AI call reports become audit writes. */
class AuditRecorderTest {

    private static final Instant T0 = Instant.parse("2026-10-03T21:00:00Z");
    private static final Clock CLOCK = Clock.fixed(T0.plusSeconds(5), ZoneOffset.UTC);

    private final List<EventEnvelope> events = new ArrayList<>();
    private SingleConnectionDataSource dataSource;
    private AuditService audit;
    private EventBus bus;

    @BeforeEach
    void setUp() {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        audit = new AuditService(JdbcClient.create(dataSource), new ObjectMapper().findAndRegisterModules(), CLOCK,
                new Pricing(new BigDecimal("2"), new BigDecimal("0.20"), new BigDecimal("10"), new BigDecimal("2.50")));
        bus = mock(EventBus.class);
        doAnswer(invocation -> events.add(invocation.getArgument(0))).when(bus).publish(any());
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    private static AiCallReport report(String callId) {
        List<StageHit> hits = List.of(new StageHit(StageId.SECRECY_DEMAND, "s2", "nikomu nie mów", SpeakerRole.CALLER,
                HitSource.LLM, true));
        ClassifierResult result = new ClassifierResult("s1-s2", "claude-sonnet-5-5", "low", "{}", hits,
                new ClassifierResult.Usage(100, 50, 20), 321, "end_turn", null);
        List<StageHit> keywords = List.of(new StageHit(StageId.AUTHORITY_CLAIM, "s1", "Mówi policja.",
                SpeakerRole.UNCLEAR, HitSource.KEYWORDS, true));
        return new AiCallReport(callId, Mode.SCRIPTED, result, RiskLevel.LOW, RiskLevel.MEDIUM, keywords);
    }

    @Test
    void anAiCallReportBecomesOneAuditRecordWithEverythingTheReportCarries() {
        AuditRecorder recorder = new AuditRecorder(audit, bus, CLOCK);

        recorder.onAiCall(report("c1"));

        AuditRecord record = audit.callAudit("c1").orElseThrow().get(0);
        assertThat(record.mode()).isEqualTo(Mode.SCRIPTED);
        assertThat(record.segmentRange()).isEqualTo("s1-s2");
        assertThat(record.usage()).isEqualTo(new AuditRecord.Usage(100, 50, 20));
        assertThat(record.latencyMs()).isEqualTo(321);
        assertThat(record.levelBefore()).isEqualTo(RiskLevel.LOW);
        assertThat(record.levelAfter()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(record.hits()).hasSize(1);
        assertThat(record.keywordHits()).extracting(h -> h.stage()).containsExactly(StageId.AUTHORITY_CLAIM);
    }

    @Test
    void lifecycleEventsFillTheCallListAndClearTextAfterACallWithoutAnAlert() {
        AuditRecorder recorder = new AuditRecorder(audit, bus, CLOCK);
        recorder.onCallOpened(new CallOpened("c1", Mode.SCRIPTED, "01-fake-police-classic", T0));
        recorder.onAiCall(report("c1"));

        recorder.onCallClosed(new CallClosed("c1", Mode.SCRIPTED, T0.plusSeconds(60), RiskLevel.MEDIUM, false));

        var call = audit.calls(10).get(0);
        assertThat(call.callId()).isEqualTo("c1");
        assertThat(call.startedAt()).isEqualTo(T0);
        assertThat(call.endedAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(call.maxLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(audit.callAudit("c1").orElseThrow().get(0).textCleared()).isTrue();
    }

    @Test
    void anAlertedCallKeepsItsAuditText() {
        AuditRecorder recorder = new AuditRecorder(audit, bus, CLOCK);
        recorder.onCallOpened(new CallOpened("c1", Mode.SCRIPTED, null, T0));
        recorder.onAiCall(report("c1"));

        recorder.onCallClosed(new CallClosed("c1", Mode.SCRIPTED, T0.plusSeconds(60), RiskLevel.HIGH, true));

        assertThat(audit.callAudit("c1").orElseThrow().get(0).textCleared()).isFalse();
    }

    @Test
    void aFailingAuditIsReportedAsDegradedAndNeverThrowsIntoTheCall() {
        AuditService broken = mock(AuditService.class);
        doThrow(new DataAccessResourceFailureException("disk full")).when(broken).record(any(AuditEntry.class));
        doThrow(new DataAccessResourceFailureException("disk full")).when(broken).callStarted(any(), any(), any());
        doThrow(new DataAccessResourceFailureException("disk full")).when(broken)
                .callEnded(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
        AuditRecorder recorder = new AuditRecorder(broken, bus, CLOCK);

        recorder.onCallOpened(new CallOpened("c1", Mode.SCRIPTED, null, T0));
        recorder.onAiCall(report("c1"));
        recorder.onCallClosed(new CallClosed("c1", Mode.SCRIPTED, T0, RiskLevel.NONE, false));

        assertThat(events).isNotEmpty().allSatisfy(e -> {
            assertThat(e).isInstanceOf(SystemStatusEvent.class);
            SystemStatusEvent status = (SystemStatusEvent) e;
            assertThat(status.payload().component()).isEqualTo(pl.aniolstroz.contracts.Component.BACKEND);
            assertThat(status.payload().state()).isEqualTo(ComponentState.DEGRADED);
            assertThat(status.payload().message()).isNotBlank();
            assertThat(status.mode()).isEqualTo(Mode.SCRIPTED);
        });
    }

    @Test
    void startupClosesCallsLeftOpenByACrash() {
        AuditService mocked = mock(AuditService.class);
        AuditRecorder recorder = new AuditRecorder(mocked, bus, CLOCK);

        recorder.onReady();

        verify(mocked).closeOrphanedCalls();
    }
}
