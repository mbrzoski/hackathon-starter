package com.hackathonstarter.llm;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Generic audit row for every model call: useful for cost/latency checks during the demo. */
@Entity
@Table(name = "llm_call_log")
public class LlmCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private String model;

    @Column(name = "input_tokens", nullable = false)
    private int inputTokens;

    @Column(name = "output_tokens", nullable = false)
    private int outputTokens;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(nullable = false)
    private String status;

    @Column(name = "error_message")
    private String errorMessage;

    protected LlmCallLog() {}

    public LlmCallLog(String model, int inputTokens, int outputTokens, long latencyMs, String status, String errorMessage) {
        this.model = model;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.latencyMs = latencyMs;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public Long getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public String getModel() { return model; }
    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public long getLatencyMs() { return latencyMs; }
    public String getStatus() { return status; }
    public String getErrorMessage() { return errorMessage; }
}
