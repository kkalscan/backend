# GitHub Secrets — KkalScan Backend

Все production-секреты хранятся в **GitHub → Settings → Secrets and variables → Actions**  
репозитория [kkalscan/backend](https://github.com/kkalscan/backend).

**В git и на диске локально секреты не коммитим.** При деплое CI генерирует `.env` на сервере из secrets.

---

## Обязательные

| Secret | Описание | Пример |
|--------|----------|--------|
| `DEPLOY_HOST` | IP или hostname сервера | `91.207.75.72` |
| `DEPLOY_USER` | SSH-пользователь | `ubuntu` |
| `DEPLOY_SSH_KEY` | Приватный SSH-ключ (PEM целиком) | `-----BEGIN OPENSSH PRIVATE KEY-----…` |
| `JWT_SECRET` | HS256, минимум 32 символа | `openssl rand -base64 32` |

---

## Деплой (опционально)

| Secret | Default | Описание |
|--------|---------|----------|
| `DEPLOY_PATH` | `/opt/kkalscan` | Папка на сервере |

---

## Приложение (рекомендуется для prod)

| Secret | Default | Описание |
|--------|---------|----------|
| `JWT_ISSUER` | `kkalscan` | Issuer JWT |
| `JWT_TTL_SECONDS` | `2592000` | TTL JWT (30 дней) |
| `VISION_PROVIDER` | `stub` | `stub` \| `gemini` \| `openai` |
| `GEMINI_API_KEY` | — | Ключ Gemini (если provider=gemini) |
| `OPENAI_API_KEY` | — | Ключ OpenAI (если provider=openai) |
| `VISION_MONTHLY_BUDGET_RUB` | `5000` | Потолок расходов Vision |
| `VISION_COST_PER_REQUEST_RUB` | `1` | Учёт стоимости за запрос |
| `TOCHKA_MERCHANT_ID` | — | Точка API |
| `TOCHKA_SECRET_KEY` | — | Точка API |
| `TOCHKA_WEBHOOK_SECRET` | — | Подпись webhook |
| `VK_APP_ID` | — | VK ID |
| `VK_SERVICE_TOKEN` | — | VK verify token |

---

## Инфраструктура (обычно не менять)

| Secret | Default | Описание |
|--------|---------|----------|
| `PORT` | `8080` | Порт контейнера |
| `DATABASE_URL` | `jdbc:sqlite:/data/kkalscan.db` | SQLite в Docker volume |

---

## Как это работает

```
push main
    ↓
GitHub Actions: secrets → scripts/generate-env.sh → .env
    ↓
SCP: app.jar + .env + docker-compose.prod.yml → сервер
    ↓
docker compose up -d --build
```

---

## Первичная настройка сервера (один раз)

```bash
# На сервере — только Docker, без ручного .env
sudo mkdir -p /opt/kkalscan
sudo ufw allow 8080/tcp

# Публичный ключ от DEPLOY_SSH_KEY → ~/.ssh/authorized_keys
```

После добавления secrets в GitHub — любой `git push origin main` задеплоит приложение с актуальным `.env`.

---

## Локальная разработка

Секреты GitHub **не** подтягиваются локально. Скопируй шаблон:

```bash
cp .env.example .env
# заполни вручную или export + ./scripts/generate-env.sh > .env
```

---

## Чеклист перед первым деплоем

- [ ] `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`
- [ ] `JWT_SECRET` (уникальный, ≥32 символов)
- [ ] `VISION_PROVIDER` + ключ API (или `stub` для теста)
- [ ] SSH-ключ добавлен в `authorized_keys` на сервере
- [ ] Порт 8080 открыт
