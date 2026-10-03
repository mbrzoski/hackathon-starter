package pl.aniolstroz.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Validates the 12 evaluation scenarios in resources/scenarios against the contract record. */
class ScenarioFilesTest {

    private static final List<String> FILES = List.of(
            "01-fake-police-classic", "02-fake-police-paraphrase", "03-fake-bank-code",
            "04-real-grandson", "05-real-police-bike", "06-tv-background",
            "07-senior-refuses-code", "08-slow-scam", "09-heavy-stt-errors",
            "10-prompt-injection", "11-remote-access", "12-callback-trap");

    private static final List<String> KEYWORD_STEMS = List.of(
            "policj", "prokurat", "blik", "przelew", "wyplac", "kurier", "tajemnic");

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    static List<String> files() {
        return FILES;
    }

    private Scenario load(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/scenarios/" + name + ".json")) {
            assertThat(in).as("resource %s", name).isNotNull();
            return mapper.readValue(in, Scenario.class);
        }
    }

    @ParameterizedTest
    @MethodSource("files")
    void scenarioIsValid(String name) throws IOException {
        Scenario s = load(name);

        assertThat(validator.validate(s)).isEmpty();
        assertThat(s.scenarioId()).isEqualTo(name);
        assertThat(s.mode()).isEqualTo(Mode.SCRIPTED);
        assertThat(s.source()).startsWith("Syntetyczne");
        assertThat(s.expected()).isNotNull();
        assertThat(s.segments()).hasSizeBetween(10, 25);
        assertThat(s.segments()).allSatisfy(seg -> assertThat(seg.delayMs()).isBetween(800L, 4000L));
        assertThat(java.util.Collections.disjoint(s.expected().mustHitStages(), s.expected().mustNotHitStages())).isTrue();
    }

    @Test
    void twelveScenariosExist() {
        assertThat(FILES).hasSize(12);
    }

    @Test
    void paraphraseScenarioHasNoKeywords() throws IOException {
        String text = load("02-fake-police-paraphrase").segments().stream()
                .map(Scenario.Segment::text)
                .reduce("", (a, b) -> a + " " + b);

        assertThat(QuoteNormalizer.normalize(text).toLowerCase(Locale.ROOT)).doesNotContain(KEYWORD_STEMS);
    }

    @Test
    void sttErrorScenarioHasNoPolishDiacritics() throws IOException {
        String text = load("09-heavy-stt-errors").segments().stream()
                .map(Scenario.Segment::text)
                .reduce("", (a, b) -> a + " " + b);

        assertThat(text).doesNotContainPattern("[ąćęłńóśźżĄĆĘŁŃÓŚŹŻ]");
    }
}
