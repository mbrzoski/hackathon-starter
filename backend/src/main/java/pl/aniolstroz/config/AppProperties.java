package pl.aniolstroz.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.*;

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
        @Valid @NotNull @DefaultValue Stt stt,
        @Valid @NotNull @DefaultValue Retention retention) {

    /** How long alerts, excerpts, decisions and audit rows are kept (DAT-02): 1 to 90 days, 30 by default. */
    public record Retention(@Min(1) @Max(90) @DefaultValue("30") int days) {
    }

    /**
     * Speech-to-text (AUD-03). {@code provider} is {@code vosk} (local, offline) or {@code fake} (tests, no speech
     * recognition). The Vosk model is not in the repository; see scripts/download-vosk-model.sh.
     */
    public record Stt(
            @NotNull @DefaultValue("vosk") Provider provider,
            @Valid @NotNull @DefaultValue Vosk vosk,
            @Valid @NotNull @DefaultValue Silence silence,
            @Valid @NotNull @DefaultValue Call call) {

        public enum Provider { VOSK, FAKE }

        /**
         * When a LIVE call begins and ends on an armed /ws/audio session. A frame is speech when its RMS (of 32768) is at
         * least {@code speechRms}; {@code speechFrames} speech frames in a row open a call, and {@code silenceMs} without
         * speech end it (env APP_STT_CALL_SPEECH_RMS, APP_STT_CALL_SPEECH_FRAMES, APP_STT_CALL_SILENCE_MS).
         */
        public record Call(
                @Positive @DefaultValue("10000") long silenceMs,
                @Min(0) @DefaultValue("300") int speechRms,
                @Positive @DefaultValue("3") int speechFrames) {
        }

        /**
         * OBS-02: no frames, or only flat ones, for {@code timeoutMs} (10 s) mean the audio is lost; the check runs
         * every {@code checkIntervalMs} (CC-04).
         */
        public record Silence(
                @Positive @DefaultValue("10000") long timeoutMs,
                @Positive @DefaultValue("1000") long checkIntervalMs) {
        }

        /** {@code modelPath}: directory of the unpacked model; env APP_STT_VOSK_MODEL_PATH. */
        public record Vosk(@NotBlank @DefaultValue("models/vosk-model-small-pl-0.22") String modelPath) {
        }
    }

    /** AI audit settings (OBS-04). */
    public record Audit(@Valid @NotNull @DefaultValue Pricing pricing) {

        /**
         * Claude prices in USD per 1M tokens, used to compute cost from usage. Defaults are the Sonnet 5.5 prices
         * (input 2, cache read 0.20, output 10) and the 5-minute cache write price of 1.25 times the input price (2.50);
         * check the last one against the price list. Change them here, never in code, when the price list changes.
         */
        public record Pricing(
                @NotNull @PositiveOrZero @DefaultValue("2") BigDecimal inputPerMillionUsd,
                @NotNull @PositiveOrZero @DefaultValue("0.20") BigDecimal cacheReadPerMillionUsd,
                @NotNull @PositiveOrZero @DefaultValue("10") BigDecimal outputPerMillionUsd,
                @NotNull @PositiveOrZero @DefaultValue("2.50") BigDecimal cacheCreationPerMillionUsd) {
        }
    }

    /** Where false_alarm and confirmed_scam decisions are appended as evaluation labels (AI-10). */
    public record Labels(@NotBlank @DefaultValue("data/labels.jsonl") String file) {
    }

    public record Claude(
            @NotBlank @DefaultValue("claude-sonnet-5-5") String model,
            @Positive @DefaultValue("8000") long timeoutMs,
            @Positive @DefaultValue("1024") long maxTokens) {
    }

    /** /ws/events settings. Empty {@code allowedOrigins} means same origin only (API-04). */
    public record Events(
            @NotNull @DefaultValue List<String> allowedOrigins,
            @Positive @DefaultValue("5000") int sendTimeLimitMs,
            @Positive @DefaultValue("262144") int bufferSizeLimitBytes,
            @Positive @DefaultValue("10000") long heartbeatIntervalMs) {
    }
}
