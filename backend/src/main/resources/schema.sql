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
