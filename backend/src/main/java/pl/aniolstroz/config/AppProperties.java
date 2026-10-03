package pl.aniolstroz.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;
import pl.aniolstroz.contracts.Mode;

/**
 * Typed application configuration (prefix {@code app}). Secrets (ANTHROPIC_API_KEY) are deliberately not bound here;
 * they are read from the environment where used.
 */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotNull @DefaultValue("SCRIPTED") Mode mode,
        @Valid @NotNull @DefaultValue Claude claude,
        @Valid @NotNull @DefaultValue Events events,
        @Valid @NotNull @DefaultValue Labels labels,
        @Valid @NotNull @DefaultValue Audit audit,
        @Valid @NotNull @DefaultValue Stt stt) {

    /**
     * Speech-to-text (AUD-03). {@code provider} is {@code vosk} (local, offline) or {@code fake} (tests, no speech
     * recognition). The Vosk model is not in the repository; see scripts/download-vosk-model.sh.
     */
    public record Stt(
            @NotNull @DefaultValue("vosk") Provider provider,
            @Valid @NotNull @DefaultValue Vosk vosk) {

        public enum Provider { VOSK, FAKE }

        /** {@code modelPath}: directory of the unpacked model; env APP_STT_VOSK_MODEL_PATH. */
        public record Vosk(@NotBlank @DefaultValue("models/vosk-model-small-pl-0.22") String modelPath) {
        }
    }

    /** AI audit settings (OBS-04). */
    public record Audit(@Valid @NotNull @DefaultValue Pricing pricing) {

        /**
         * Claude prices in USD per 1M tokens, used to compute cost from usage. Defaults are the Sonnet 5.5 prices
         * (input 2, cache read 0.20, output 10). Change them here, never in code, when the price list changes.
         */
        public record Pricing(
                @NotNull @PositiveOrZero @DefaultValue("2") BigDecimal inputPerMillionUsd,
                @NotNull @PositiveOrZero @DefaultValue("0.20") BigDecimal cacheReadPerMillionUsd,
                @NotNull @PositiveOrZero @DefaultValue("10") BigDecimal outputPerMillionUsd) {
        }
    }

    /** Where false_alarm and confirmed_scam decisions are appended as evaluation labels (AI-10). */
    public record Labels(@NotBlank @DefaultValue("data/labels.jsonl") String file) {
    }

    public record Claude(
            @NotBlank @DefaultValue("claude-sonnet-5-5") String model,
            @Positive @DefaultValue("2500") long timeoutMs) {
    }

    /** /ws/events settings. Empty {@code allowedOrigins} means same origin only (API-04). */
    public record Events(
            @NotNull @DefaultValue List<String> allowedOrigins,
            @Positive @DefaultValue("5000") int sendTimeLimitMs,
            @Positive @DefaultValue("262144") int bufferSizeLimitBytes,
            @Positive @DefaultValue("10000") long heartbeatIntervalMs) {
    }
}
