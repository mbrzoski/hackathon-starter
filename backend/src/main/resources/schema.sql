-- Alerted calls only (DAT-01): the alert and the cited transcript segments with +-2 segments of context.
-- Calls without an alert never reach this database.
CREATE TABLE IF NOT EXISTS alerts (
    alert_id     TEXT PRIMARY KEY,
    call_id      TEXT NOT NULL,
    level        TEXT NOT NULL,
    template_id  TEXT NOT NULL,
    short_text   TEXT NOT NULL,
    advice       TEXT NOT NULL,
    triggered_by TEXT NOT NULL,
    created_at   TEXT NOT NULL,
    mode         TEXT NOT NULL,
    stages_json  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS alert_segments (
    alert_id   TEXT NOT NULL REFERENCES alerts (alert_id) ON DELETE CASCADE,
    seg_id     TEXT NOT NULL,
    t_start_ms INTEGER NOT NULL,
    t_end_ms   INTEGER NOT NULL,
    text       TEXT NOT NULL,
    speaker    TEXT NOT NULL,
    cited      INTEGER NOT NULL,
    PRIMARY KEY (alert_id, seg_id)
);

-- Decisions of people about an alert. An alert can have several. No foreign key: the alert of an active call is
-- decided before it is written to alerts (which happens when the call ends).
CREATE TABLE IF NOT EXISTS decisions (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    alert_id       TEXT NOT NULL,
    actor          TEXT NOT NULL,
    decision       TEXT NOT NULL,
    decided_at     TEXT NOT NULL,
    mode           TEXT NOT NULL,
    ignored_stages TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_decisions_alert ON decisions (alert_id);

-- AI audit (OBS-03). One row per classifier call. Call text lives in memory until the call ends (rule 4, DAT-03):
-- raw_output is NULL and the quotes are removed from the hit lists (text_cleared = 1) while a call runs and after a
-- call without an alert. After a call with an alert the text is written and text_cleared is 0. The numbers stay.
CREATE TABLE IF NOT EXISTS audit_calls (
    call_id    TEXT PRIMARY KEY,
    mode       TEXT NOT NULL,
    started_at TEXT NOT NULL,
    ended_at   TEXT,
    max_level  TEXT NOT NULL DEFAULT 'NONE',
    had_alert  INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS audit_records (
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    call_id                TEXT NOT NULL,
    mode                   TEXT NOT NULL,
    recorded_at            TEXT NOT NULL,
    segment_range          TEXT NOT NULL,
    model                  TEXT NOT NULL,
    effort                 TEXT NOT NULL,
    input_tokens           INTEGER NOT NULL,
    cache_read_input_tokens INTEGER NOT NULL,
    output_tokens          INTEGER NOT NULL,
    cache_creation_input_tokens INTEGER NOT NULL DEFAULT 0,
    latency_ms             INTEGER NOT NULL,
    stop_reason            TEXT,
    error                  TEXT,
    raw_output             TEXT,
    hits_json              TEXT NOT NULL,
    keyword_hits_json      TEXT NOT NULL,
    level_before           TEXT NOT NULL,
    level_after            TEXT NOT NULL,
    hit_count              INTEGER NOT NULL,
    rejected_hits          INTEGER NOT NULL,
    text_cleared           INTEGER NOT NULL DEFAULT 0,
    late                   INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_audit_records_call ON audit_records (call_id);

-- Protection switch (admin portal). One row; no row means on. Not touched by DELETE /api/data: it is a setting, not data.
CREATE TABLE IF NOT EXISTS protection (
    id      INTEGER PRIMARY KEY CHECK (id = 1),
    enabled INTEGER NOT NULL
);



-- Household settings (setup wizard): consents, trusted contacts, sensitivity, retention, senior's name. One JSON row;
-- no row means the defaults (no consent). A setting, not data: not touched by DELETE /api/data. Never sent to Claude.
CREATE TABLE IF NOT EXISTS settings (
    id   INTEGER PRIMARY KEY CHECK (id = 1),
    json TEXT NOT NULL
);
