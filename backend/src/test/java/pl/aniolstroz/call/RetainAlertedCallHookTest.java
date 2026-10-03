package pl.aniolstroz.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import pl.aniolstroz.alerts.AlertStore;
import pl.aniolstroz.contracts.ComponentState;
import pl.aniolstroz.contracts.EventEnvelope;
import pl.aniolstroz.contracts.EventEnvelope.CallEndedEvent;
import pl.aniolstroz.contracts.EventEnvelope.SystemStatusEvent;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.SpeakerLabel;
import pl.aniolstroz.contracts.TranscriptSegment;
import pl.aniolstroz.events.EventBus;

/** DAT-01 with a real SQLite database: alerted calls keep the cited segments +-2, the rest is deleted. */
class RetainAlertedCallHookTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-03T21:00:00Z"), ZoneOffset.UTC);

    private final List<EventEnvelope> events = new ArrayList<>();
    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbc;
    private EventBus bus;

    @BeforeEach
    void setUp() {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = JdbcClient.create(dataSource);
        bus = mock(EventBus.class);
        doAnswer(invocation -> events.add(invocation.getArgument(0))).when(bus).publish(any());
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    private AlertStore realStore() {
        return new AlertStore(jdbc, new ObjectMapper(), new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    private CallService serviceWith(AlertStore store) {
        return CallServices.create(bus, CLOCK, new RetainAlertedCallHook(store, bus, CLOCK));
    }

    /** Plays the given texts as final segments s1, s2, ... of a new call. */
    private CallState play(CallService service, String... texts) {
        CallState call = service.start(Mode.SCRIPTED);
        for (String text : texts) {
            service.addSegment(new TranscriptSegment(call.callId(), "x", 0, 0, text, true, SpeakerLabel.B, null));
        }
        return call;
    }

    private static String[] harmless(int count) {
        String[] texts = new String[count];
        for (int i = 0; i < count; i++) {
            texts[i] = "Zwykłe zdanie numer " + (i + 1) + ".";
        }
        return texts;
    }

    private static List<String> ids(List<TranscriptSegment> segments) {
        return segments.stream().map(TranscriptSegment::segId).toList();
    }

    private int count(String table) {
        return jdbc.sql("SELECT count(*) FROM " + table).query(Integer.class).single();
    }

    @Test
    void callWithoutAlertIsDeletedCompletelyAndNothingIsStored() {
        CallService service = serviceWith(realStore());
        CallState call = play(service, harmless(6));

        service.end();

        assertThat(call.transcript()).isEmpty();
        assertThat(count("alerts")).isZero();
        assertThat(count("alert_segments")).isZero();
    }

    @Test
    void alertedCallKeepsCitedSegmentsWithTwoSegmentsOfContextInMemoryAndInTheDatabase() {
        CallService service = serviceWith(realStore());
        String[] texts = harmless(10);
        texts[2] = "Mówi policja.";                 // s3, cited
        texts[4] = "Proszę wypłacić gotówkę.";     // s5, cited, raises the alert (high)
        CallState call = play(service, texts);
        assertThat(call.alerts()).hasSize(1);

        service.end();

        assertThat(ids(call.transcript())).containsExactly("s1", "s2", "s3", "s4", "s5", "s6", "s7");
        assertThat(count("alerts")).isEqualTo(1);
        String alertId = call.alerts().get(0).alertId();
        assertThat(ids(realStore().segmentsOf(alertId))).containsExactly("s1", "s2", "s3", "s4", "s5", "s6", "s7");
        assertThat(jdbc.sql("SELECT seg_id FROM alert_segments WHERE cited = 1 ORDER BY seg_id").query(String.class).list())
                .containsExactly("s3", "s5");
        assertThat(jdbc.sql("SELECT text FROM alert_segments WHERE seg_id = 's5'").query(String.class).single())
                .isEqualTo("Proszę wypłacić gotówkę.");
    }

    @Test
    void segmentsFarFromEveryCitationAreDropped() {
        CallService service = serviceWith(realStore());
        String[] texts = harmless(12);
        texts[0] = "Mówi policja.";                // s1
        texts[1] = "Nikomu nie mów.";              // s2 -> medium alert cites s1, s2
        CallState call = play(service, texts);

        service.end();

        assertThat(ids(call.transcript())).containsExactly("s1", "s2", "s3", "s4");
    }

    @Test
    void everyAlertOfTheCallKeepsItsOwnExcerptAndMemoryHoldsTheUnion() {
        CallService service = serviceWith(realStore());
        String[] texts = harmless(12);
        texts[1] = "Mówi policja. Nikomu nie mów.";  // s2 -> medium alert, cites s2
        texts[8] = "Proszę wypłacić pieniądze.";     // s9 -> high alert, cites s2 and s9
        CallState call = play(service, texts);
        assertThat(call.alerts()).hasSize(2);

        service.end();

        assertThat(count("alerts")).isEqualTo(2);
        assertThat(ids(realStore().segmentsOf(call.alerts().get(0).alertId()))).containsExactly("s1", "s2", "s3", "s4");
        assertThat(ids(realStore().segmentsOf(call.alerts().get(1).alertId())))
                .containsExactly("s1", "s2", "s3", "s4", "s7", "s8", "s9", "s10", "s11");
        assertThat(ids(call.transcript())).containsExactly("s1", "s2", "s3", "s4", "s7", "s8", "s9", "s10", "s11");
    }

    @Test
    void failedStorageIsReportedAndTheExcerptStaysInMemory() {
        AlertStore failing = mock(AlertStore.class);
        doThrow(new DataAccessResourceFailureException("disk")).when(failing).save(any(), any());
        CallService service = serviceWith(failing);
        String[] texts = harmless(8);
        texts[3] = "Mówi policja. Nikomu nie mów.";  // s4 -> alert
        CallState call = play(service, texts);

        service.end();

        assertThat(ids(call.transcript())).containsExactly("s2", "s3", "s4", "s5", "s6");
        SystemStatusEvent status = (SystemStatusEvent) events.stream().filter(SystemStatusEvent.class::isInstance)
                .findFirst().orElseThrow();
        assertThat(status.payload().component()).isEqualTo(pl.aniolstroz.contracts.Component.BACKEND);
        assertThat(status.payload().state()).isEqualTo(ComponentState.DEGRADED);
        assertThat(status.payload().message()).isNotBlank();
        assertThat(status.mode()).isEqualTo(Mode.SCRIPTED);
        assertThat(events.get(events.size() - 1)).isInstanceOf(CallEndedEvent.class);
        assertThat(service.active()).isEmpty();
    }
}
