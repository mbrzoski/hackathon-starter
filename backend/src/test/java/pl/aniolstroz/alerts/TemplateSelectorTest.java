package pl.aniolstroz.alerts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.aniolstroz.contracts.StageId.AUTHORITY_CLAIM;
import static pl.aniolstroz.contracts.StageId.ISOLATION;
import static pl.aniolstroz.contracts.StageId.MONEY_REQUEST;
import static pl.aniolstroz.contracts.StageId.PAYMENT_CHANNEL;
import static pl.aniolstroz.contracts.StageId.REMOTE_ACCESS;
import static pl.aniolstroz.contracts.StageId.SECRECY_DEMAND;
import static pl.aniolstroz.contracts.StageId.URGENT_THREAT;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import pl.aniolstroz.contracts.RiskLevel;
import pl.aniolstroz.contracts.StageId;

/** DET-04: the template is picked by a pure function of level and stage set, and its text is pre-written. */
class TemplateSelectorTest {

    static Stream<Arguments> selections() {
        return Stream.of(
                Arguments.of(RiskLevel.HIGH, EnumSet.of(SECRECY_DEMAND, MONEY_REQUEST), "high-secrecy-money"),
                Arguments.of(RiskLevel.HIGH, EnumSet.of(AUTHORITY_CLAIM, SECRECY_DEMAND, MONEY_REQUEST,
                        PAYMENT_CHANNEL), "high-secrecy-money"),
                Arguments.of(RiskLevel.HIGH, EnumSet.of(AUTHORITY_CLAIM, PAYMENT_CHANNEL), "high-payment-channel"),
                Arguments.of(RiskLevel.HIGH, EnumSet.of(SECRECY_DEMAND, PAYMENT_CHANNEL), "high-payment-channel"),
                Arguments.of(RiskLevel.HIGH, EnumSet.of(AUTHORITY_CLAIM, MONEY_REQUEST), "high-general"),
                Arguments.of(RiskLevel.HIGH, EnumSet.of(URGENT_THREAT, MONEY_REQUEST, ISOLATION), "high-general"),
                Arguments.of(RiskLevel.MEDIUM, EnumSet.of(AUTHORITY_CLAIM, ISOLATION), "medium-isolation"),
                Arguments.of(RiskLevel.MEDIUM, EnumSet.of(ISOLATION, REMOTE_ACCESS, URGENT_THREAT),
                        "medium-isolation"),
                Arguments.of(RiskLevel.MEDIUM, EnumSet.of(AUTHORITY_CLAIM, URGENT_THREAT), "medium-general"),
                Arguments.of(RiskLevel.MEDIUM, EnumSet.of(MONEY_REQUEST, PAYMENT_CHANNEL), "medium-general"));
    }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @MethodSource("selections")
    void picksTemplateByLevelAndStages(RiskLevel level, Set<StageId> stages, String expectedId) {
        assertThat(TemplateSelector.select(level, stages)).contains(expectedId);
    }

    @ParameterizedTest
    @EnumSource(value = RiskLevel.class, names = {"NONE", "LOW"})
    void noTemplateBelowMedium(RiskLevel level) {
        assertThat(TemplateSelector.select(level, EnumSet.allOf(StageId.class))).isEqualTo(Optional.empty());
    }

    @Test
    void sameInputGivesSameTemplateEveryTime() {
        Set<StageId> stages = EnumSet.of(AUTHORITY_CLAIM, MONEY_REQUEST);

        assertThat(TemplateSelector.select(RiskLevel.HIGH, stages)).isEqualTo(TemplateSelector.select(RiskLevel.HIGH, stages));
    }

    @Test
    void everyTemplateTheSelectorCanReturnExistsInTheBundledFile() {
        AlertTemplates templates = AlertTemplates.bundled();

        assertThat(TemplateSelector.ALL_IDS).isNotEmpty().allSatisfy(id -> {
            assertThat(templates.get(id).shortText()).as(id + " shortText").isNotBlank();
            assertThat(templates.get(id).advice()).as(id + " advice").isNotBlank();
        });
    }

    @Test
    void everySelectionIsListedInAllIds() {
        assertThat(selections().map(a -> (String) a.get()[2])).allSatisfy(id ->
                assertThat(TemplateSelector.ALL_IDS).contains(id));
    }

    @Test
    void secrecyAndMoneyTemplateIsReadAsTheAgreedSentences() {
        AlertTemplates.Template t = AlertTemplates.bundled().get("high-secrecy-money");

        assertThat(t.shortText() + " " + t.advice()).isEqualTo(
                "Rozmówca prosi o tajemnicę i o pieniądze. Prawdziwa policja nigdy tego nie robi. Możesz się rozłączyć.");
    }

    @Test
    void isolationTemplateWarnsAboutTheCallbackTrap() {
        assertThat(AlertTemplates.bundled().get("medium-isolation").advice())
                .isEqualTo("Rozłącz się i odczekaj minutę, zanim zadzwonisz pod 112 albo do bliskich.");
    }

    @Test
    void shortTextIsOneSentenceAndNoTemplateShowsPercentagesOrNumbersFromTheCall() {
        AlertTemplates templates = AlertTemplates.bundled();

        assertThat(TemplateSelector.ALL_IDS).allSatisfy(id -> {
            String shortText = templates.get(id).shortText();
            assertThat(shortText).as(id).endsWith(".").doesNotContain("%");
            assertThat(shortText.indexOf('.')).as(id + " is one sentence").isEqualTo(shortText.length() - 1);
        });
    }

    @Test
    void fileWithMissingTemplateIsRejectedWithItsId() {
        String yaml = "templates:\n  medium-general:\n    shortText: Ala.\n    advice: Ola.\n";

        assertThatThrownBy(() -> AlertTemplates.parse(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), "alerts.test.yml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("alerts.test.yml")
                .hasMessageContaining("high-secrecy-money");
    }
}
