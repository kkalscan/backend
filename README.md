# KkalScan Backend

REST API для [KkalScan](https://github.com/kkalscan/mobile): скан еды по фото, дневник, лимиты, Pro, OAuth после оплаты.

| Параметр | Значение |
|----------|----------|
| Стек | **Kotlin**, **Ktor 3**, Exposed, SQLite (MVP) |
| Base URL | `http://91.207.75.72:8080/api/v1/` |
| Контейнер | `kkalscan-api` |
| Клиент | [kkalscan/mobile](https://github.com/kkalscan/mobile) |

## Документация

| Файл | Описание |
|------|----------|
| [docs/architecture.md](docs/architecture.md) | Модули, слои, интеграции |
| [docs/api.md](docs/api.md) | REST API v1 — HTTP контракты |
| [docs/methods.md](docs/methods.md) | **Каталог методов** — services, repositories, routes |
| [docs/database.md](docs/database.md) | Схема БД, миграции |
| [docs/scan-quota.md](docs/scan-quota.md) | Лимиты сканов и бизнес-правила |
| [docs/auth.md](docs/auth.md) | JWT, VK ID, merge device → user |
| [docs/deployment.md](docs/deployment.md) | Docker, env, деплой на 91.207.75.72 |
| [docs/github-secrets.md](docs/github-secrets.md) | **Все secrets для GitHub Actions** |

## CI/CD

GitHub Actions: `.github/workflows/ci-cd.yml`

| Job | Trigger | Действие |
|-----|---------|----------|
| **test** | push/PR → `main`, `master` | `./gradlew test shadowJar` |
| **deploy** | push → `main`, `master` | secrets → `.env` → SCP → `docker compose up` |

**Все production-секреты — только в GitHub Actions.** Список: [docs/github-secrets.md](docs/github-secrets.md)

Минимум для деплоя: `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`, `JWT_SECRET`.

### Ручной деплой (без CI)

```bash
./gradlew shadowJar
cp build/libs/*-all.jar app.jar
scp app.jar docker-compose.prod.yml Dockerfile.prod user@91.207.75.72:/opt/kkalscan/
ssh user@91.207.75.72 "cd /opt/kkalscan && docker compose -f docker-compose.prod.yml up -d --build"
```

## Быстрый старт (dev)

```bash
cp .env.example .env   # заполнить VISION_API_KEY, JWT_SECRET
./gradlew run
curl http://localhost:8080/health
```

## Структура проекта

```
src/main/kotlin/ru/kkalscan/
├── Application.kt          # entry point
├── plugins/                # Ktor plugins (routing, auth, serialization)
├── routes/                 # HTTP handlers
├── domain/                 # use cases, business rules
├── data/                   # repositories, Exposed tables
└── integrations/           # Vision API, Tochka, VK
```

## Эндпоинты (кратко)

| Метод | Path | Назначение |
|-------|------|------------|
| GET | `/health` | Healthcheck |
| POST | `/api/v1/scan` | Фото → блюда + КБЖУ |
| POST | `/api/v1/scan/bonus` | +2 скана за рекламу |
| GET | `/api/v1/diary` | Дневник за день |
| POST | `/api/v1/diary/entries` | Добавить приём пищи |
| DELETE | `/api/v1/diary/entries/{id}` | Удалить запись |
| GET | `/api/v1/subscription/status` | Pro + account_linked |
| POST | `/api/v1/auth/vk` | Привязка VK после Pro |
| POST | `/api/v1/payments/tochka/webhook` | Webhook оплаты |
| GET | `/pay` | Страница оплаты (HTML) |
| GET | `/privacy` | Политика конфиденциальности |
