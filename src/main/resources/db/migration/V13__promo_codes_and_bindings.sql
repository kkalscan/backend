CREATE TABLE promo_codes (
    code TEXT PRIMARY KEY,
    discount_percent INTEGER NOT NULL,
    active INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE device_promo_bindings (
    device_id TEXT PRIMARY KEY,
    promo_code TEXT NOT NULL,
    bound_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_device_promo_bindings_code ON device_promo_bindings(promo_code);

INSERT INTO promo_codes (code, discount_percent, active) VALUES ('Lida', 50, 1);
