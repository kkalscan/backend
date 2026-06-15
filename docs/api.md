# REST API v1 — KkalScan Backend

| Поле | Значение |
|------|----------|
| Версия | 1.1 |
| Дата | 2026-06-14 |
| Base URL | `http://91.207.75.72:8080/api/v1/` |
| Методы | [methods.md](./methods.md) — services, repositories, routes |

---

## 1. Общие правила

### Headers

| Header | Обязательность | Описание |
|--------|----------------|----------|
| `X-Device-Id` | Guest: да | UUID v4 устройства |
| `Authorization` | После VK link | `Bearer <jwt>` |
| `Content-Type` | JSON / multipart | по эндпоинту |

### Формат ошибок

```json
{
  "error": "limit_hit",
  "message": "Дневной лимит сканов исчерпан",
  "scans_left": 0
}
```

### Коды

| HTTP | error | Когда |
|------|-------|-------|
| 400 | `bad_request` | невалидный UUID, тело |
| 401 | `unauthorized` | невалидный JWT |
| 403 | `forbidden` | чужая запись дневника |
| 404 | `not_found` | entry id |
| 409 | `bonus_already_used` | повторный бонус за рекламу |
| 409 | `bug_report_already_used` | повторный баг-репорт с этого устройства |
| 429 | `limit_hit` | лимит сканов |
| 503 | `vision_budget_exceeded` | потолок Vision API |
| 503 | `vision_unavailable` | таймаут / ошибка LLM |
| 500 | `internal_error` | необработанное |

---

## 2. Health

### `GET /health`

Без auth. Для Docker healthcheck.

**200:**
```json
{ "status": "ok", "version": "0.1.0" }
```

---

## 3. Scan

### `POST /api/v1/scan`

**multipart/form-data:**

| Field | Type | Required |
|-------|------|----------|
| `device_id` | string (UUID) | да* |
| `photo` | file (image/jpeg) | да |
| `timezone_offset_minutes` | int | нет, default client offset |

\* или `X-Device-Id` header

**Логика:**

1. Resolve device, check Pro / quota (**до** Vision).
2. Call Vision API.
3. Сохранить `scan_sessions` (без файла фото) с результатом.
4. **Не списывать** квоту — списание при `POST /diary/entries`.

**200:**
```json
{
  "scan_id": "uuid",
  "dishes": [
    {
      "name": "Борщ с говядиной",
      "grams": 300,
      "kcal": 180,
      "protein": 8.5,
      "fat": 6.2,
      "carbs": 22.1
    }
  ],
  "total_kcal": 180,
  "total_protein": 8.5,
  "total_fat": 6.2,
  "total_carbs": 22.1,
  "scans_left": 2,
  "is_pro": false,
  "disclaimer": "Оценка приблизительная, не медицинский совет"
}
```

**429:**
```json
{
  "error": "limit_hit",
  "message": "На сегодня бесплатные сканы закончились",
  "scans_left": 0
}
```

---

### `POST /api/v1/scan/bonus`

Начисление +2 скана после rewarded video.

**JSON body:**
```json
{
  "device_id": "uuid"
}
```

**200:**
```json
{
  "scans_left": 2,
  "bonus_granted": true
}
```

**409:** бонус уже использован сегодня.

---

## 4. Diary

### `GET /api/v1/diary`

**Query:**

| Param | Required | Example |
|-------|----------|---------|
| `device_id` | да* | uuid |
| `date` | да | `2026-06-14` (ISO local date) |
| `timezone_offset_minutes` | нет | `180` |

**200:**
```json
{
  "date": "2026-06-14",
  "total_kcal": 1840,
  "scans_left": 1,
  "is_pro": false,
  "account_linked": false,
  "linked_providers": [],
  "entries": [
    {
      "id": "uuid",
      "created_at": "2026-06-14T08:15:00+03:00",
      "meal_type": "breakfast",
      "total_kcal": 420,
      "dishes": [
        {
          "name": "Овсянка с бананом",
          "grams": 250,
          "kcal": 320,
          "protein": 12,
          "fat": 8,
          "carbs": 52
        }
      ]
    }
  ]
}
```

