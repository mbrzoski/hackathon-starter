package pl.aniolstroz.call;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pl.aniolstroz.ai.StageClassifier;

/** The model decides; the dictionary is only a safety net (rule 7). */
class KeywordBaselineTest {

    private static final StageClassifier REAL = snapshot -> null;
    private static final StageClassifier MOCK = new StageClassifier() {
        @Override
        public pl.aniolstroz.ai.ClassifierResult classify(pl.aniolstroz.ai.CallSnapshot snapshot) {
            return null;
        }

        @Override
        public boolean isMock() {
            return true;
        }
    };

    @Test
    void fallbackStaysOutWhileTheRealModelAnswers() {
        assertThat(new KeywordBaseline("fallback", REAL, new AiHealth()).active()).isFalse();
    }

    @Test
    void fallbackStepsInWithoutARealModel() {
        assertThat(new KeywordBaseline("fallback", MOCK, new AiHealth()).active()).isTrue();
    }

    @Test
    void fallbackStepsInWhenTheModelFailed() {
        AiHealth health = new AiHealth();
        KeywordBaseline baseline = new KeywordBaseline("fallback", REAL, health);
        health.failed();
        assertThat(baseline.active()).isTrue();
        health.succeeded();
        assertThat(baseline.active()).isFalse();
    }

    @Test
    void alwaysAndOffDoNotLookAtTheModel() {
        AiHealth health = new AiHealth();
        health.failed();
        assertThat(new KeywordBaseline("always", REAL, new AiHealth()).active()).isTrue();
        assertThat(new KeywordBaseline("OFF", MOCK, health).active()).isFalse();
    }
}
