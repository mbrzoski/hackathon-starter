package pl.aniolstroz.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import pl.aniolstroz.ai.ClassifierError;
import pl.aniolstroz.ai.ClassifierResult;
import pl.aniolstroz.config.AppProperties.Audit.Pricing;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.SpeakerRole;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;

/** OBS-03 and DAT-03 on a real SQLite database. */
class AuditServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-03T21:00:00Z");
    private static final Pricing PRICING = new Pricing(new BigDecimal("2"), new BigDecimal("0.20"), new BigDecimal("10"), new BigDecimal("2.50"));

    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbc;
    private AuditService audit;

    @BeforeEach
    void setUp() {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = JdbcClient.create(dataSource);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        audit = new AuditService(jdbc, mapper, Clock.fixed(T0.plusSeconds(5), ZoneOffset.UTC), PRICING);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    private static StageHit llmHit(StageId stage, String segId, String quote, boolean validated) {
        return new StageHit(stage, segId, quote, SpeakerRole.CALLER, HitSource.LLM, validated);
    }

    private static StageHit keywordHit(StageId stage, String segId, String quote) {
        return new StageHit(stage, segId, quote, SpeakerRole.UNCLEAR, HitSource.KEYWORDS, true);
    }

    private static ClassifierResult result(String range, List<StageHit> hits) {
        return new ClassifierResult(range, "claude-sonnet-5-5", "low", "{\"stage_hits\":[\"cytat z rozmowy\"]}", hits,
                new ClassifierResult.Usage(2900, 2400, 120), 850, "end_turn", null);
    }

    private static AuditEntry entry(String callId, ClassifierResult result, RiskLevel before, RiskLevel after,
            List<StageHit> keywordHits) {
        return new AuditEntry(callId, Mode.SCRIPTED, result, before, after, keywordHits);
    }

    private AuditEntry simpleEntry(String callId) {
        return entry(callId, result("s1-s3", List.of(llmHit(StageId.SECRECY_DEMAND, "s2", "nikomu nie mów", true))),
                RiskLevel.LOW, RiskLevel.MEDIUM, List.of());
    }

    private List<AuditRecord> records(String callId) {
        return audit.callAudit(callId).orElseThrow();
    }

    @Test
    void writesAndReadsBackEveryFieldOfARecord() {
        audit.callStarted("c1", Mode.SCRIPTED, T0);
        List<StageHit> hits = List.of(
                llmHit(StageId.SECRECY_DEMAND, "s2", "nikomu nie mów", true),
                llmHit(StageId.MONEY_REQUEST, "s3", "zmyślony cytat", false));
        List<StageHit> keywords = List.of(keywordHit(StageId.SECRECY_DEMAND, "s2", "Nikomu nie mów o tym."));

        audit.record(entry("c1", result("s1-s3", hits), RiskLevel.LOW, RiskLevel.MEDIUM, keywords));

        AuditRecord record = records("c1").get(0);
        assertThat(record.id()).isPositive();
        assertThat(record.callId()).isEqualTo("c1");
        assertThat(record.mode()).isEqualTo(Mode.SCRIPTED);
        assertThat(record.recordedAt()).isEqualTo(T0.plusSeconds(5));
        assertThat(record.segmentRange()).isEqualTo("s1-s3");
        assertThat(record.model()).isEqualTo("claude-sonnet-5-5");
        assertThat(record.effort()).isEqualTo("low");
        assertThat(record.usage()).isEqualTo(new AuditRecord.Usage(2900, 2400, 120));
        assertThat(record.latencyMs()).isEqualTo(850);
        assertThat(record.stopReason()).isEqualTo("end_turn");
        assertThat(record.error()).isNull();
        assertThat(record.rawOutput()).isEqualTo("{\"stage_hits\":[\"cytat z rozmowy\"]}");
        assertThat(record.hits()).containsExactly(
                new AuditHit(StageId.SECRECY_DEMAND, "s2", "nikomu nie mów", SpeakerRole.CALLER, HitSource.LLM, true),
                new AuditHit(StageId.MONEY_REQUEST, "s3", "zmyślony cytat", SpeakerRole.CALLER, HitSource.LLM, false));
        assertThat(record.keywordHits()).containsExactly(new AuditHit(
                StageId.SECRECY_DEMAND, "s2", "Nikomu nie mów o tym.", SpeakerRole.UNCLEAR, HitSource.KEYWORDS, true));
        assertThat(record.levelBefore()).isEqualTo(RiskLevel.LOW);
        assertThat(record.levelAfter()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(record.textCleared()).isFalse();
    }

    @Test
    void aFailedAiCallKeepsItsErrorAndNoHits() {
        ClassifierResult failed = new ClassifierResult("s1-s2", "claude-sonnet-5-5", "low", null, List.of(),
                new ClassifierResult.Usage(0, 0, 0), 2500, null, ClassifierError.TIMEOUT);

        audit.record(entry("c1", failed, RiskLevel.NONE, RiskLevel.NONE, List.of()));

        AuditRecord record = records("c1").get(0);
        assertThat(record.error()).isEqualTo(ClassifierError.TIMEOUT);
        assertThat(record.stopReason()).isNull();
        assertThat(record.rawOutput()).isNull();
        assertThat(record.hits()).isEmpty();
        assertThat(record.latencyMs()).isEqualTo(2500);
    }

    @Test
    void oneRecordPerAiCallInTheOrderTheyWereWrittenAndSeparatedByCall() {
        audit.record(simpleEntry("c1"));
        audit.record(simpleEntry("c2"));
        audit.record(entry("c1", result("s1-s5", List.of()), RiskLevel.MEDIUM, RiskLevel.MEDIUM, List.of()));

        assertThat(records("c1")).extracting(AuditRecord::segmentRange).containsExactly("s1-s3", "s1-s5");
        assertThat(records("c2")).hasSize(1);
    }

    @Test
    void clearingAfterACallWithoutAnAlertRemovesTextAndKeepsNumbers() {
        audit.callStarted("c1", Mode.SCRIPTED, T0);
        audit.callStarted("c2", Mode.SCRIPTED, T0);
        List<StageHit> hits = List.of(
                llmHit(StageId.SECRECY_DEMAND, "s2", "nikomu nie mów", true),
                llmHit(StageId.MONEY_REQUEST, "s3", "zmyślony cytat", false));
        audit.record(entry("c1", result("s1-s3", hits), RiskLevel.LOW, RiskLevel.MEDIUM,
                List.of(keywordHit(StageId.SECRECY_DEMAND, "s2", "Nikomu nie mów o tym."))));
        audit.record(simpleEntry("c2"));

        audit.callEnded("c1", T0.plusSeconds(60), RiskLevel.MEDIUM, false);

        AuditRecord cleared = records("c1").get(0);
        assertThat(cleared.textCleared()).isTrue();
        assertThat(cleared.rawOutput()).isNull();
        assertThat(cleared.hits()).extracting(AuditHit::quote).containsOnlyNulls();
        assertThat(cleared.keywordHits()).extracting(AuditHit::quote).containsOnlyNulls();
        // everything that is not text stays
        assertThat(cleared.hits()).extracting(AuditHit::stage, AuditHit::segId, AuditHit::validated)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(StageId.SECRECY_DEMAND, "s2", true),
                        org.assertj.core.groups.Tuple.tuple(StageId.MONEY_REQUEST, "s3", false));
        assertThat(cleared.usage()).isEqualTo(new AuditRecord.Usage(2900, 2400, 120));
        assertThat(cleared.latencyMs()).isEqualTo(850);
        assertThat(cleared.stopReason()).isEqualTo("end_turn");
        assertThat(cleared.segmentRange()).isEqualTo("s1-s3");
        assertThat(cleared.levelBefore()).isEqualTo(RiskLevel.LOW);
        assertThat(cleared.levelAfter()).isEqualTo(RiskLevel.MEDIUM);
        // another call is untouched
        assertThat(records("c2").get(0).textCleared()).isFalse();
        assertThat(records("c2").get(0).rawOutput()).isNotNull();
    }

    @Test
    void textIsNotInTheDatabaseFileAnyMoreAfterClearing() {
        audit.record(simpleEntry("c1"));
        audit.callEnded("c1", T0, RiskLevel.MEDIUM, false);

        String stored = jdbc.sql("SELECT coalesce(raw_output, '') || hits_json || keyword_hits_json FROM audit_records")
                .query(String.class).single();

        assertThat(stored).doesNotContain("nikomu nie mów").doesNotContain("cytat z rozmowy");
    }

    @Test
    void anAlertedCallKeepsItsText() {
        audit.callStarted("c1", Mode.SCRIPTED, T0);
        audit.record(simpleEntry("c1"));

        audit.callEnded("c1", T0.plusSeconds(60), RiskLevel.HIGH, true);

        AuditRecord record = records("c1").get(0);
        assertThat(record.textCleared()).isFalse();
        assertThat(record.rawOutput()).isNotNull();
        assertThat(record.hits().get(0).quote()).isEqualTo("nikomu nie mów");
    }

    @Test
    void anAnswerThatArrivesAfterTheCallEndedWithoutAnAlertIsStoredAlreadyCleared() {
        audit.callStarted("c1", Mode.SCRIPTED, T0);
        audit.callEnded("c1", T0.plusSeconds(60), RiskLevel.NONE, false);

        audit.record(simpleEntry("c1"));

        AuditRecord record = records("c1").get(0);
        assertThat(record.textCleared()).isTrue();
        assertThat(record.rawOutput()).isNull();
        assertThat(record.hits()).extracting(AuditHit::quote).containsOnlyNulls();
        assertThat(record.usage().inputTokens()).isEqualTo(2900);
    }

    @Test
    void anAnswerAfterAnAlertedCallEndedKeepsItsText() {
        audit.callStarted("c1", Mode.SCRIPTED, T0);
        audit.callEnded("c1", T0.plusSeconds(60), RiskLevel.HIGH, true);

        audit.record(simpleEntry("c1"));

        assertThat(records("c1").get(0).textCleared()).isFalse();
    }

    @Test
    void callListShowsStartEndMaxLevelAndTheNumberOfAiCallsNewestFirst() {
        audit.callStarted("old", Mode.SCRIPTED, T0);
        audit.record(simpleEntry("old"));
        audit.record(simpleEntry("old"));
        audit.callEnded("old", T0.plusSeconds(30), RiskLevel.HIGH, true);
        audit.callStarted("active", Mode.MOCK, T0.plusSeconds(100));
        audit.record(simpleEntry("active"));

        List<CallSummary> calls = audit.calls(10);

        assertThat(calls).extracting(CallSummary::callId).containsExactly("active", "old");
        assertThat(calls.get(0)).isEqualTo(new CallSummary("active", Mode.MOCK, T0.plusSeconds(100), null,
                RiskLevel.MEDIUM, 1));
        assertThat(calls.get(1)).isEqualTo(new CallSummary("old", Mode.SCRIPTED, T0, T0.plusSeconds(30),
                RiskLevel.HIGH, 2));
    }

    @Test
    void callListHonoursTheLimit() {
        for (int i = 1; i <= 5; i++) {
            audit.callStarted("c" + i, Mode.SCRIPTED, T0.plusSeconds(i));
        }

        assertThat(audit.calls(3)).extracting(CallSummary::callId).containsExactly("c5", "c4", "c3");
    }

    @Test
    void aCallWithoutAiCallsIsListedWithZero() {
        audit.callStarted("quiet", Mode.LIVE, T0);

        assertThat(audit.calls(10)).singleElement().satisfies(c -> assertThat(c.aiCalls()).isZero());
    }

    @Test
    void auditOfAKnownCallWithoutRecordsIsEmptyAndOfAnUnknownCallIsAbsent() {
        audit.callStarted("quiet", Mode.LIVE, T0);

        assertThat(audit.callAudit("quiet")).contains(List.of());
        assertThat(audit.callAudit("never-heard-of")).isEmpty();
    }

    @Test
    void summaryCountsRealCallsOnlyAndOnFixedData() {
        audit.record(realEntry("c1", 100, new ClassifierResult.Usage(1000, 0, 100), null, List.of()));
        audit.record(realEntry("c1", 200, new ClassifierResult.Usage(1200, 1000, 100), null,
                List.of(llmHit(StageId.MONEY_REQUEST, "s1", "x", false), llmHit(StageId.MONEY_REQUEST, "s2", "y", false))));
        audit.record(realEntry("c2", 300, new ClassifierResult.Usage(2000, 1500, 200), null, List.of()));
        audit.record(realEntry("c2", 400, new ClassifierResult.Usage(0, 0, 0), ClassifierError.TIMEOUT, List.of()));
        // mock calls are not measurements
        audit.record(entry("c3", new ClassifierResult("s1-s1", "mock", "none", "{}", List.of(),
                new ClassifierResult.Usage(0, 0, 0), 0, "mock", null), RiskLevel.NONE, RiskLevel.NONE, List.of()));

        AuditSummary summary = audit.summary();

        assertThat(summary.aiCalls()).isEqualTo(4);
        assertThat(summary.conversations()).isEqualTo(2);
        assertThat(summary.mockCallsExcluded()).isEqualTo(1);
        assertThat(summary.latencyP50Ms()).isEqualTo(200);
        assertThat(summary.latencyP95Ms()).isEqualTo(400);
        assertThat(summary.avgCostPerCallUsd()).isEqualByComparingTo("0.0043");
        assertThat(summary.avgCostPerConversationUsd()).isEqualByComparingTo("0.00645");
        assertThat(summary.rejectedQuotes()).isEqualTo(2);
        assertThat(summary.errorsByCause()).containsOnly(java.util.Map.entry(ClassifierError.TIMEOUT, 1));
    }

    @Test
    void rejectedQuotesStayCountedAfterTheirTextWasCleared() {
        audit.callStarted("c1", Mode.SCRIPTED, T0);
        audit.record(realEntry("c1", 100, new ClassifierResult.Usage(10, 0, 10), null,
                List.of(llmHit(StageId.MONEY_REQUEST, "s1", "zmyślony", false))));

        audit.callEnded("c1", T0.plusSeconds(1), RiskLevel.NONE, false);

        assertThat(audit.summary().rejectedQuotes()).isEqualTo(1);
    }

    @Test
    void summaryOfAnEmptyAuditHasNoNumbersToInvent() {
        AuditSummary summary = audit.summary();

        assertThat(summary.aiCalls()).isZero();
        assertThat(summary.latencyP50Ms()).isNull();
        assertThat(summary.avgCostPerCallUsd()).isNull();
    }

    @Test
    void callsLeftOpenByACrashAreClosedAndTheirTextIsCleared() {
        audit.callStarted("crashed", Mode.SCRIPTED, T0);
        audit.record(simpleEntry("crashed"));
        audit.callStarted("done", Mode.SCRIPTED, T0);
        audit.callEnded("done", T0.plusSeconds(1), RiskLevel.HIGH, true);
        audit.record(simpleEntry("done"));

        int closed = audit.closeOrphanedCalls();

        assertThat(closed).isEqualTo(1);
        assertThat(records("crashed").get(0).textCleared()).isTrue();
        assertThat(records("crashed").get(0).rawOutput()).isNull();
        assertThat(audit.calls(10)).filteredOn(c -> c.callId().equals("crashed"))
                .singleElement().satisfies(c -> assertThat(c.endedAt()).isNotNull());
        assertThat(records("done").get(0).textCleared()).isFalse();
        assertThat(audit.closeOrphanedCalls()).isZero();
    }

    private String storedText() {
        return jdbc.sql("SELECT coalesce(group_concat(coalesce(raw_output, '') || hits_json || keyword_hits_json), '')"
                + " FROM audit_records").query(String.class).single();
    }

    @Test
    void textOfARunningCallIsNotInTheDatabaseButStillVisibleInItsAudit() {
        audit.callStarted("c1", Mode.SCRIPTED, T0);
        audit.record(entry("c1", result("s1-s3", List.of(llmHit(StageId.SECRECY_DEMAND, "s2", "nikomu nie mów", true))),
                RiskLevel.LOW, RiskLevel.MEDIUM, List.of(keywordHit(StageId.SECRECY_DEMAND, "s2", "Nikomu nie mów."))));

        assertThat(storedText()).doesNotContain("nikomu nie mów").doesNotContain("Nikomu nie mów")
                .doesNotContain("cytat z rozmowy");
        AuditRecord live = records("c1").get(0);
        assertThat(live.rawOutput()).isEqualTo("{\"stage_hits\":[\"cytat z rozmowy\"]}");
        assertThat(live.hits().get(0).quote()).isEqualTo("nikomu nie mów");
        assertThat(live.keywordHits().get(0).quote()).isEqualTo("Nikomu nie mów.");
        assertThat(live.textCleared()).isFalse();
    }

    @Test
    void textOfAnAlertedCallIsWrittenToTheDatabaseOnlyWhenTheCallEnds() {
        audit.callStarted("c1", Mode.SCRIPTED, T0);
        audit.record(simpleEntry("c1"));
        assertThat(storedText()).doesNotContain("nikomu nie mów");

        audit.callEnded("c1", T0.plusSeconds(60), RiskLevel.HIGH, true);

        assertThat(storedText()).contains("nikomu nie mów").contains("cytat z rozmowy");
    }

    @Test
    void textOfACallWithoutAnAlertNeverReachesTheDatabase() {
        audit.callStarted("c1", Mode.SCRIPTED, T0);
        audit.record(simpleEntry("c1"));
        audit.record(simpleEntry("c1"));

        audit.callEnded("c1", T0.plusSeconds(60), RiskLevel.MEDIUM, false);

        assertThat(storedText()).doesNotContain("nikomu nie mów").doesNotContain("cytat z rozmowy");
        assertThat(records("c1")).allSatisfy(r -> assertThat(r.textCleared()).isTrue());
    }

    @Test
    void aRecordThatRacesWithTheEndOfTheCallNeverBringsTextBack() throws Exception {
        for (int i = 0; i < 100; i++) {
            String id = "race" + i;
            audit.callStarted(id, Mode.SCRIPTED, T0);
            Thread recording = Thread.ofPlatform().start(() -> audit.record(simpleEntry(id)));
            Thread ending = Thread.ofPlatform().start(() -> audit.callEnded(id, T0.plusSeconds(1), RiskLevel.NONE, false));
            recording.join();
            ending.join();
        }

        assertThat(storedText()).doesNotContain("nikomu nie mów").doesNotContain("cytat z rozmowy");
    }

    @Test
    void cacheWritesAreStoredReadBackAndPaidFor() {
        audit.record(realEntry("c1", 100, new ClassifierResult.Usage(1000, 0, 100, 2000), null, List.of()));

        assertThat(records("c1").get(0).usage()).isEqualTo(new AuditRecord.Usage(1000, 0, 100, 2000));
        // 1000 * 2 + 100 * 10 + 2000 * 2.50 = 8000 per million
        assertThat(audit.summary().avgCostPerCallUsd()).isEqualByComparingTo("0.008");
        assertThat(audit.summary().pricing().cacheCreationPerMillionUsd()).isEqualByComparingTo("2.50");
    }

    @Test
    void aDatabaseFromBeforeCacheWritesWereRecordedGetsTheColumn() {
        SingleConnectionDataSource old = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        try {
            JdbcClient oldJdbc = JdbcClient.create(old);
            oldJdbc.sql("CREATE TABLE audit_calls (call_id TEXT PRIMARY KEY, mode TEXT NOT NULL, started_at TEXT NOT NULL,"
                    + " ended_at TEXT, max_level TEXT NOT NULL DEFAULT 'NONE', had_alert INTEGER NOT NULL DEFAULT 0)")
                    .update();
            oldJdbc.sql("CREATE TABLE audit_records (id INTEGER PRIMARY KEY AUTOINCREMENT, call_id TEXT NOT NULL,"
                    + " mode TEXT NOT NULL, recorded_at TEXT NOT NULL, segment_range TEXT NOT NULL, model TEXT NOT NULL,"
                    + " effort TEXT NOT NULL, input_tokens INTEGER NOT NULL, cache_read_input_tokens INTEGER NOT NULL,"
                    + " output_tokens INTEGER NOT NULL, latency_ms INTEGER NOT NULL, stop_reason TEXT, error TEXT,"
                    + " raw_output TEXT, hits_json TEXT NOT NULL, keyword_hits_json TEXT NOT NULL,"
                    + " level_before TEXT NOT NULL, level_after TEXT NOT NULL, hit_count INTEGER NOT NULL,"
                    + " rejected_hits INTEGER NOT NULL, text_cleared INTEGER NOT NULL DEFAULT 0)").update();
            ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
            AuditService migrated = new AuditService(oldJdbc, mapper, Clock.fixed(T0, ZoneOffset.UTC), PRICING);

            migrated.record(realEntry("c1", 100, new ClassifierResult.Usage(10, 0, 10, 5), null, List.of()));
            // a second start finds the column and leaves the table alone
            new AuditService(oldJdbc, mapper, Clock.fixed(T0, ZoneOffset.UTC), PRICING);

            assertThat(migrated.callAudit("c1").orElseThrow().get(0).usage())
                    .isEqualTo(new AuditRecord.Usage(10, 0, 10, 5));
        } finally {
            old.destroy();
        }
    }

    @Test
    void aLateAnswerIsStoredAsLateAndItsHitsAreNotCountedAsRejected() {
        audit.callStarted("c1", Mode.SCRIPTED, T0);
        audit.callEnded("c1", T0.plusSeconds(10), RiskLevel.NONE, false);
        // an answer that came after the call ended: its hits were never validated (validated = false)
        ClassifierResult unvalidated = result("s1-s3", List.of(
                llmHit(StageId.SECRECY_DEMAND, "s2", "nikomu nie mów", false),
                llmHit(StageId.MONEY_REQUEST, "s3", "wypłać pieniądze", false)));

        audit.record(new AuditEntry("c1", Mode.SCRIPTED, unvalidated, RiskLevel.LOW, RiskLevel.LOW, List.of(), true));

        AuditRecord record = records("c1").get(0);
        assertThat(record.late()).isTrue();
        assertThat(record.hits()).extracting(AuditHit::validated).containsOnly(false);
        assertThat(audit.summary().rejectedQuotes()).isZero();
        assertThat(audit.summary().lateResults()).isEqualTo(1);
    }

    @Test
    void anAnswerInTimeIsNotLateAndItsRejectedQuotesAreCounted() {
        audit.callStarted("c1", Mode.SCRIPTED, T0);

        audit.record(realEntry("c1", 100, new ClassifierResult.Usage(10, 0, 10), null,
                List.of(llmHit(StageId.MONEY_REQUEST, "s1", "zmyślony", false))));

        assertThat(records("c1").get(0).late()).isFalse();
        assertThat(audit.summary().rejectedQuotes()).isEqualTo(1);
        assertThat(audit.summary().lateResults()).isZero();
    }

    private AuditEntry realEntry(String callId, long latencyMs, ClassifierResult.Usage usage, ClassifierError error,
            List<StageHit> hits) {
        return entry(callId, new ClassifierResult("s1-s2", "claude-sonnet-5-5", "low", error == null ? "{}" : null, hits,
                usage, latencyMs, error == null ? "end_turn" : null, error), RiskLevel.NONE, RiskLevel.NONE, List.of());
    }
}
