# Payment gaps — диагностика перед реальной оплатой

Чеклист перед включением оплат Точки в проде. MVP free-mode и in-memory хранилища закрывают happy-path локально, но для живых платежей нужно закрыть пункты ниже.

## 1. Выключить free-активацию

- `FREE_PRO_ACTIVATION=false`
- Пока `true` (default в dev), `POST /payments/pro/start` активирует Pro без Точки

## 2. PUBLIC_BASE_URL по HTTPS

- Redirect / webhook success URL должны быть публичным HTTPS
- Без этого Точка не примет `redirect_url` / `fail_redirect_url`

## 3. Webhook Точки

- Endpoint: `POST /api/v1/payments/tochka/webhook`
- Настроить в кабинете Точки + проверка подписи
- После `PAID`/`SUCCESS`/`APPROVED` → `activatePro` по `payment.tariff`

## 4. Persist payments

- Сейчас платежи в памяти → рестарт теряет pending/paid
- Нужна таблица payments (id, device_id, tochka_id, amount_kopecks, tariff, status, paid_at)

## 5. Persist devices / `pro_until` / promo

- `pro_until` на device/user — в SQLite (или уже частично), иначе Pro пропадает после рестарта
- **Промокаталог**: таблица `promo_codes` (`code` PK, `discount_percent`, `active`)
- **Bind**: `device_id → promo_code` (таблица или колонка), сейчас in-memory

## 6. Tochka env

- JWT / customerCode / merchant credentials из secrets
- Не логировать токены

## 7. Закрыть test/activate

- `POST /payments/test/activate` — только для staging
- В проде: `TEST_PAYMENT_ENABLED=false` (или удалить маршрут)

## 8. Monthly = one-shot +N дней

- Тариф `pro_monthly_199` — разовый платёж, `pro_until = paidAt + 30 дней` из `TariffCatalog`
- Автопродления нет
- Lifetime `pro_lifetime_5000` → +36_500 дней (~100 лет)

## 9. Сумма Точки = после скидки

- List price только в `TariffCatalog` (seed: 200 ₽ / 5000 ₽)
- Bound promo (`POST /promo/apply` → device bind) снижает `amount_kopecks` в `createTochkaPayment` / free start
- Клиент **не** шлёт промокод в `pro/start` — только `tariff`

## 10. Smoke

1. `GET /subscription/offers?device_id=` → 200 и 5000
2. `POST /promo/apply` `{ device_id, promo_code: "Lida" }` → 50%
3. `GET /subscription/offers` → 100 и 2500
4. `POST /payments/pro/start` `{ device_id, tariff: "pro_lifetime_5000" }`  
   - free mode: Pro с далёким `pro_until`  
   - paid mode: Tochka на 250_000 коп. → webhook → Pro
