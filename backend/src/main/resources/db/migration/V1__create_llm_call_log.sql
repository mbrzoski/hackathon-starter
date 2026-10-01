CREATE TABLE llm_call_log (
    id            BIGSERIAL PRIMARY KEY,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    model         VARCHAR(100) NOT NULL,
    input_tokens  INTEGER      NOT NULL DEFAULT 0,
    output_tokens INTEGER      NOT NULL DEFAULT 0,
    latency_ms    BIGINT       NOT NULL DEFAULT 0,
    status        VARCHAR(20)  NOT NULL,
    error_message VARCHAR(500)
);

CREATE INDEX idx_llm_call_log_created_at ON llm_call_log (created_at);
