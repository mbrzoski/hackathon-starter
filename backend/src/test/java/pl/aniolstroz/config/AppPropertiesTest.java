package pl.aniolstroz.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

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
    void unknownModeFailsStartup() {
        runner.withPropertyValues("app.mode=PRODUCTION")
                .run(context -> assertThat(context).hasFailed());
    }
}
