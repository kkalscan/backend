# GitHub Secrets — KkalScan Backend

Все production-секреты хранятся в **GitHub → Settings → Secrets and variables → Actions**  
репозитория [kkalscan/backend](https://github.com/kkalscan/backend).

**В git секреты не коммитим.** При деплое CI генерирует `.env` на сервере.

---

## Обязательные

| Secret | Описание | Пример |
|--------|----------|--------|
| `DEPLOY_HOST` | IP сервера | `91.207.75.72` |
| `DEPLOY_USER` | SSH-пользователь | `ubuntu` |
| `DEPLOY_SSH_KEY` | Приватный SSH-ключ (PEM) | `-----BEGIN OPENSSH…` |
| `JWT_SECRET` | HS256, ≥32 символов | `openssl rand -base64 32` |

---

## Vision (stub на MVP)

**`VISION_PROVIDER` не берётся из GitHub Secrets** — в `scripts/generate-env.sh` всегда `stub`.  
Чтобы включить OpenRouter на prod, поменяйте строку в `generate-env.sh` и задеплойте.

| Переменная | Источник | Описание |
|------------|----------|----------|
| `VISION_PROVIDER` | `generate-env.sh` | Захардкожено `stub` |
| `OPENROUTER_API_KEY` | Secret (опционально) | Нужен только при `openrouter` |
| `OPENROUTER_MODEL` | Secret | `google/gemini-2.5-flash` — vision-модель |
| `OPENROUTER_BASE_URL` | Secret | `https://openrouter.ai/api/v1` |
| `OPENROUTER_APP_URL` | Secret | `http://91.207.75.72:8080` |
| `OPENROUTER_APP_NAME` | Secret | `KkalScan` |
| `VISION_MONTHLY_BUDGET_RUB` | Secret | `5000` |
| `VISION_COST_PER_REQUEST_RUB` | Secret | `1` |

**Смена модели:** поменяй только `OPENROUTER_MODEL`, код не трогаем.

Примеры моделей:
- `google/gemini-2.5-flash` — рекомендуется (vision, дёшево)
- `openai/gpt-4o-mini` — альтернатива
- ~~`google/gemini-2.0-flash-001`~~ — **удалена** с OpenRouter, не использовать

`GEMINI_API_KEY` / `OPENAI_API_KEY` **не используются** — всё через OpenRouter.

---

## Деплой (опционально)

| Secret | Default |
|--------|---------|
| `DEPLOY_PATH` | `/opt/kkalscan` |

---

## Платежи и auth

| Secret | Описание |
|--------|----------|
| `TOCHKA_MERCHANT_ID` | ID торговой точки (15 цифр), если несколько |
| `TOCHKA_SECRET_KEY` / `TOCHKA_ACCESS_TOKEN` | JWT Bearer для Tochka OpenAPI |
| `TOCHKA_CUSTOMER_CODE` | *(опционально)* override, если в Точке несколько клиентов |
| `TOCHKA_WEBHOOK_SECRET` | Для dev/stub webhook (prod — RS256 JWT) |
| `PUBLIC_BASE_URL` | HTTPS URL backend для redirect после оплаты |
| `VK_APP_ID` | VK ID |
| `VK_SERVICE_TOKEN` | VK verify |
| `JWT_ISSUER` | `kkalscan` |
| `JWT_TTL_SECONDS` | `2592000` |

---

## Bug report (SMTP)

Письма о багах уходят на `mail@antonbutov.com` через `mail.antonbutov.com:587`.

| Secret | Обязательность | Default в `generate-env.sh` |
|--------|----------------|----------------------------|
| `SMTP_PASSWORD` | **да** (для prod) | — |
| `SMTP_USER` | нет | `mail@antonbutov.com` |
| `SMTP_HOST` | нет | `mail.antonbutov.com` |
| `SMTP_PORT` | нет | `587` |
| `SMTP_FROM` | нет | `mail@antonbutov.com` |
| `BUG_REPORT_NOTIFY_TO` | нет | `mail@antonbutov.com` |
| `SMTP_USE_TLS` | нет | `true` |

Без `SMTP_PASSWORD` на prod баг-репорты сохраняются в БД, но письмо не отправится (ошибка SMTP).

---

## Prod checklist (MVP — stub vision)

Деплой всегда пишет `VISION_PROVIDER=stub` в `.env` на сервере.

Для OpenRouter позже: в `scripts/generate-env.sh` заменить на `VISION_PROVIDER=openrouter` и задать секрет `OPENROUTER_API_KEY`.

```env
VISION_PROVIDER=stub
```

Dev / CI tests — то же самое, локально через `.env.example`.

---

## Локально

```bash
cp .env.example .env
# VISION_PROVIDER=stub  или openrouter + OPENROUTER_API_KEY
./gradlew run
```
