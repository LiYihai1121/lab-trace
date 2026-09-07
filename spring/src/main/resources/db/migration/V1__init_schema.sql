PRAGMA journal_mode = WAL;

CREATE TABLE IF NOT EXISTS users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT UNIQUE NOT NULL,
    password_hash TEXT NOT NULL,
    name          TEXT NOT NULL,
    role          TEXT NOT NULL DEFAULT 'student' CHECK (role IN ('student','admin')),
    created_at    TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS checkin_codes (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    code       TEXT UNIQUE NOT NULL,
    expires_at TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS checkin_records (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id          INTEGER NOT NULL REFERENCES users(id),
    checkin_time     TEXT NOT NULL,
    checkout_time    TEXT,
    duration_minutes INTEGER,
    status           TEXT NOT NULL DEFAULT 'checked_in' CHECK (status IN ('checked_in','completed')),
    code_id          INTEGER
);

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER NOT NULL REFERENCES users(id),
    token_hash TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    used_at    TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_records_user      ON checkin_records(user_id);
CREATE INDEX IF NOT EXISTS idx_records_status    ON checkin_records(status);
CREATE INDEX IF NOT EXISTS idx_records_time      ON checkin_records(checkin_time);
CREATE INDEX IF NOT EXISTS idx_reset_tokens_hash ON password_reset_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_reset_tokens_user ON password_reset_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_codes_expires     ON checkin_codes(expires_at);
CREATE INDEX IF NOT EXISTS idx_records_code      ON checkin_records(code_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_records_user_active
    ON checkin_records(user_id) WHERE status = 'checked_in';
