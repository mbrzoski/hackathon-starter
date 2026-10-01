package com.hackathonstarter.llm;

import com.hackathonstarter.llm.model.LlmRequest;
import com.hackathonstarter.llm.model.LlmResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Decorator: logs latency/tokens and stores a best-effort audit row. Never fails the actual call. */
public class LoggingLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LoggingLlmClient.class);

    private final LlmClient delegate;
    private final LlmCallLogRepository repository;
    private final String defaultModel;

    public LoggingLlmClient(LlmClient delegate, LlmCallLogRepository repository, String defaultModel) {
        this.delegate = delegate;
        this.repository = repository;
        this.defaultModel = defaultModel;
    }

    @Override
    public LlmResponse complete(LlmRequest request) {
        long start = System.nanoTime();
        try {
            LlmResponse response = delegate.complete(request);
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.info("LLM call ok model={} in={} out={} stop={} {}ms", response.model(),
                    response.usage().inputTokens(), response.usage().outputTokens(), response.stopReason(), ms);
            save(new LlmCallLog(response.model(), response.usage().inputTokens(), response.usage().outputTokens(), ms, "OK", null));
            return response;
        } catch (RuntimeException e) {
            long ms = (System.nanoTime() - start) / 1_000_000;
            log.warn("LLM call failed after {}ms: {}", ms, e.getMessage());
            String model = request.model() != null ? request.model() : defaultModel;
            save(new LlmCallLog(model, 0, 0, ms, "ERROR", truncate(e.getMessage())));
            throw e;
        }
    }

    private void save(LlmCallLog entry) {
        try {
            repository.save(entry);
        } catch (RuntimeException e) {
            log.warn("Could not store LLM call log: {}", e.getMessage());
        }
    }

    private static String truncate(String s) {
        return s == null ? null : s.length() > 500 ? s.substring(0, 500) : s;
    }
}
