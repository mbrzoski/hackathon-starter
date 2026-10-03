package pl.aniolstroz.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import pl.aniolstroz.contracts.Mode;

class AppPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AppProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    @Test
    void defaultsMatchTheSpecification() {
        runner.run(context -> {
            AppProperties props = context.getBean(AppProperties.class);
            assertThat(props.mode()).isEqualTo(Mode.SCRIPTED);
            assertThat(props.claude().model()).isEqualTo("claude-sonnet-5-5");
            assertThat(props.claude().timeoutMs()).isEqualTo(2500);
        });
    }

    @Test
    void nonPositiveTimeoutFailsStartup() {
        runner.withPropertyValues("app.claude.timeout-ms=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void blankModelFailsStartup() {
        runner.withPropertyValues("app.claude.model=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void auditPricingDefaultsToSonnet55PricesPerMillionTokens() {
        runner.run(context -> {
            AppProperties.Audit.Pricing pricing = context.getBean(AppProperties.class).audit().pricing();
            assertThat(pricing.inputPerMillionUsd()).isEqualByComparingTo("2");
            assertThat(pricing.cacheReadPerMillionUsd()).isEqualByComparingTo("0.20");
            assertThat(pricing.outputPerMillionUsd()).isEqualByComparingTo("10");
        });
    }

    @Test
    void auditPricingCanBeChangedInConfiguration() {
        runner.withPropertyValues("app.audit.pricing.input-per-million-usd=1.5")
                .run(context -> assertThat(context.getBean(AppProperties.class).audit().pricing().inputPerMillionUsd())
                        .isEqualByComparingTo("1.5"));
    }

    @Test
    void negativePriceFailsStartup() {
        runner.withPropertyValues("app.audit.pricing.output-per-million-usd=-1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void unknownModeFailsStartup() {
        runner.withPropertyValues("app.mode=PRODUCTION")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void sttDefaultsToLocalVoskWithTheSmallPolishModel() {
        runner.run(context -> {
            AppProperties.Stt stt = context.getBean(AppProperties.class).stt();
            assertThat(stt.provider()).isEqualTo(AppProperties.Stt.Provider.VOSK);
            assertThat(stt.vosk().modelPath()).isEqualTo("models/vosk-model-small-pl-0.22");
        });
    }

    @Test
    void sttProviderAndModelPathCanBeChanged() {
        runner.withPropertyValues("app.stt.provider=fake", "app.stt.vosk.model-path=/opt/model")
                .run(context -> {
                    AppProperties.Stt stt = context.getBean(AppProperties.class).stt();
                    assertThat(stt.provider()).isEqualTo(AppProperties.Stt.Provider.FAKE);
                    assertThat(stt.vosk().modelPath()).isEqualTo("/opt/model");
                });
    }

    @Test
    void unknownSttProviderOrBlankModelPathFailsStartup() {
        runner.withPropertyValues("app.stt.provider=azure").run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("app.stt.vosk.model-path=").run(context -> assertThat(context).hasFailed());
    }
}
