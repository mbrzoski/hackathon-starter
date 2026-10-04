package pl.aniolstroz.alerts;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import pl.aniolstroz.config.AppProperties;
import pl.aniolstroz.contracts.Actor;
import pl.aniolstroz.contracts.Alert;
import pl.aniolstroz.contracts.Decision;
import pl.aniolstroz.contracts.DecisionType;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.StageId;
import pl.aniolstroz.risk.RiskEngine;

/**
 * Appends one JSON line per false_alarm or confirmed_scam decision to {@code data/labels.jsonl}.
 *
 * <p>These labels are material for the next round of human evaluation only. They never change the model, the prompt,
 * the keyword dictionary or the risk rules automatically (AI-10): nothing in the backend reads this file.
 * A line carries stage ids and no quotes, so no call text is copied. {@code mode} is part of every line so that
 * SCRIPTED, MOCK and REPLAY labels can be told apart from LIVE ones.
 */
@Component
public class LabelWriter {

    private record Label(String alertId, String callId, Mode mode, List<StageId> stages, DecisionType decision,
            Actor actor, Instant at) {
    }

    private final Path file;
    private final ObjectMapper mapper;
    private final ReentrantLock lock = new ReentrantLock();

    @Autowired
    LabelWriter(AppProperties properties, ObjectMapper mapper) {
        this(Path.of(properties.labels().file()), mapper);
    }

    public LabelWriter(Path file, ObjectMapper mapper) {
        this.file = file.toAbsolutePath();
        this.mapper = mapper;
    }

    /** DELETE /api/data: the labels are stored data too (DAT-02). */
    public void deleteAll() throws IOException {
        lock.lock();
        try {
            Files.deleteIfExists(file);
        } finally {
            lock.unlock();
        }
    }

    /** Removes the labels of one call (its data was erased by the family). */
    public void deleteCall(String callId) throws IOException {
        lock.lock();
        try {
            if (!Files.exists(file)) {
                return;
            }
            List<String> kept = new java.util.ArrayList<>();
            for (String line : Files.readAllLines(file)) {
                if (line.isBlank() || !callId.equals(mapper.readTree(line).path("callId").asText())) {
                    kept.add(line);
                }
            }
            Files.write(file, kept);
        } finally {
            lock.unlock();
        }
    }

    public void append(Alert alert, Decision decision) throws IOException {
        List<StageId> stages = List.copyOf(RiskEngine.stagesOf(alert.stages()));
        String line = mapper.writeValueAsString(new Label(alert.alertId(), alert.callId(), alert.mode(), stages,
                decision.decision(), decision.actor(), decision.at())) + "\n";
        lock.lock();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } finally {
            lock.unlock();
        }
    }
}
