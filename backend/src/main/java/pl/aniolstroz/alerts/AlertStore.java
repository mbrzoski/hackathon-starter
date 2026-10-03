package pl.aniolstroz.alerts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionOperations;
import pl.aniolstroz.contracts.Alert;
import pl.aniolstroz.contracts.SpeakerLabel;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.TranscriptSegment;

/**
 * SQLite storage (BE-07) for alerted calls only: the alert and the transcript excerpt around the cited segments
 * (DAT-01). Calls without an alert never reach it. Texts are not logged.
 */
@Repository
public class AlertStore {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final TransactionOperations transactions;

    public AlertStore(JdbcClient jdbc, ObjectMapper mapper, TransactionOperations transactions) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.transactions = transactions;
    }

    /** Stores the alert and its excerpt in one transaction. */
    public void save(Alert alert, List<TranscriptSegment> excerpt) {
        String stagesJson = stagesJson(alert);
        Set<String> cited = alert.stages().stream().map(StageHit::segId).collect(Collectors.toSet());
        transactions.executeWithoutResult(status -> {
            jdbc.sql("""
                    INSERT INTO alerts (alert_id, call_id, level, template_id, short_text, advice, triggered_by,
                                        created_at, mode, stages_json)
                    VALUES (:id, :callId, :level, :templateId, :shortText, :advice, :triggeredBy, :createdAt, :mode,
                            :stages)""")
                    .param("id", alert.alertId())
                    .param("callId", alert.callId())
                    .param("level", lower(alert.level()))
                    .param("templateId", alert.templateId())
                    .param("shortText", alert.shortText())
                    .param("advice", alert.advice())
                    .param("triggeredBy", lower(alert.triggeredBy()))
                    .param("createdAt", alert.createdAt().toString())
                    .param("mode", alert.mode().name())
                    .param("stages", stagesJson)
                    .update();
            for (TranscriptSegment segment : excerpt) {
                jdbc.sql("""
                        INSERT INTO alert_segments (alert_id, seg_id, t_start_ms, t_end_ms, text, speaker, cited)
                        VALUES (:id, :segId, :start, :end, :text, :speaker, :cited)""")
                        .param("id", alert.alertId())
                        .param("segId", segment.segId())
                        .param("start", segment.tStartMs())
                        .param("end", segment.tEndMs())
                        .param("text", segment.text())
                        .param("speaker", segment.speaker().name())
                        .param("cited", cited.contains(segment.segId()) ? 1 : 0)
                        .update();
            }
        });
    }

    /** The stored excerpt of an alert in transcript order. */
    public List<TranscriptSegment> segmentsOf(String alertId) {
        return jdbc.sql("""
                SELECT seg_id, t_start_ms, t_end_ms, text, speaker FROM alert_segments
                WHERE alert_id = :id ORDER BY CAST(substr(seg_id, 2) AS INTEGER)""")
                .param("id", alertId)
                .query((rs, row) -> new TranscriptSegment(callIdOf(alertId), rs.getString("seg_id"),
                        rs.getLong("t_start_ms"), rs.getLong("t_end_ms"), rs.getString("text"), true,
                        SpeakerLabel.valueOf(rs.getString("speaker")), null))
                .list();
    }

    private String callIdOf(String alertId) {
        return jdbc.sql("SELECT call_id FROM alerts WHERE alert_id = :id").param("id", alertId)
                .query(String.class).single();
    }

    private String stagesJson(Alert alert) {
        try {
            return mapper.writeValueAsString(alert.stages());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize alert stages", e);
        }
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
