package pl.aniolstroz.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import pl.aniolstroz.config.AppProperties;
import pl.aniolstroz.contracts.Mode;

/**
 * Chooses the classifier. The API key is read from the environment only (rule 8) and never logged. Without a key the
 * dev profile falls back to the mock; every other profile refuses to start rather than quietly fake the AI (rule 7).
 */
@Configuration(proxyBeanMethods = false)
class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);
    private static final String KEY_VARIABLE = "ANTHROPIC_API_KEY";

    enum Choice { MOCK, CLAUDE }

    static Choice choose(Mode appMode, String apiKey, boolean devProfile) {
        if (appMode == Mode.MOCK) {
            return Choice.MOCK;
        }
        if (apiKey != null && !apiKey.isBlank()) {
            return Choice.CLAUDE;
        }
        if (devProfile) {
            return Choice.MOCK;
        }
        throw new IllegalStateException(KEY_VARIABLE + " is not set. Set it, or use app.mode=MOCK for canned answers.");
    }

    @Bean
    StageClassifier stageClassifier(AppProperties properties, Environment environment, Clock clock,
            ObjectMapper mapper) {
        String apiKey = environment.getProperty(KEY_VARIABLE);
        Choice choice = choose(properties.mode(), apiKey, environment.acceptsProfiles(Profiles.of("dev")));
        if (choice == Choice.MOCK) {
            if (properties.mode() != Mode.MOCK) {
                log.warn("{} is not set: the AI layer answers from canned mocks (calls are labelled MOCK).",
                        KEY_VARIABLE);
            }
            return new MockStageClassifier(mapper);
        }
        return ClaudeStageClassifier.create(apiKey, null, properties.claude().model(), properties.claude().maxTokens(),
                Duration.ofMillis(properties.claude().timeoutMs()), clock, mapper);
    }

    /** One virtual thread per classification; the work is blocking HTTP (BE-02). */
    @Bean(destroyMethod = "shutdown")
    ExecutorService aiExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
