package pl.aniolstroz.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import pl.aniolstroz.contracts.Mode;

/**
 * Typed application configuration (prefix {@code app}). Secrets (ANTHROPIC_API_KEY, AZURE_SPEECH_KEY,
 * AZURE_SPEECH_REGION) are deliberately not bound here; they are read from the environment where used.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull @DefaultValue("SCRIPTED") Mode mode,
        @Valid @NotNull @DefaultValue Claude claude) {

    public record Claude(
            @NotBlank @DefaultValue("claude-sonnet-5-5") String model,
            @Positive @DefaultValue("2500") long timeoutMs) {
    }
}
