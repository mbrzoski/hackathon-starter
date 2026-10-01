package com.hackathonstarter.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Llm llm, Cors cors) {

    public record Llm(
            String provider,
            String apiKey,
            String baseUrl,
            String model,
            int maxTokens,
            Duration timeout,
            String anthropicVersion) {

        public boolean isMock() {
            return "mock".equalsIgnoreCase(provider);
        }

        public boolean apiKeyConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Cors(List<String> allowedOrigins) {}
}
