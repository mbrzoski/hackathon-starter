package pl.aniolstroz.settings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;
import pl.aniolstroz.config.AppProperties;
import pl.aniolstroz.contracts.Component;
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.EventEnvelope.SystemStatusEvent;
import pl.aniolstroz.contracts.SystemStatus;
import pl.aniolstroz.events.EventBus;

/**
 * Data retention (DAT-02): alerts, their excerpts, decisions and audit rows older than the retention of the household
 * settings (1 to 90 days; {@code app.retention.days} until the settings are saved) are deleted at startup, every hour
 * and right after the settings change. {@link #deleteAll()} backs {@code DELETE /api/data}. A failed cleanup is never
 * silent: it publishes {@code system.status} (backend, degraded).
 */
@Service
public class RetentionService {

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final JdbcClient jdbc;
    private final TransactionOperations transactions;
    private final AppProperties properties;
    private final SettingsService settings;
    private final pl.aniolstroz.alerts.LabelWriter labels;
    private final EventBus eventBus;
    private final Clock clock;

    RetentionService(JdbcClient jdbc, TransactionOperations transactions, AppProperties properties,
                     EventBus eventBus, Clock clock, SettingsService settings,
                     pl.aniolstroz.alerts.LabelWriter labels) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.properties = properties;
        this.eventBus = eventBus;
        this.clock = clock;
        this.settings = settings;
        this.labels = labels;
    }

    /** A shorter retention saved in the setup wizard applies at once, not only at the next hourly run. */
    @org.springframework.context.event.EventListener
    void onSettingsChanged(SettingsChanged event) {
        scheduledCleanup();
    }

    /** Runs at startup (initial delay 0) and then every hour. */
    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.HOURS, initialDelay = 0)
    void scheduledCleanup() {
        try {
            deleteOlderThan(clock.instant().minus(Duration.ofDays(settings.retentionDays())));
        } catch (RuntimeException e) {
            // Type only: messages may carry SQL with data.
            log.error("Retention cleanup failed: {}", e.getClass().getName());
            Instant now = clock.instant();
            eventBus.publish(new SystemStatusEvent(eventBus.modeOrDefault(properties.mode()), now, new SystemStatus(
                    Component.BACKEND, ComponentState.DEGRADED,
                    "Nie udało się usunąć starych danych zgodnie z retencją.", now)));
        }
    }

    /** Deletes everything older than the cutoff in one transaction; returns the number of alerts removed. */
    int deleteOlderThan(Instant cutoff) {
        String limit = cutoff.toString();
        return transactions.execute(status -> {
            String oldAlerts = "SELECT alert_id FROM alerts WHERE created_at < :cutoff";
            jdbc.sql("DELETE FROM alert_segments WHERE alert_id IN (" + oldAlerts + ")").param("cutoff", limit).update();
            jdbc.sql("DELETE FROM decisions WHERE decided_at < :cutoff OR alert_id IN (" + oldAlerts + ")")
                    .param("cutoff", limit).update();
            int removed = jdbc.sql("DELETE FROM alerts WHERE created_at < :cutoff").param("cutoff", limit).update();
            jdbc.sql("""
                    DELETE FROM audit_records
                    WHERE recorded_at < :cutoff OR call_id IN (SELECT call_id FROM audit_calls WHERE started_at < :cutoff)""")
                    .param("cutoff", limit).update();
            jdbc.sql("DELETE FROM audit_calls WHERE started_at < :cutoff").param("cutoff", limit).update();
            return removed;
        });
    }

    /** Deletes all stored alerts, excerpts, decisions, audit rows and evaluation labels (DELETE /api/data). */
    public void deleteAll() {
        transactions.executeWithoutResult(status -> {
            for (String table : new String[] {"alert_segments", "decisions", "alerts", "audit_records", "audit_calls"}) {
                jdbc.sql("DELETE FROM " + table).update();
            }
        });
        try {
            labels.deleteAll();
        } catch (java.io.IOException e) {
            log.error("Deleting the evaluation labels failed: {}", e.getClass().getName());
            throw new IllegalStateException("Nie udało się usunąć etykiet ewaluacji.", e);
        }
    }
}
