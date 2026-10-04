package pl.aniolstroz.settings;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import pl.aniolstroz.call.CallService;
import pl.aniolstroz.contracts.Mode;

/** DELETE /api/calls/{callId}: the family erases everything stored about one call, and only that call. */
@SpringBootTest(properties = "app.events.heartbeat-interval-ms=3600000")
@AutoConfigureMockMvc
class DeleteCallDataTest {

    private static final String SPEC = "contract/openapi.yaml";

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    CallService calls;
    @Value("${app.labels.file}")
    String labelsFile;

    private void store(String callId, String alertId) {
        jdbc.sql("INSERT INTO alerts VALUES (:a, :c, 'high', 't', 's', 'a', 'both', '2026-10-04T10:00:00Z', 'MOCK', '[]')")
                .param("a", alertId).param("c", callId).update();
        jdbc.sql("INSERT INTO alert_segments VALUES (:a, 's1', 0, 1, 'tekst', 'A', 1)").param("a", alertId).update();
        jdbc.sql("INSERT INTO decisions (alert_id, actor, decision, decided_at, mode, ignored_stages) "
                + "VALUES (:a, 'senior', 'hung_up', '2026-10-04T10:01:00Z', 'MOCK', '[]')").param("a", alertId).update();
        jdbc.sql("INSERT INTO audit_calls (call_id, mode, started_at) VALUES (:c, 'MOCK', '2026-10-04T10:00:00Z')")
                .param("c", callId).update();
        jdbc.sql("""
                INSERT INTO audit_records (call_id, mode, recorded_at, segment_range, model, effort, input_tokens,
                    cache_read_input_tokens, output_tokens, latency_ms, hits_json, keyword_hits_json, level_before,
                    level_after, hit_count, rejected_hits)
                VALUES (:c, 'MOCK', '2026-10-04T10:00:00Z', 's1-s1', 'm', 'low', 1, 0, 1, 1, '[]', '[]', 'NONE', 'NONE', 0, 0)""")
                .param("c", callId).update();
    }

    private int count(String table, String callId) {
        String where = table.equals("alert_segments") || table.equals("decisions")
                ? "alert_id IN (SELECT alert_id FROM alerts WHERE call_id = :c) OR alert_id LIKE :c || '%'"
                : "call_id = :c";
        return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE " + where).param("c", callId).query(Integer.class).single();
    }

    @BeforeEach
    @AfterEach
    void clean() throws Exception {
        for (String t : new String[] {"alert_segments", "decisions", "alerts", "audit_records", "audit_calls"}) {
            jdbc.sql("DELETE FROM " + t).update();
        }
        Files.deleteIfExists(Path.of(labelsFile));
        calls.end();
    }

    @Test
    void erasesEverythingAboutTheCallAndNothingElse() throws Exception {
        store("call-1", "call-1-alert");
        store("call-2", "call-2-alert");
        Path labels = Path.of(labelsFile);
        Files.createDirectories(labels.getParent());
        Files.writeString(labels, "{\"callId\":\"call-1\"}\n{\"callId\":\"call-2\"}\n");

        mockMvc.perform(delete("/api/calls/call-1")).andExpect(status().isNoContent()).andExpect(openApi().isValid(SPEC));

        for (String t : new String[] {"alerts", "audit_records", "audit_calls"}) {
            assertThat(count(t, "call-1")).as(t).isZero();
            assertThat(count(t, "call-2")).as(t).isEqualTo(1);
        }
        assertThat(jdbc.sql("SELECT COUNT(*) FROM decisions").query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM alert_segments").query(Integer.class).single()).isEqualTo(1);
        assertThat(Files.readString(labels)).doesNotContain("call-1").contains("call-2");
    }

    @Test
    void anUnknownCallIs404() throws Exception {
        mockMvc.perform(delete("/api/calls/nothing-here")).andExpect(status().isNotFound()).andExpect(openApi().isValid(SPEC));
    }

    @Test
    void theCallStillGoingOnIs409() throws Exception {
        String callId = calls.start(Mode.SCRIPTED).callId();
        mockMvc.perform(delete("/api/calls/" + callId)).andExpect(status().isConflict()).andExpect(openApi().isValid(SPEC));
    }
}
