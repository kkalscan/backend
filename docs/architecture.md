# Архитектура backend KkalScan

| Поле | Значение |
|------|----------|
| Версия | 1.0 |
| Дата | 2026-06-14 |
| Стек | Kotlin 2.1, Ktor 3, Exposed, HikariCP, SQLite / PostgreSQL |

**Связанные документы:** [api.md](./api.md), [database.md](./database.md), [mobile FR](https://github.com/kkalscan/mobile/blob/main/docs/mvp-functional-requirements.md)

---

## 1. Назначение

Backend — единый источник правды для:

- распознавания еды (прокси к Vision API);
- дневника питания;
- дневных лимитов сканов и бонуса за рекламу;
- подписки Pro (Точка API);
- привязки аккаунта VK после Pro.

Клиент (KMP) **не** хранит лимиты и дневник как source of truth — только `device_id`, JWT (если есть) и UI-кэш.

---

## 2. Контекст (C4 — контейнер)

```
┌─────────────────┐     HTTP      ┌──────────────────────┐
│  Android (KMP)  │ ────────────► │  kkalscan-api :8080  │
│  Decompose+Ktor │ ◄──────────── │  Ktor + Netty        │
└─────────────────┘               └──────────┬───────────┘
                                               │
                    ┌──────────────────────────┼──────────────────────────┐
                    ▼                          ▼                          ▼
            ┌───────────────┐        ┌───────────────┐        ┌───────────────┐
            │ SQLite volume │        │ Gemini / GPT  │        │ Tochka API    │
            │ (MVP)         │        │ Vision API    │        │ (payments)    │
            └───────────────┘        └───────────────┘        └───────────────┘
                    │                          │
                    └──────────────┬───────────┘
                                   ▼
                           ┌───────────────┐
                           │ VK API        │
                           │ (token verify)│
                           └───────────────┘
```

---

## 3. Слои приложения (Ktor)

```
routes/          HTTP: парсинг запроса, status codes, DTO
    ↓
domain/          Use cases: ScanFood, AddDiaryEntry, CheckQuota, MergeAccount
    ↓
data/            Repositories + Exposed DAO
    ↓
integrations/    VisionClient, TochkaClient, VkAuthClient
```

**Правило:** routes не вызывают Exposed напрямую; domain не знает про Ktor `ApplicationCall`.

---

## 4. Ktor plugins

| Plugin | Назначение |
|--------|------------|
| `ContentNegotiation` | JSON (kotlinx.serialization) |
| `StatusPages` | Единый формат ошибок `{ "error": "…", "message": "…" }` |
| `CallLogging` | request id, path, duration |
| `Authentication` | JWT (`Bearer`) + optional guest via `X-Device-Id` |
| `CORS` | MVP: `*` или отключён (только mobile) |
| `Micrometer` | опционально v0.1.1 |

---

## 5. Идентификация запросов

```
Guest:     X-Device-Id: <uuid>
Pro+JWT:   Authorization: Bearer <jwt> + X-Device-Id (текущее устройство)
```

`IdentityResolver` в domain:

1. Если есть valid JWT → `user_id`, все `device_id` пользователя.
2. Иначе → `device_id` из header/query; auto-create row в `devices`.

---

## 6. Основные use cases

| Use case | Триггер | Ключевая логика |
|----------|---------|-----------------|
| `ScanFood` | POST `/scan` | quota check → Vision API → JSON dishes (лимит **не** списывается здесь) |
| `ConsumeScan` | POST `/diary/entries` с `scan_id` | списать 1 скан с квоты (или Pro skip) |
| `GrantAdBonus` | POST `/scan/bonus` | +2, max 1/day |
| `GetDiaryDay` | GET `/diary` | entries + total_kcal + scans_left |
| `ActivatePro` | Tochka webhook | `pro_until += 30 days` на device/user |
| `LinkVkAccount` | POST `/auth/vk` | verify VK token, merge device → user |

**Важно (FR):** лимит списывается при **добавлении в дневник**, не при ошибке Vision API.

---

## 7. Vision API

| Параметр | Значение |
|----------|----------|
| Провайдер | **OpenRouter** (`OPENROUTER_MODEL`) |
| Фото | multipart JPEG, max ~500 KB (клиент сжимает) |
| Хранение фото | **Не хранить** после ответа (NFR-06) |
| Hard stop | ~5 000 ₽/мес → HTTP 503 `vision_budget_exceeded` |
| Промпт | еда, ответ JSON на русском |
| Таймаут | 30 сек |

Интерфейс:

```kotlin
interface VisionClient {
    suspend fun analyzeFood(imageBytes: ByteArray): List<DishRecognition>
}
```

---

## 8. Платежи (Точка)

| Route | Кто вызывает |
|-------|--------------|
| `GET /pay?device_id=` | Браузер (Custom Tabs) — HTML + redirect на create |
| `POST /api/v1/payments/tochka/create` | internal / pay page |
| `POST /api/v1/payments/tochka/webhook` | Точка (подпись webhook) |

Metadata платежа: `device_id`, tariff `pro_monthly_199`, amount `19900` kopecks.

---

## 9. Конфигурация (environment)

| Variable | Описание |
|----------|----------|
| `PORT` | 8080 |
| `DATABASE_URL` | `jdbc:sqlite:/data/kkalscan.db` |
| `JWT_SECRET` | HS256 secret |
| `JWT_ISSUER` | `kkalscan` |
| `VISION_PROVIDER` | `stub` \| `openrouter` |
| `OPENROUTER_API_KEY` | Ключ OpenRouter |
| `OPENROUTER_MODEL` | Модель (меняется без деплоя кода) |
| `VISION_MONTHLY_BUDGET_RUB` | 5000 |
| `TOCHKA_*` | merchant, webhook secret |
| `VK_APP_ID`, `VK_SERVICE_TOKEN` | verify VK tokens |

---

## 10. Модули Gradle (monolith MVP)

Один модуль `:app` — без multi-module до v0.2.

Пакеты:

```
ru.kkalscan
├── Application.kt
├── config/AppConfig.kt
├── plugins/
├── routes/
├── domain/
│   ├── model/
│   ├── service/
│   └── port/          # interfaces for integrations
├── data/
│   ├── table/
│   └── repository/
└── integrations/
```

---

## 11. Тестирование

| Уровень | Инструмент |
|---------|------------|
| Unit | kotlin.test + fakes для repositories |
| Domain | Kotest BehaviorSpec |
| API | Ktor `testApplication` |
| Contract | Mock VisionClient |

---

## 12. Out of scope v0.1

- GraphQL, gRPC
- Очереди (Kafka/Rabbit) — синхронный scan достаточен
- PostgreSQL — опционально; SQLite по умолчанию
- Yandex OAuth verify — v0.1.1
- Admin panel

---

## 13. История

| Версия | Дата | Изменения |
|--------|------|-----------|
| 1.0 | 2026-06-14 | Первая версия |
