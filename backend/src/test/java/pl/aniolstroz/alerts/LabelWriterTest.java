package pl.aniolstroz.alerts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.aniolstroz.contracts.Actor;
import pl.aniolstroz.contracts.Alert;
import pl.aniolstroz.contracts.Decision;
import pl.aniolstroz.contracts.DecisionType;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.TriggeredBy;

class LabelWriterTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static Alert alert(String id) {
        return new Alert(id, "call-1", RiskLevel.MEDIUM, List.of(), "medium-general", "Krótko.", "Rada.",
                TriggeredBy.LLM, Instant.parse("2026-10-03T21:00:00Z"), Mode.REPLAY);
    }

    private static Decision decision(String alertId) {
        return new Decision(alertId, Actor.FAMILY, DecisionType.CONFIRMED_SCAM, Instant.parse("2026-10-03T21:05:00Z"));
    }

    @Test
    void createsMissingDirectoriesAndAppendsLines(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("a").resolve("b").resolve("labels.jsonl");
        LabelWriter writer = new LabelWriter(file, mapper);

        writer.append(alert("a-1"), decision("a-1"));
        writer.append(alert("a-2"), decision("a-2"));

        List<String> lines = Files.readAllLines(file);
        assertThat(lines).hasSize(2);
        assertThat(mapper.readTree(lines.get(0)).get("alertId").asText()).isEqualTo("a-1");
        assertThat(mapper.readTree(lines.get(1)).get("alertId").asText()).isEqualTo("a-2");
        assertThat(mapper.readTree(lines.get(0)).get("stages").isArray()).isTrue();
        assertThat(mapper.readTree(lines.get(0)).get("mode").asText()).isEqualTo("REPLAY");
    }

    @Test
    void keepsExistingLinesFromEarlierRuns(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("labels.jsonl");
        Files.writeString(file, "{\"alertId\":\"old\"}\n");

        new LabelWriter(file, mapper).append(alert("a-1"), decision("a-1"));

        assertThat(Files.readAllLines(file)).hasSize(2);
        assertThat(Files.readAllLines(file).get(0)).isEqualTo("{\"alertId\":\"old\"}");
    }

    @Test
    void concurrentAppendsNeverShareALine(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("labels.jsonl");
        LabelWriter writer = new LabelWriter(file, mapper);
        int writers = 20;
        CountDownLatch done = new CountDownLatch(writers);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < writers; i++) {
                String id = "a-" + i;
                executor.submit(() -> {
                    try {
                        writer.append(alert(id), decision(id));
                    } catch (IOException e) {
                        throw new IllegalStateException(e);
                    } finally {
                        done.countDown();
                    }
                });
            }
            done.await();
        }

        List<String> lines = Files.readAllLines(file);
        assertThat(lines).hasSize(writers);
        for (String line : lines) {
            assertThat(mapper.readTree(line).get("alertId").asText()).startsWith("a-");
        }
    }
}
