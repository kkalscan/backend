CREATE TABLE workout_entries (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL REFERENCES devices(id),
    user_id TEXT REFERENCES users(id),
    name TEXT NOT NULL,
    kcal INTEGER NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_workout_device_created ON workout_entries(device_id, created_at);
CREATE INDEX idx_workout_user_created ON workout_entries(user_id, created_at);
