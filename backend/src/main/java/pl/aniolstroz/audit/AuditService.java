package pl.aniolstroz.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import pl.aniolstroz.ai.ClassifierError;
import pl.aniolstroz.ai.ClassifierResult;
import pl.aniolstroz.config.AppProperties;
import pl.aniolstroz.config.AppProperties.Audit.Pricing;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;

/**
 * The AI audit in SQLite (BE-07, OBS-03): one record per classifier call, plus the calls themselves so they can be
 * listed. Text is never logged (OBS-05).
 *
 * <p>Call text stays in memory until the call is over (rule 4, DAT-01, DAT-03). While a call runs, a record is
 * written with its numbers only: the raw answer is missing and the quotes are removed from the hit lists
 * ({@code text_cleared = 1}). The text of the record is kept in memory, so the audit of a running call still shows it
 * ({@link #callAudit}). When the call ends with an alert the text is written to the record; when it ends without one,
 * the text is dropped and never reaches the disk. A crash loses the text of the call that was running, which is the
 * safe default. Everything that decides whether text may be stored happens under one lock, so a record that arrives
 * while the call ends cannot bring text back.
 */
@Service
public class AuditService {

    private static final String MOCK_MODEL = "mock";

    /** The text of one record that is not on disk (yet): the raw answer and the hits with their quotes. */
    private record PendingText(String rawOutput, List<AuditHit> hits, List<AuditHit> keywordHits) {
    }

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Pricing pricing;

    /** Guards the state below and makes "is the call over?" plus "write the record" one step. */
    private final ReentrantLock lock = new ReentrantLock();
    /** Record id to the text of that record, for calls that are still running. */
    private final Map<Long, PendingText> pending = new HashMap<>();
    /** Call id to the ids of its records that have pending text. */
    private final Map<String, List<Long>> pendingByCall = new HashMap<>();

    @Autowired
    public AuditService(JdbcClient jdbc, ObjectMapper mapper, Clock clock, AppProperties properties) {
        this(jdbc, mapper, clock, properties.audit().pricing());
    }

