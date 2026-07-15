CREATE TABLE promo_purchases (
    id TEXT PRIMARY KEY,
    payment_id TEXT NOT NULL,
    device_id TEXT NOT NULL,
    tariff TEXT NOT NULL,
    amount_kopecks INTEGER NOT NULL,
    list_amount_kopecks INTEGER NOT NULL,
    promo_code TEXT,
    discount_percent INTEGER NOT NULL DEFAULT 0,
    status TEXT NOT NULL,
    paid_at TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX idx_promo_purchases_code ON promo_purchases(promo_code);
CREATE INDEX idx_promo_purchases_paid_at ON promo_purchases(paid_at);
