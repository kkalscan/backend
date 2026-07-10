CREATE TABLE daily_activity (
    device_id TEXT NOT NULL REFERENCES devices(id),
    user_id TEXT REFERENCES users(id),
    local_date TEXT NOT NULL,
    steps INTEGER NOT NULL DEFAULT 0,
    kcal INTEGER NOT NULL DEFAULT 0,
    source TEXT NOT NULL DEFAULT 'none',
    updated_at TEXT NOT NULL DEFAULT (datetime('now')),
    PRIMARY KEY (device_id, local_date)
);

CREATE INDEX idx_daily_activity_user_date ON daily_activity(user_id, local_date);
