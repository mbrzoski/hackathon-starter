package pl.aniolstroz.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.core.io.ClassPathResource;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.StageHit;

/**
 * MOCK mode: no network, no AI. Answers come from {@code resources/mocks/<scenarioId>.json}: each step applies once
 * the transcript has reached {@code afterSegment}, and answers are cumulative like those of the real model, which
 * returns all hits every time. A scenario without a file gets an empty answer, not an error. The calls it serves are
 * labelled MOCK, so nobody takes them for real AI results.
 */
public final class MockStageClassifier implements StageClassifier {

    private static final Pattern SAFE_ID = Pattern.compile("^[a-z0-9-]+$");

    private record Step(int afterSegment, @JsonProperty("stage_hits") List<StageHitsResponse.Hit> stageHits) {
    }

    private record MockFile(String scenarioId, String description, List<Step> steps) {
    }

    private final ObjectMapper mapper;
    private final Map<String, Optional<MockFile>> files = new ConcurrentHashMap<>();

    public MockStageClassifier(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean isMock() {
        return true;
    }

    @Override
    public ClassifierResult classify(CallSnapshot snapshot) {
        List<StageHit> hits = file(snapshot.scenarioId())
                .map(f -> f.steps().stream()
                        .filter(step -> step.afterSegment() <= snapshot.lastSegmentNumber())
                        .flatMap(step -> step.stageHits().stream())
                        .map(hit -> new StageHit(hit.stage(), hit.segmentId(), hit.quote(), hit.speakerRole(),
                                HitSource.LLM, false))
                        .toList())
                .orElse(List.of());
        return new ClassifierResult(snapshot.segmentRange(), "mock", "none", rawOutput(hits), hits,
                new ClassifierResult.Usage(0, 0, 0), 0, "mock", null);
    }

    private Optional<MockFile> file(String scenarioId) {
        if (scenarioId == null || !SAFE_ID.matcher(scenarioId).matches()) {
            return Optional.empty();
        }
        return files.computeIfAbsent(scenarioId, this::load);
    }

    private Optional<MockFile> load(String scenarioId) {
        ClassPathResource resource = new ClassPathResource("mocks/" + scenarioId + ".json");
        if (!resource.exists()) {
            return Optional.empty();
        }
        try (InputStream in = resource.getInputStream()) {
            return Optional.of(mapper.readValue(in, MockFile.class));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read mocks/" + scenarioId + ".json", e);
        }
    }

    private String rawOutput(List<StageHit> hits) {
        List<StageHitsResponse.Hit> raw = hits.stream()
                .map(h -> new StageHitsResponse.Hit(h.stage(), h.segId(), h.quote(), h.speakerRole())).toList();
        try {
            return mapper.writeValueAsString(new StageHitsResponse(raw));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize mock answer", e);
        }
    }
}
