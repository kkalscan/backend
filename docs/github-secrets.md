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

## Vision (OpenRouter)

| Secret | Default | Описание |
|--------|---------|----------|
| `VISION_PROVIDER` | `stub` | `stub` — тест без API; `openrouter` — prod |
| `OPENROUTER_API_KEY` | — | Ключ [openrouter.ai/keys](https://openrouter.ai/keys) |
| `OPENROUTER_MODEL` | `google/gemini-2.0-flash-001` | Любая vision-модель OpenRouter |
| `OPENROUTER_BASE_URL` | `https://openrouter.ai/api/v1` | Обычно не менять |
| `OPENROUTER_APP_URL` | `http://91.207.75.72:8080` | Referer для OpenRouter |
| `OPENROUTER_APP_NAME` | `KkalScan` | X-Title для OpenRouter |
| `VISION_MONTHLY_BUDGET_RUB` | `5000` | Hard stop расходов |
| `VISION_COST_PER_REQUEST_RUB` | `1` | Учёт в budget |

**Смена модели:** поменяй только `OPENROUTER_MODEL`, код не трогаем.

Примеры моделей:
- `google/gemini-2.0-flash-001` — дёшево, быстро
- `openai/gpt-4o-mini` — альтернатива
- `anthropic/claude-3.5-haiku` — если нужен Claude

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
| `TOCHKA_MERCHANT_ID` | Точка API |
| `TOCHKA_SECRET_KEY` | Точка API |
| `TOCHKA_WEBHOOK_SECRET` | Webhook |
| `VK_APP_ID` | VK ID |
| `VK_SERVICE_TOKEN` | VK verify |
| `JWT_ISSUER` | `kkalscan` |
| `JWT_TTL_SECONDS` | `2592000` |

---

## Prod checklist

```env
VISION_PROVIDER=openrouter
OPENROUTER_API_KEY=sk-or-v1-...
OPENROUTER_MODEL=google/gemini-2.0-flash-001
```

Dev / CI tests:
```env
VISION_PROVIDER=stub
```

---

## Локально

```bash
cp .env.example .env
# VISION_PROVIDER=stub  или openrouter + OPENROUTER_API_KEY
./gradlew run
```
