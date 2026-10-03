package pl.aniolstroz.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import pl.aniolstroz.config.AppProperties;
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.EventEnvelope;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.events.EventBus;

class RetentionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbc;
    private RetentionService service;
    private final List<EventEnvelope> published = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = JdbcClient.create(dataSource);
        EventBus bus = mock(EventBus.class);
        doAnswer(invocation -> published.add(invocation.getArgument(0))).when(bus).publish(any());
        service = service(bus, new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    private RetentionService service(EventBus bus, TransactionTemplate tx) {
        var props = new AppProperties(Mode.SCRIPTED, new AppProperties.Claude("m", 1000, 1024),
                new AppProperties.Events(List.of(), 1000, 1000, 1000), new AppProperties.Labels("x"),
                new AppProperties.Audit(new AppProperties.Audit.Pricing(
                        java.math.BigDecimal.ONE, java.math.BigDecimal.ONE, java.math.BigDecimal.ONE, java.math.BigDecimal.ONE)),
                new AppProperties.Stt(AppProperties.Stt.Provider.FAKE, new AppProperties.Stt.Vosk("m"),
                        new AppProperties.Stt.Silence(10_000, 1_000)),
                new AppProperties.Retention(30));
        return new RetentionService(jdbc, tx, props, bus, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    private void insertAlert(String id, String createdAt) {
        jdbc.sql("INSERT INTO alerts VALUES (:id, 'c', 'high', 't', 's', 'a', 'both', :at, 'MOCK', '[]')")
                .param("id", id).param("at", createdAt).update();
        jdbc.sql("INSERT INTO alert_segments VALUES (:id, 's1', 0, 1, 'tekst', 'A', 1)").param("id", id).update();
        jdbc.sql("INSERT INTO decisions (alert_id, actor, decision, decided_at, mode, ignored_stages) "
                + "VALUES (:id, 'senior', 'hung_up', :at, 'MOCK', '[]')").param("id", id).param("at", createdAt).update();
    }

    private void insertAudit(String callId, String startedAt) {
        jdbc.sql("INSERT INTO audit_calls (call_id, mode, started_at) VALUES (:c, 'MOCK', :at)")
                .param("c", callId).param("at", startedAt).update();
        jdbc.sql("""
                INSERT INTO audit_records (call_id, mode, recorded_at, segment_range, model, effort, input_tokens,
                    cache_read_input_tokens, output_tokens, latency_ms, hits_json, keyword_hits_json, level_before,
                    level_after, hit_count, rejected_hits)
                VALUES (:c, 'MOCK', :at, '0-1', 'm', 'low', 1, 0, 1, 1, '[]', '[]', 'NONE', 'NONE', 0, 0)""")
                .param("c", callId).param("at", startedAt).update();
    }

    private int count(String table) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table).query(Integer.class).single();
    }

    @Test
    void deletesOnlyRowsOlderThanTheRetention() {
        insertAlert("old", "2026-09-01T10:00:00Z");
        insertAlert("fresh", "2026-10-25T10:00:00Z");
        insertAudit("old-call", "2026-09-01T10:00:00Z");
        insertAudit("fresh-call", "2026-10-25T10:00:00Z");

        service.scheduledCleanup();

        assertThat(jdbc.sql("SELECT alert_id FROM alerts").query(String.class).list()).containsExactly("fresh");
        assertThat(jdbc.sql("SELECT alert_id FROM alert_segments").query(String.class).list()).containsExactly("fresh");
        assertThat(jdbc.sql("SELECT alert_id FROM decisions").query(String.class).list()).containsExactly("fresh");
        assertThat(jdbc.sql("SELECT call_id FROM audit_calls").query(String.class).list()).containsExactly("fresh-call");
        assertThat(jdbc.sql("SELECT call_id FROM audit_records").query(String.class).list()).containsExactly("fresh-call");
        assertThat(published).isEmpty();
    }

    @Test
    void deleteAllEmptiesEveryTable() {
        insertAlert("a", "2026-10-29T10:00:00Z");
        insertAudit("c", "2026-10-29T10:00:00Z");

        service.deleteAll();

        for (String table : List.of("alerts", "alert_segments", "decisions", "audit_calls", "audit_records")) {
            assertThat(count(table)).as(table).isZero();
        }
    }

    @Test
    void failedCleanupPublishesDegradedStatus() {
        dataSource.destroy();
        EventBus bus = mock(EventBus.class);
        doAnswer(invocation -> published.add(invocation.getArgument(0))).when(bus).publish(any());
        var broken = service(bus, new TransactionTemplate(new DataSourceTransactionManager(
                new SingleConnectionDataSource("jdbc:sqlite::memory:", true))));
        // The fresh in-memory database has no tables, so the first DELETE fails.
        broken.scheduledCleanup();

        assertThat(published).hasSize(1);
        var status = ((EventEnvelope.SystemStatusEvent) published.get(0)).payload();
        assertThat(status.state()).isEqualTo(ComponentState.DEGRADED);
    }
}
