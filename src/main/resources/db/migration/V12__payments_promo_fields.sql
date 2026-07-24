-- V12: payment promo fields + tochka index for durable billing

ALTER TABLE payments ADD COLUMN promo_code TEXT;
ALTER TABLE payments ADD COLUMN discount_percent INTEGER NOT NULL DEFAULT 0;
ALTER TABLE payments ADD COLUMN list_amount_kopecks INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_payments_tochka_id ON payments(tochka_payment_id);
CREATE INDEX IF NOT EXISTS idx_payments_device_status ON payments(device_id, status);
