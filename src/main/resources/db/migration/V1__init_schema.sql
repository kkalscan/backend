-- V1: core schema (see docs/database.md)

CREATE TABLE users (
    id TEXT PRIMARY KEY,
    pro_until TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE devices (
    id TEXT PRIMARY KEY,
    user_id TEXT REFERENCES users(id),
    pro_until TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    last_seen_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_devices_user_id ON devices(user_id);

CREATE TABLE oauth_identities (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL REFERENCES users(id),
    provider TEXT NOT NULL,
    provider_user_id TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(provider, provider_user_id)
);

CREATE TABLE scan_quota (
    device_id TEXT NOT NULL REFERENCES devices(id),
    quota_date TEXT NOT NULL,
    scans_used INTEGER NOT NULL DEFAULT 0,
    bonus_granted INTEGER NOT NULL DEFAULT 0,
    bonus_scans INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (device_id, quota_date)
);

CREATE TABLE scan_sessions (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES devices(id),
    dishes_json TEXT NOT NULL,
    consumed INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE diary_entries (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES devices(id),
    user_id TEXT REFERENCES users(id),
    meal_type TEXT NOT NULL,
    scan_session_id TEXT REFERENCES scan_sessions(id),
    total_kcal INTEGER NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_diary_device_created ON diary_entries(device_id, created_at);
CREATE INDEX idx_diary_user_created ON diary_entries(user_id, created_at);

CREATE TABLE diary_dishes (
    id TEXT PRIMARY KEY,
    entry_id TEXT NOT NULL REFERENCES diary_entries(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    grams INTEGER NOT NULL,
    kcal INTEGER NOT NULL,
    protein REAL NOT NULL,
    fat REAL NOT NULL,
    carbs REAL NOT NULL
);

CREATE TABLE payments (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES devices(id),
    user_id TEXT REFERENCES users(id),
    tochka_payment_id TEXT,
    amount_kopecks INTEGER NOT NULL,
    tariff TEXT NOT NULL,
    status TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    paid_at TEXT
);

CREATE TABLE vision_usage (
    month TEXT PRIMARY KEY,
    cost_rub INTEGER NOT NULL DEFAULT 0,
    request_count INTEGER NOT NULL DEFAULT 0
);
