# Лимиты сканов и квота

| Поле | Значение |
|------|----------|
| Версия | 1.0 |
| Дата | 2026-06-14 |

---

## Константы

```
FREE_SCANS_PER_DAY = 3
AD_BONUS_SCANS     = 2
AD_BONUS_MAX_PER_DAY = 1   # флаг bonus_granted
PRO_TARIFF         = pro_monthly_199
PRO_PRICE_RUB      = 199
```

---

## Когда списывается скан

| Событие | Списание |
|---------|----------|
| POST `/scan` успех | **Нет** |
| POST `/scan` ошибка Vision | **Нет** |
| POST `/diary/entries` с `scan_id` | **Да**, 1 скан (если not Pro и scan не consumed) |
| POST `/diary/entries` без scan_id (ручной ввод v0.2) | по правилам v0.2 |
| DELETE diary entry | **Не возвращает** |

---

## Алгоритм `scans_left`

```kotlin
fun scansLeft(quota: ScanQuota, isPro: Boolean): Int? {
    if (isPro) return null  // unlimited, клиент показывает "∞" или скрывает счётчик
    val allowance = FREE_SCANS_PER_DAY + if (quota.bonusGranted) AD_BONUS_SCANS else 0
    return (allowance - quota.scansUsed).coerceAtLeast(0)
}
```

---

## Сброс дня

- `quota_date` = **локальная дата клиента** (`date` + `timezone_offset_minutes`).
- Сервер не полагается на UTC midnight server-side.
- Новая строка `scan_quota` создаётся при первом запросе за день.

---

## Pro check

```kotlin
fun isPro(device: Device, user: User?): Boolean {
    val until = user?.proUntil ?: device.proUntil
    return until != null && until.isAfter(Instant.now())
}
```

---

## limit_hit

`POST /scan` и consume при diary:

```
if (!isPro && scansLeft == 0) → HTTP 429 limit_hit
```

Pro-пользователь **никогда** не получает 429 по квоте.

---

## Ad bonus

```
POST /scan/bonus
if quota.bonusGranted → 409
else quota.bonusGranted = true; quota.bonusScans = 2
```

Клиент показывает rewarded video **до** вызова API; сервер доверяет клиенту на MVP (v0.2: server-side ad verification optional).

---

## Vision budget

Перед вызовом Vision:

```
if vision_usage.cost_rub >= VISION_MONTHLY_BUDGET_RUB → 503 vision_budget_exceeded
```

После успешного вызова — increment cost estimate.

---

## История

| Версия | Дата | Изменения |
|--------|------|-----------|
| 1.0 | 2026-06-14 | Первая версия |
