package pl.aniolstroz.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import pl.aniolstroz.ai.ClassifierError;
import pl.aniolstroz.ai.ClassifierResult;
import pl.aniolstroz.config.AppProperties;
import pl.aniolstroz.config.AppProperties.Audit.Pricing;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.StageHit;

/**
 * The AI audit in SQLite (BE-07, OBS-03): one record per classifier call, plus the calls themselves so they can be
 * listed. After a call without an alert the text fields are emptied and the numbers stay (DAT-03). Text is never
 * logged (OBS-05).
 *
 * <p>Records of a call are written while it is running, so the text is on disk until the call ends. That is why a
 * call left open by a crash is closed and cleaned at startup ({@link #closeOrphanedCalls}): a call that never ended
 * has no alert in the database, and the safe default for its text is to delete it.
 */
@Service
public class AuditService {

    private static final String MOCK_MODEL = "mock";

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Pricing pricing;

    @Autowired
    public AuditService(JdbcClient jdbc, ObjectMapper mapper, Clock clock, AppProperties properties) {
        this(jdbc, mapper, clock, properties.audit().pricing());
    }

    public AuditService(JdbcClient jdbc, ObjectMapper mapper, Clock clock, Pricing pricing) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
        this.pricing = pricing;
    }

    public void callStarted(String callId, Mode mode, Instant at) {
        jdbc.sql("INSERT OR IGNORE INTO audit_calls (call_id, mode, started_at) VALUES (:id, :mode, :at)")
                .param("id", callId).param("mode", mode.name()).param("at", at.toString()).update();
    }

    /**
     * Stores one record. If the call already ended without an alert, the record is stored with its text already
     * removed: a late answer must not bring text back.
     */
    public void record(AuditEntry entry) {
        ClassifierResult result = entry.result();
        boolean cleared = endedWithoutAlert(entry.callId());
        List<AuditHit> hits = result.hits().stream().map(AuditHit::of).toList();
        List<AuditHit> keywordHits = entry.keywordHits().stream().map(AuditHit::of).toList();
        if (cleared) {
            hits = hits.stream().map(AuditHit::withoutQuote).toList();
            keywordHits = keywordHits.stream().map(AuditHit::withoutQuote).toList();
        }
        // A call whose start was missed (a database hiccup) still shows up in the list.
        callStarted(entry.callId(), entry.mode(), clock.instant());
        jdbc.sql("""
                INSERT INTO audit_records (call_id, mode, recorded_at, segment_range, model, effort, input_tokens,
                    cache_read_input_tokens, output_tokens, latency_ms, stop_reason, error, raw_output, hits_json,
                    keyword_hits_json, level_before, level_after, hit_count, rejected_hits, text_cleared)
                VALUES (:callId, :mode, :at, :range, :model, :effort, :in, :cacheRead, :out, :latency, :stop, :error,
                    :raw, :hits, :keywordHits, :before, :after, :hitCount, :rejected, :cleared)""")
                .param("callId", entry.callId())
                .param("mode", entry.mode().name())
                .param("at", clock.instant().toString())
                .param("range", result.segmentRange())
                .param("model", result.model())
                .param("effort", result.effort())
                .param("in", result.usage().inputTokens())
                .param("cacheRead", result.usage().cacheReadInputTokens())
                .param("out", result.usage().outputTokens())
                .param("latency", result.latencyMs())
                .param("stop", result.stopReason())
                .param("error", result.error() == null ? null : result.error().name())
                .param("raw", cleared ? null : result.rawOutput())
                .param("hits", json(hits))
                .param("keywordHits", json(keywordHits))
                .param("before", entry.levelBefore().name())
                .param("after", entry.levelAfter().name())
                .param("hitCount", result.hits().size())
                .param("rejected", (int) result.hits().stream().filter(h -> !h.validated()).count())
                .param("cleared", cleared ? 1 : 0)
                .update();
    }

    /** Closes the call and, when it had no alert, removes the text of its records (DAT-03). */
    public void callEnded(String callId, Instant at, RiskLevel maxLevel, boolean hadAlert) {
        jdbc.sql("UPDATE audit_calls SET ended_at = :at, max_level = :level, had_alert = :alert WHERE call_id = :id")
                .param("at", at.toString()).param("level", maxLevel.name()).param("alert", hadAlert ? 1 : 0)
                .param("id", callId).update();
        if (!hadAlert) {
            clearText(callId);
        }
    }

    /** Newest calls first. */
    public List<CallSummary> calls(int limit) {
        return jdbc.sql("""
                SELECT c.call_id, c.mode, c.started_at, c.ended_at, c.max_level,
                       (SELECT count(*) FROM audit_records r WHERE r.call_id = c.call_id) AS ai_calls,
                       (SELECT max(CASE r.level_after WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 WHEN 'LOW' THEN 1 ELSE 0 END)
                        FROM audit_records r WHERE r.call_id = c.call_id) AS level_seen
                FROM audit_calls c ORDER BY c.rowid DESC LIMIT :limit""")
                .param("limit", limit)
                .query(this::toCallSummary)
                .list();
    }

    /** The records of a call, oldest first; empty if the audit does not know the call. */
    public Optional<List<AuditRecord>> callAudit(String callId) {
        List<AuditRecord> records = jdbc.sql("SELECT * FROM audit_records WHERE call_id = :id ORDER BY id")
                .param("id", callId).query(this::toRecord).list();
        boolean known = !records.isEmpty()
                || jdbc.sql("SELECT count(*) FROM audit_calls WHERE call_id = :id").param("id", callId)
                        .query(Integer.class).single() > 0;
        return known ? Optional.of(records) : Optional.empty();
    }

    public AuditSummary summary() {
        List<AuditStatistics.Sample> samples = jdbc.sql("""
                SELECT call_id, latency_ms, input_tokens, cache_read_input_tokens, output_tokens, error, rejected_hits
                FROM audit_records WHERE model <> :mock""")
                .param("mock", MOCK_MODEL)
                .query((rs, row) -> new AuditStatistics.Sample(rs.getString("call_id"), rs.getLong("latency_ms"),
                        new ClassifierResult.Usage(rs.getLong("input_tokens"), rs.getLong("cache_read_input_tokens"),
                                rs.getLong("output_tokens")),
                        rs.getString("error") == null ? null : ClassifierError.valueOf(rs.getString("error")),
                        rs.getInt("rejected_hits")))
                .list();
        int mockCalls = jdbc.sql("SELECT count(*) FROM audit_records WHERE model = :mock").param("mock", MOCK_MODEL)
                .query(Integer.class).single();
        return AuditStatistics.summarize(samples, mockCalls, pricing);
    }

    /**
     * Closes the calls that were still open when the application stopped, and removes their text. Returns how many.
     */
    public int closeOrphanedCalls() {
        List<String> open = jdbc.sql("SELECT call_id FROM audit_calls WHERE ended_at IS NULL")
                .query(String.class).list();
        for (String callId : open) {
            callEnded(callId, clock.instant(), highestLevelSeen(callId), false);
        }
        return open.size();
    }

    private boolean endedWithoutAlert(String callId) {
        return jdbc.sql("SELECT count(*) FROM audit_calls WHERE call_id = :id AND ended_at IS NOT NULL AND had_alert = 0")
                .param("id", callId).query(Integer.class).single() > 0;
    }

    private void clearText(String callId) {
        jdbc.sql("SELECT id, hits_json, keyword_hits_json FROM audit_records WHERE call_id = :id AND text_cleared = 0")
                .param("id", callId)
                .query((rs, row) -> new Object[] {rs.getLong("id"), rs.getString("hits_json"),
                        rs.getString("keyword_hits_json")})
                .list()
                .forEach(row -> jdbc.sql("""
                        UPDATE audit_records SET raw_output = NULL, hits_json = :hits, keyword_hits_json = :keywordHits,
                            text_cleared = 1 WHERE id = :id""")
                        .param("hits", json(withoutQuotes((String) row[1])))
                        .param("keywordHits", json(withoutQuotes((String) row[2])))
                        .param("id", row[0])
                        .update());
    }

    private List<AuditHit> withoutQuotes(String hitsJson) {
        return readHits(hitsJson).stream().map(AuditHit::withoutQuote).toList();
    }

    private RiskLevel highestLevelSeen(String callId) {
        return jdbc.sql("SELECT level_after FROM audit_records WHERE call_id = :id").param("id", callId)
                .query(String.class).list().stream().map(RiskLevel::valueOf)
                .max(java.util.Comparator.naturalOrder()).orElse(RiskLevel.NONE);
    }

    private CallSummary toCallSummary(ResultSet rs, int row) throws SQLException {
        String endedAt = rs.getString("ended_at");
        // No query in here: the single connection is busy delivering these rows. An open call has no final maximum yet,
        // so it shows the highest level its AI calls saw (ordinal of RiskLevel: NONE 0, LOW 1, MEDIUM 2, HIGH 3).
        RiskLevel maxLevel = endedAt == null ? RiskLevel.values()[rs.getInt("level_seen")]
                : RiskLevel.valueOf(rs.getString("max_level"));
        return new CallSummary(rs.getString("call_id"), Mode.valueOf(rs.getString("mode")),
                Instant.parse(rs.getString("started_at")), endedAt == null ? null : Instant.parse(endedAt), maxLevel,
                rs.getInt("ai_calls"));
    }

    private AuditRecord toRecord(ResultSet rs, int row) throws SQLException {
        String error = rs.getString("error");
        return new AuditRecord(rs.getLong("id"), rs.getString("call_id"), Mode.valueOf(rs.getString("mode")),
                Instant.parse(rs.getString("recorded_at")), rs.getString("segment_range"), rs.getString("model"),
                rs.getString("effort"),
                new AuditRecord.Usage(rs.getLong("input_tokens"), rs.getLong("cache_read_input_tokens"),
                        rs.getLong("output_tokens")),
                rs.getLong("latency_ms"), rs.getString("stop_reason"),
                error == null ? null : ClassifierError.valueOf(error), rs.getString("raw_output"),
                readHits(rs.getString("hits_json")), readHits(rs.getString("keyword_hits_json")),
                RiskLevel.valueOf(rs.getString("level_before")), RiskLevel.valueOf(rs.getString("level_after")),
                rs.getInt("text_cleared") == 1);
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize audit data", e);
        }
    }

    private List<AuditHit> readHits(String json) {
        try {
            return mapper.readValue(json, new TypeReference<List<AuditHit>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored audit hits are unreadable", e);
        }
    }
}
