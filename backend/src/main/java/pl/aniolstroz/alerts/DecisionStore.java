package pl.aniolstroz.alerts;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pl.aniolstroz.contracts.Actor;
import pl.aniolstroz.contracts.Decision;
import pl.aniolstroz.contracts.DecisionType;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.StageId;

/** SQLite table {@code decisions}: every decision of a person about an alert (an alert can have several). */
@Repository
public class DecisionStore {

    private final JdbcClient jdbc;

    public DecisionStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void save(Decision decision, Mode mode, Set<StageId> ignoredStages) {
        jdbc.sql("""
                INSERT INTO decisions (alert_id, actor, decision, decided_at, mode, ignored_stages)
                VALUES (:alertId, :actor, :decision, :at, :mode, :ignored)""")
                .param("alertId", decision.alertId())
                .param("actor", decision.actor().name().toLowerCase(Locale.ROOT))
                .param("decision", decision.decision().name().toLowerCase(Locale.ROOT))
                .param("at", decision.at().toString())
                .param("mode", mode.name())
                .param("ignored", ignoredStages.stream().map(StageId::name).sorted().collect(Collectors.joining(",")))
                .update();
    }

    /** Decisions per alert id, oldest first. Alerts without decisions are absent from the map. */
    public Map<String, List<Decision>> findByAlertIds(Collection<String> alertIds) {
        Map<String, List<Decision>> result = new LinkedHashMap<>();
        if (alertIds.isEmpty()) {
            return result;
        }
        jdbc.sql("SELECT alert_id, actor, decision, decided_at FROM decisions WHERE alert_id IN (:ids) ORDER BY id")
                .param("ids", alertIds)
                .query((rs, row) -> new Decision(rs.getString("alert_id"),
                        Actor.valueOf(rs.getString("actor").toUpperCase(Locale.ROOT)),
                        DecisionType.valueOf(rs.getString("decision").toUpperCase(Locale.ROOT)),
                        Instant.parse(rs.getString("decided_at"))))
                .list()
                .forEach(d -> result.computeIfAbsent(d.alertId(), k -> new java.util.ArrayList<>()).add(d));
        return result;
    }
}