    public AuditService(JdbcClient jdbc, ObjectMapper mapper, Clock clock, Pricing pricing) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
        this.pricing = pricing;
        addMissingColumns();
    }

    /** A database file from before a column existed gets it; schema.sql only creates tables that are missing. */
    private void addMissingColumns() {
        List<String> columns = jdbc.sql("SELECT name FROM pragma_table_info('audit_records')")
                .query(String.class).list();
        if (columns.isEmpty()) {
            return;
        }
        if (!columns.contains("cache_creation_input_tokens")) {
            jdbc.sql("ALTER TABLE audit_records ADD COLUMN cache_creation_input_tokens INTEGER NOT NULL DEFAULT 0")
                    .update();
        }
        if (!columns.contains("late")) {
            jdbc.sql("ALTER TABLE audit_records ADD COLUMN late INTEGER NOT NULL DEFAULT 0").update();
        }
    }

    public void callStarted(String callId, Mode mode, Instant at) {
        jdbc.sql("INSERT OR IGNORE INTO audit_calls (call_id, mode, started_at) VALUES (:id, :mode, :at)")
                .param("id", callId).param("mode", mode.name()).param("at", at.toString()).update();
    }

    /**
     * Stores one record, without text while its call is running (the text waits in memory). If the call already ended,
     * the text is stored only when it ended with an alert: a late answer must not bring text back.
     */
    public void record(AuditEntry entry) {
        ClassifierResult result = entry.result();
        List<AuditHit> hits = result.hits().stream().map(AuditHit::of).toList();
        List<AuditHit> keywordHits = entry.keywordHits().stream().map(AuditHit::of).toList();
        lock.lock();
        try {
            // A call whose start was missed (a database hiccup) still shows up in the list.
            callStarted(entry.callId(), entry.mode(), clock.instant());
            Optional<Boolean> endedWithAlert = endedWithAlert(entry.callId());
            boolean textOnDisk = endedWithAlert.orElse(false);
            boolean callRunning = endedWithAlert.isEmpty();
            long id = insert(entry, result, textOnDisk ? hits : withoutQuotes(hits),
                    textOnDisk ? keywordHits : withoutQuotes(keywordHits), textOnDisk ? result.rawOutput() : null,
                    !textOnDisk);
            if (callRunning) {
                pending.put(id, new PendingText(result.rawOutput(), hits, keywordHits));
                pendingByCall.computeIfAbsent(entry.callId(), k -> new ArrayList<>()).add(id);
            }
        } finally {
            lock.unlock();
        }
    }

    private long insert(AuditEntry entry, ClassifierResult result, List<AuditHit> hits, List<AuditHit> keywordHits,
            String rawOutput, boolean textCleared) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO audit_records (call_id, mode, recorded_at, segment_range, model, effort, input_tokens,
                    cache_read_input_tokens, output_tokens, cache_creation_input_tokens, latency_ms, stop_reason,
                    error, raw_output, hits_json, keyword_hits_json, level_before, level_after, hit_count,
                    rejected_hits, text_cleared, late)
                VALUES (:callId, :mode, :at, :range, :model, :effort, :in, :cacheRead, :out, :cacheCreation,
                    :latency, :stop, :error, :raw, :hits, :keywordHits, :before, :after, :hitCount, :rejected,
                    :cleared, :late)""")
                .param("callId", entry.callId())
                .param("mode", entry.mode().name())
                .param("at", clock.instant().toString())
                .param("range", result.segmentRange())
                .param("model", result.model())
                .param("effort", result.effort())
                .param("in", result.usage().inputTokens())
                .param("cacheRead", result.usage().cacheReadInputTokens())
                .param("out", result.usage().outputTokens())
                .param("cacheCreation", result.usage().cacheCreationInputTokens())
                .param("latency", result.latencyMs())
                .param("stop", result.stopReason())
                .param("error", result.error() == null ? null : result.error().name())
                .param("raw", rawOutput)
                .param("hits", json(hits))
                .param("keywordHits", json(keywordHits))
                .param("before", entry.levelBefore().name())
                .param("after", entry.levelAfter().name())
                .param("hitCount", result.hits().size())
                // A late answer was never validated: its hits are not rejections.
                .param("rejected", entry.late() ? 0 : (int) result.hits().stream().filter(h -> !h.validated()).count())
                .param("late", entry.late() ? 1 : 0)
                .param("cleared", textCleared ? 1 : 0)
                .update(key);
        return key.getKey().longValue();
    }

    /**
     * Closes the call. With an alert, the text kept in memory is written to its records; without one it is dropped
     * (DAT-03) and so are any quotes an older version of the audit may have written to disk.
     */
    public void callEnded(String callId, Instant at, RiskLevel maxLevel, boolean hadAlert) {
        lock.lock();
        try {
            jdbc.sql("UPDATE audit_calls SET ended_at = :at, max_level = :level, had_alert = :alert WHERE call_id = :id")
                    .param("at", at.toString()).param("level", maxLevel.name()).param("alert", hadAlert ? 1 : 0)
                    .param("id", callId).update();
            List<Long> ids = pendingByCall.remove(callId);
            try {
                if (hadAlert && ids != null) {
                    ids.forEach(this::writeText);
                }
            } finally {
                if (ids != null) {
                    ids.forEach(pending::remove);
                }
            }
            if (!hadAlert) {
                clearText(callId);
            }
        } finally {
            lock.unlock();
        }
    }

    private void writeText(long id) {
        PendingText text = pending.get(id);
        if (text == null) {
            return;
        }
        jdbc.sql("""
                UPDATE audit_records SET raw_output = :raw, hits_json = :hits, keyword_hits_json = :keywordHits,
                    text_cleared = 0 WHERE id = :id""")
                .param("raw", text.rawOutput())
                .param("hits", json(text.hits()))
                .param("keywordHits", json(text.keywordHits()))
                .param("id", id)
                .update();
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

    /**
     * The records of a call, oldest first; empty if the audit does not know the call. For a call that is still
     * running the text comes from memory, so it is visible without ever having been written to disk.
     */
    public Optional<List<AuditRecord>> callAudit(String callId) {
        lock.lock();
        try {
            List<AuditRecord> records = jdbc.sql("SELECT * FROM audit_records WHERE call_id = :id ORDER BY id")
                    .param("id", callId).query(this::toRecord).list().stream()
                    .map(record -> {
                        PendingText text = pending.get(record.id());
                        return text == null ? record
                                : record.withText(text.rawOutput(), text.hits(), text.keywordHits());
                    })
                    .toList();
            boolean known = !records.isEmpty()
                    || jdbc.sql("SELECT count(*) FROM audit_calls WHERE call_id = :id").param("id", callId)
                            .query(Integer.class).single() > 0;
            return known ? Optional.of(records) : Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    public AuditSummary summary() {
        List<AuditStatistics.Sample> samples = jdbc.sql("""
                SELECT call_id, latency_ms, input_tokens, cache_read_input_tokens, output_tokens,
                       cache_creation_input_tokens, error, rejected_hits, late
                FROM audit_records WHERE model <> :mock""")
                .param("mock", MOCK_MODEL)
                .query((rs, row) -> new AuditStatistics.Sample(rs.getString("call_id"), rs.getLong("latency_ms"),
                        new ClassifierResult.Usage(rs.getLong("input_tokens"), rs.getLong("cache_read_input_tokens"),
                                rs.getLong("output_tokens"), rs.getLong("cache_creation_input_tokens")),
                        rs.getString("error") == null ? null : ClassifierError.valueOf(rs.getString("error")),
                        rs.getInt("rejected_hits"), rs.getInt("late") == 1))
                .list();
        int mockCalls = jdbc.sql("SELECT count(*) FROM audit_records WHERE model = :mock").param("mock", MOCK_MODEL)
                .query(Integer.class).single();
        return AuditStatistics.summarize(samples, mockCalls, pricing);
    }

    /**
     * Closes the calls that were still open when the application stopped. Their text lived only in memory and is
     * gone; quotes that an older version wrote to disk are removed. Returns how many calls were closed.
     */
    public int closeOrphanedCalls() {
        List<String> open = jdbc.sql("SELECT call_id FROM audit_calls WHERE ended_at IS NULL")
                .query(String.class).list();
        for (String callId : open) {
            callEnded(callId, clock.instant(), highestLevelSeen(callId), false);
        }
        return open.size();
    }

    /** Empty while the call is running or unknown; otherwise whether it ended with an alert. */
    private Optional<Boolean> endedWithAlert(String callId) {
        return jdbc.sql("SELECT had_alert FROM audit_calls WHERE call_id = :id AND ended_at IS NOT NULL")
                .param("id", callId).query((rs, row) -> rs.getInt("had_alert") == 1).optional();
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
                        .param("hits", json(withoutQuotes(readHits((String) row[1]))))
                        .param("keywordHits", json(withoutQuotes(readHits((String) row[2]))))
                        .param("id", row[0])
                        .update());
    }

    private static List<AuditHit> withoutQuotes(List<AuditHit> hits) {
        return hits.stream().map(AuditHit::withoutQuote).toList();
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
                        rs.getLong("output_tokens"), rs.getLong("cache_creation_input_tokens")),
                rs.getLong("latency_ms"), rs.getString("stop_reason"),
                error == null ? null : ClassifierError.valueOf(error), rs.getString("raw_output"),
                readHits(rs.getString("hits_json")), readHits(rs.getString("keyword_hits_json")),
                RiskLevel.valueOf(rs.getString("level_before")), RiskLevel.valueOf(rs.getString("level_after")),
                rs.getInt("text_cleared") == 1, rs.getInt("late") == 1);
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
