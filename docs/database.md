# Схема базы данных

| Поле | Значение |
|------|----------|
| Версия | 1.0 |
| Дата | 2026-06-14 |
| MVP | SQLite в Docker volume |
| ORM | Exposed + Flyway migrations |

---

## 1. ER-диаграмма

```
users ─────────────┬── oauth_identities
  │                │
  │ pro_until      │
  │                │
  └── devices ─────┼── scan_quota (per device, per date)
                   │
                   └── diary_entries ── diary_dishes

scan_sessions (device_id, result JSON, consumed flag)
payments (device_id, tochka_payment_id, status)
vision_usage (month, cost_rub) — budget tracking
```

---

## 2. Таблицы

### `users`

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | |
| `pro_until` | TIMESTAMP NULL | источник правды для Pro после link |
| `created_at` | TIMESTAMP | |

### `devices`

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | = client device_id |
| `user_id` | UUID FK NULL | после OAuth merge |
| `pro_until` | TIMESTAMP NULL | до link; после merge → users |
| `created_at` | TIMESTAMP | |
| `last_seen_at` | TIMESTAMP | |

Index: `user_id`

### `oauth_identities`

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | |
| `user_id` | UUID FK | |
| `provider` | VARCHAR | `vk`, `yandex` |
| `provider_user_id` | VARCHAR | |
| `created_at` | TIMESTAMP | |

Unique: `(provider, provider_user_id)`

### `scan_quota`

Дневная квота per device (local date от клиента).

| Column | Type | Notes |
|--------|------|-------|
| `device_id` | UUID FK | |
| `quota_date` | DATE | local date пользователя |
| `scans_used` | INT | default 0 |
| `bonus_granted` | BOOLEAN | default false |
| `bonus_scans` | INT | +2 если bonus_granted |

PK: `(device_id, quota_date)`

**scans_left** = `(3 + bonus_scans - scans_used)` если не Pro.

### `scan_sessions`

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | scan_id в API |
| `device_id` | UUID FK | |
| `dishes_json` | TEXT | JSON array |
| `consumed` | BOOLEAN | true после diary/entries |
| `created_at` | TIMESTAMP | |

Фото **не** сохраняется.

### `diary_entries`

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | |
| `device_id` | UUID FK | |
| `user_id` | UUID FK NULL | denorm после merge |
| `meal_type` | VARCHAR | breakfast/lunch/dinner/snack |
| `scan_session_id` | UUID FK NULL | |
| `total_kcal` | INT | |
| `created_at` | TIMESTAMP | |

Index: `(device_id, created_at)`, `(user_id, created_at)`

### `diary_dishes`

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | |
| `entry_id` | UUID FK | |
| `name` | VARCHAR | |
| `grams` | INT | |
| `kcal` | INT | |
| `protein` | DOUBLE | |
| `fat` | DOUBLE | |
| `carbs` | DOUBLE | |
| `fiber` | DOUBLE | default 0 |

### `payments`

| Column | Type | Notes |
|--------|------|-------|
| `id` | UUID PK | |
| `device_id` | UUID FK | |
| `user_id` | UUID FK NULL | |
| `tochka_payment_id` | VARCHAR | |
| `amount_kopecks` | INT | 19900 |
| `tariff` | VARCHAR | pro_monthly_199 |
| `status` | VARCHAR | pending/paid/failed |
| `created_at` | TIMESTAMP | |
| `paid_at` | TIMESTAMP NULL | |

### `vision_usage`

| Column | Type | Notes |
|--------|------|-------|
| `month` | VARCHAR | `2026-06` |
| `cost_rub` | INT | накопительно |
| `request_count` | INT | |

---

## 3. Миграции (Flyway)

```
src/main/resources/db/migration/
├── V1__init_schema.sql
├── V2__auth_users_oauth.sql
└── V3__payments.sql
```

---

## 4. Merge device → user (транзакция)

```sql
BEGIN;
  UPDATE devices SET user_id = :user_id WHERE id = :device_id;
  UPDATE diary_entries SET user_id = :user_id WHERE device_id = :device_id;
  UPDATE users SET pro_until = GREATEST(pro_until, :device_pro_until) WHERE id = :user_id;
  UPDATE devices SET pro_until = NULL WHERE id = :device_id; -- canonical on user
COMMIT;
```

---

## 5. PostgreSQL (v0.2)

Замена SQLite: `DATABASE_URL=jdbc:postgresql://…` — схема та же, типы UUID/TIMESTAMP native.

---

## 6. История

| Версия | Дата | Изменения |
|--------|------|-----------|
| 1.0 | 2026-06-14 | Первая версия |
