package pl.aniolstroz.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import pl.aniolstroz.contracts.Scenario;
import pl.aniolstroz.contracts.SpeakerLabel;

class ScenarioRepositoryTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    private ScenarioRepository from(String... classpathFiles) {
        var resources = java.util.Arrays.stream(classpathFiles).map(ClassPathResource::new)
                .toArray(ClassPathResource[]::new);
        return ScenarioRepository.fromResources(mapper, resources);
    }

    @Test
    void loadsAllBundledScenariosAndTheyPassContractValidation() {
        var repository = new ScenarioRepository(mapper);

        assertThat(repository.all()).hasSize(12);
        assertThat(repository.all().get(0).scenarioId()).isEqualTo("01-fake-police-classic");
        assertThat(repository.find("12-callback-trap")).isPresent();
    }

    @Test
    void keepsFileNameOrderAndFindsById() {
        var repository = from("scenarios-valid/b-second.json", "scenarios-valid/a-first.json");

        assertThat(repository.all()).extracting(Scenario::scenarioId).containsExactly("a-first", "b-second");
        assertThat(repository.find("a-first").orElseThrow().segments()).hasSize(2);
        assertThat(repository.find("a-first").orElseThrow().segments().get(1).speaker())
                .isEqualTo(SpeakerLabel.UNKNOWN);
        assertThat(repository.find("missing")).isEmpty();
    }

    @Test
    void invalidValueNamesTheFileAndTheField() {
        assertThatThrownBy(() -> from("scenarios-invalid/negative-delay.json"))
                .isInstanceOf(ScenarioValidationException.class)
                .hasMessageContaining("negative-delay.json")
                .hasMessageContaining("/segments/1/delayMs");
    }

    @Test
    void missingRequiredFieldNamesTheFieldAndTheFile() {
        assertThatThrownBy(() -> from("scenarios-invalid/missing-title.json"))
                .isInstanceOf(ScenarioValidationException.class)
                .hasMessageContaining("missing-title.json")
                .hasMessageContaining("title");
    }

    @Test
    void unknownPropertyIsRejected() {
        assertThatThrownBy(() -> from("scenarios-invalid/unknown-field.json"))
                .isInstanceOf(ScenarioValidationException.class)
                .hasMessageContaining("unknown-field.json")
                .hasMessageContaining("pauseMs");
    }

    @Test
    void brokenJsonNamesTheFile() {
        assertThatThrownBy(() -> from("scenarios-invalid/broken-json.json"))
                .isInstanceOf(ScenarioValidationException.class)
                .hasMessageContaining("broken-json.json");
    }

    @Test
    void duplicateScenarioIdIsRejected() {
        assertThatThrownBy(() -> from("scenarios-valid/a-first.json", "scenarios-valid/a-first.json"))
                .isInstanceOf(ScenarioValidationException.class)
                .hasMessageContaining("duplicate");
    }
}