`meal_type`: `breakfast` | `lunch` | `dinner` | `snack`

---

### `POST /api/v1/diary/entries`

**JSON body:**
```json
{
  "device_id": "uuid",
  "meal_type": "lunch",
  "scan_id": "uuid-from-scan",
  "dishes": [
    {
      "name": "Борщ",
      "grams": 300,
      "kcal": 180,
      "protein": 8.5,
      "fat": 6.2,
      "carbs": 22.1
    }
  ]
}
```

- `scan_id` — optional; если передан и ещё не consumed → списать 1 скан с квоты.
- `dishes` — required если нет валидного `scan_id`.

**201:** созданная entry (как элемент массива entries выше) + `scans_left`.

**429:** limit при попытке consume scan.

---

### `DELETE /api/v1/diary/entries/{id}`

**Query:** `device_id` или JWT.

**204** — удалено. **403** — не своё. **404** — нет id.

Удаление **не возвращает** скан в квоту.

---

## 5. Subscription

### `GET /api/v1/subscription/status`

**Query:** `device_id`

**200:**
```json
{
  "is_pro": true,
  "pro_until": "2026-07-14T12:00:00+03:00",
  "account_linked": false,
  "linked_providers": [],
  "tariff": "pro_monthly_199"
}
```

---

## 5.1 Feedback

### `POST /api/v1/feedback/bug`

Сообщение о баге. За первый принятый репорт с устройства — **Pro на 30 дней**.

**multipart/form-data:**

| Field | Type | Required |
|-------|------|----------|
| `device_id` | string (UUID) | да* |
| `email` | string | да |
| `description` | string (10…2000) | да |
| `screenshot` | file (image/jpeg, image/png) | нет, до 3 шт., ≤600 KB |

\* или `X-Device-Id` header

**200:**
```json
{
  "report_id": "uuid",
  "is_pro": true,
  "pro_until": "2026-07-14T12:00:00Z",
  "message": "Спасибо! Pro на месяц активирован."
}
```

**409:** `bug_report_already_used` — с этого device_id репорт уже отправляли.

---

## 6. Auth (после Pro)

### `POST /api/v1/auth/vk`

**JSON:**
```json
{
  "device_id": "uuid",
  "access_token": "vk_oauth_token"
}
```

**200:**
```json
{
  "access_token": "jwt…",
  "token_type": "Bearer",
  "expires_in": 2592000,
  "user_id": "uuid-user",
  "is_pro": true,
  "account_linked": true,
  "linked_providers": ["vk"]
}
```

**401:** invalid VK token.

### `POST /api/v1/auth/yandex` (v0.1.1)

Аналогично VK.

### `GET /api/v1/auth/me`

**Header:** `Authorization: Bearer`

**200:**
```json
{
  "user_id": "uuid",
  "is_pro": true,
  "pro_until": "2026-07-14T12:00:00+03:00",
  "linked_providers": ["vk"],
  "devices": ["device-uuid-1"]
}
```

---

## 7. Payments (Tochka)

### `POST /api/v1/payments/tochka/create`

**JSON:**
```json
{
  "device_id": "uuid",
  "tariff": "pro_monthly_199"
}
```

**200:**
```json
{
  "payment_url": "https://…",
  "payment_id": "uuid"
}
```

### `POST /api/v1/payments/tochka/webhook`

Вызывает Точка. Verify signature. Body — по документации Точки.

**200:** `{ "ok": true }`

---

## 8. Static pages

| Path | Описание |
|------|----------|
| `GET /pay?device_id=` | HTML: кнопка оплаты 199 ₽/мес |
| `GET /privacy` | Политика конфиденциальности (HTML/Markdown) |

---

## 9. История

| Версия | Дата | Изменения |
|--------|------|-----------|
| 1.0 | 2026-06-14 | Первая версия API v1 |
| 1.1 | 2026-06-14 | Ссылка на [methods.md](./methods.md) |
