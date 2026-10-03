package pl.aniolstroz.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.aniolstroz.contracts.HitSource;
import pl.aniolstroz.contracts.Mode;
import pl.aniolstroz.contracts.Scenario;
import pl.aniolstroz.contracts.SpeakerLabel;
import pl.aniolstroz.contracts.StageHit;
import pl.aniolstroz.contracts.StageId;
import pl.aniolstroz.contracts.TranscriptSegment;
import pl.aniolstroz.demo.ScenarioRepository;
import pl.aniolstroz.risk.QuoteValidator;

/** MOCK mode: canned answers from resources/mocks, no network. */
class MockStageClassifierTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    private final MockStageClassifier classifier = new MockStageClassifier(mapper);
    private final List<Scenario> scenarios = new ScenarioRepository(mapper).all();

    private Scenario scenario(String id) {
        return scenarios.stream().filter(s -> s.scenarioId().equals(id)).findFirst().orElseThrow();
    }

    private static List<TranscriptSegment> segments(Scenario scenario, int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new TranscriptSegment("c", "s" + (i + 1), 0, 0, scenario.segments().get(i).text(),
                        true, scenario.segments().get(i).speaker(), null))
                .toList();
    }

    private ClassifierResult classify(String scenarioId, int segmentCount) {
        Scenario scenario = scenario(scenarioId);
        return classifier.classify(new CallSnapshot("c", Mode.MOCK, scenarioId, segments(scenario, segmentCount)));
    }

    @Test
    void itIsAMock() {
        assertThat(classifier.isMock()).isTrue();
    }

    @Test
    void answersStartEmptyAndGrowAsTheTranscriptGrows() {
        assertThat(classify("01-fake-police-classic", 2).hits()).isEmpty();
        assertThat(classify("01-fake-police-classic", 3).hits()).extracting(StageHit::stage)
                .containsExactly(StageId.AUTHORITY_CLAIM);
        assertThat(classify("01-fake-police-classic", 9).hits()).extracting(StageHit::stage)
                .contains(StageId.AUTHORITY_CLAIM, StageId.URGENT_THREAT, StageId.SECRECY_DEMAND)
                .doesNotContain(StageId.MONEY_REQUEST);
        assertThat(classify("01-fake-police-classic", 16).hits()).extracting(StageHit::stage)
                .contains(StageId.MONEY_REQUEST, StageId.PAYMENT_CHANNEL);
    }

    @Test
    void hitsLookLikeTheRealModelsNotYetValidatedLlmHits() {
        ClassifierResult result = classify("01-fake-police-classic", 3);

        assertThat(result.hits()).allSatisfy(h -> {
            assertThat(h.source()).isEqualTo(HitSource.LLM);
            assertThat(h.validated()).isFalse();
        });
    }

    @Test
    void resultIsMarkedAsMockWithoutUsageOrError() {
        ClassifierResult result = classify("01-fake-police-classic", 3);

        assertThat(result.error()).isNull();
        assertThat(result.model()).isEqualTo("mock");
        assertThat(result.stopReason()).isEqualTo("mock");
        assertThat(result.usage()).isEqualTo(new ClassifierResult.Usage(0, 0, 0));
        assertThat(result.segmentRange()).isEqualTo("s1-s3");
        assertThat(result.rawOutput()).contains("stage_hits");
    }

    @Test
    void unknownOrMissingScenarioGivesAnEmptyAnswerNotAnError() {
        CallSnapshot unknown = new CallSnapshot("c", Mode.MOCK, "no-such-scenario",
                segments(scenario("04-real-grandson"), 3));
        CallSnapshot none = new CallSnapshot("c", Mode.MOCK, null, segments(scenario("04-real-grandson"), 3));

        assertThat(classifier.classify(unknown).hits()).isEmpty();
        assertThat(classifier.classify(unknown).error()).isNull();
        assertThat(classifier.classify(none).hits()).isEmpty();
    }

    @Test
    void scenarioIdCannotEscapeTheMocksDirectory() {
        CallSnapshot sneaky = new CallSnapshot("c", Mode.MOCK, "../application",
                segments(scenario("04-real-grandson"), 3));

        assertThat(classifier.classify(sneaky).hits()).isEmpty();
    }

    @Test
    void theParaphraseScenarioIsTheCaseWhereTheAiSeesWhatKeywordsMiss() {
        assertThat(classify("02-fake-police-paraphrase", 13).hits()).extracting(StageHit::stage)
                .contains(StageId.SECRECY_DEMAND, StageId.MONEY_REQUEST, StageId.PAYMENT_CHANNEL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"01-fake-police-classic", "02-fake-police-paraphrase", "03-fake-bank-code"})
    void everyCannedQuoteIsVerbatimInItsScenario(String scenarioId) {
        Scenario scenario = scenario(scenarioId);
        List<TranscriptSegment> all = segments(scenario, scenario.segments().size());

        List<StageHit> validated = QuoteValidator.validate(
                classifier.classify(new CallSnapshot("c", Mode.MOCK, scenarioId, all)).hits(), all);

        assertThat(validated).isNotEmpty().allSatisfy(h ->
                assertThat(h.validated()).as(h.segId() + ": " + h.quote()).isTrue());
    }

    @Test
    void speakerLabelsDoNotMatter() {
        Scenario scenario = scenario("01-fake-police-classic");
        List<TranscriptSegment> relabelled = segments(scenario, 3).stream()
                .map(s -> new TranscriptSegment(s.callId(), s.segId(), 0, 0, s.text(), true, SpeakerLabel.UNKNOWN, null))
                .toList();

        assertThat(classifier.classify(new CallSnapshot("c", Mode.MOCK, "01-fake-police-classic", relabelled)).hits())
                .hasSize(1);
    }
}
