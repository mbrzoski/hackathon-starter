package pl.aniolstroz.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import pl.aniolstroz.contracts.Mode;

class AiConfigTest {

    @Test
    void mockModeAlwaysUsesTheMockEvenWithAKey() {
        assertThat(AiConfig.choose(Mode.MOCK, "sk-ant-x", false)).isEqualTo(AiConfig.Choice.MOCK);
        assertThat(AiConfig.choose(Mode.MOCK, null, true)).isEqualTo(AiConfig.Choice.MOCK);
    }

    @Test
    void aKeyMeansTheRealClassifier() {
        assertThat(AiConfig.choose(Mode.SCRIPTED, "sk-ant-x", true)).isEqualTo(AiConfig.Choice.CLAUDE);
        assertThat(AiConfig.choose(Mode.LIVE, "sk-ant-x", false)).isEqualTo(AiConfig.Choice.CLAUDE);
    }

    @Test
    void noKeyInTheDevProfileFallsBackToTheMock() {
        assertThat(AiConfig.choose(Mode.SCRIPTED, null, true)).isEqualTo(AiConfig.Choice.MOCK);
        assertThat(AiConfig.choose(Mode.SCRIPTED, "  ", true)).isEqualTo(AiConfig.Choice.MOCK);
    }

    @Test
    void noKeyOutsideDevStopsStartupInsteadOfSilentlyFakingTheAi() {
        assertThatThrownBy(() -> AiConfig.choose(Mode.SCRIPTED, null, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ANTHROPIC_API_KEY");
    }
}
