package pl.aniolstroz.alerts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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
import pl.aniolstroz.contracts.Alert;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.SpeakerLabel;
import pl.aniolstroz.contracts.SpeakerRole;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;
import pl.aniolstroz.contracts.TranscriptSegment;
import pl.aniolstroz.contracts.TriggeredBy;

class AlertStoreTest {

    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbc;
    private AlertStore store;

    @BeforeEach
    void setUp() {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = JdbcClient.create(dataSource);
        store = new AlertStore(jdbc, new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    private static Alert alert(String id) {
        List<StageHit> stages = List.of(
                new StageHit(StageId.SECRECY_DEMAND, "s2", "Nikomu nie mów.", SpeakerRole.UNCLEAR, HitSource.KEYWORDS, true),
                new StageHit(StageId.MONEY_REQUEST, "s4", "Proszę wypłacić pieniądze.", SpeakerRole.UNCLEAR,
                        HitSource.KEYWORDS, true));
        return new Alert(id, "call-1", RiskLevel.HIGH, stages, "high-secrecy-money", "Krótko.", "Rada.",
                TriggeredBy.KEYWORDS, Instant.parse("2026-10-03T21:00:00Z"), Mode.SCRIPTED);
    }

    private static TranscriptSegment segment(int n) {
        return new TranscriptSegment("call-1", "s" + n, n * 100L, n * 100L + 50, "tekst " + n, true,
                SpeakerLabel.B, null);
    }

    @Test
    void savesAlertRowWithTemplateTextsModeAndLevel() {
        store.save(alert("a-1"), List.of(segment(2)));

        var row = jdbc.sql("SELECT * FROM alerts WHERE alert_id = 'a-1'").query().singleRow();
        assertThat(row).containsEntry("call_id", "call-1")
                .containsEntry("level", "high")
                .containsEntry("template_id", "high-secrecy-money")
                .containsEntry("short_text", "Krótko.")
                .containsEntry("advice", "Rada.")
                .containsEntry("triggered_by", "keywords")
                .containsEntry("mode", "SCRIPTED")
                .containsEntry("created_at", "2026-10-03T21:00:00Z");
        assertThat((String) row.get("stages_json")).contains("Nikomu nie mów.").contains("SECRECY_DEMAND");
    }

    @Test
    void savesTheExcerptAndMarksCitedSegments() {
        store.save(alert("a-1"), List.of(segment(1), segment(2), segment(3), segment(4), segment(5), segment(6)));

        assertThat(store.segmentsOf("a-1")).extracting(TranscriptSegment::segId)
                .containsExactly("s1", "s2", "s3", "s4", "s5", "s6");
        assertThat(store.segmentsOf("a-1").get(1)).isEqualTo(segment(2));
        assertThat(jdbc.sql("SELECT seg_id FROM alert_segments WHERE cited = 1 ORDER BY seg_id").query(String.class).list())
                .containsExactly("s2", "s4");
    }

    @Test
    void saveIsAtomicWhenASegmentInsertFails() {
        List<TranscriptSegment> duplicate = List.of(segment(2), segment(2));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> store.save(alert("a-1"), duplicate))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);

        assertThat(jdbc.sql("SELECT count(*) FROM alerts").query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM alert_segments").query(Integer.class).single()).isZero();
    }

    @Test
    void twoAlertsOfOneCallKeepTheirOwnExcerpts() {
        store.save(alert("a-1"), List.of(segment(2)));
        store.save(alert("a-2"), List.of(segment(2), segment(3)));

        assertThat(store.segmentsOf("a-1")).hasSize(1);
        assertThat(store.segmentsOf("a-2")).hasSize(2);
    }
}
