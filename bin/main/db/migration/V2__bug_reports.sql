CREATE TABLE bug_reports (
    id TEXT PRIMARY KEY,
    device_id TEXT NOT NULL UNIQUE,
    user_id TEXT,
    email TEXT NOT NULL,
    description TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_bug_reports_device_id ON bug_reports(device_id);

CREATE TABLE bug_report_screenshots (
    id TEXT PRIMARY KEY,
    report_id TEXT NOT NULL REFERENCES bug_reports(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    content_type TEXT NOT NULL DEFAULT 'image/jpeg',
    data BLOB NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_bug_report_screenshots_report_id ON bug_report_screenshots(report_id);
