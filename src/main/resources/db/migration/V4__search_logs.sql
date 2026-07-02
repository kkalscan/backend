CREATE TABLE search_logs (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL,
    query TEXT NOT NULL,
    query_normalized TEXT NOT NULL,
    source TEXT NOT NULL DEFAULT 'diary',
    results_count INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_search_logs_normalized ON search_logs(query_normalized);
CREATE INDEX idx_search_logs_created_at ON search_logs(created_at);
