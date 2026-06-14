# Деплой KkalScan API

| Поле | Значение |
|------|----------|
| Версия | 1.0 |
| Дата | 2026-06-14 |
| Хост | `91.207.75.72` (butov6101.hlab.kz) |
| Порт | **8080** |

---

## 1. Docker

### Dockerfile

Multi-stage: Gradle build → JRE 21 runtime.

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY build/libs/kkalscan-api-all.jar app.jar
EXPOSE 8080
VOLUME /data
ENV DATABASE_URL=jdbc:sqlite:/data/kkalscan.db
HEALTHCHECK CMD wget -qO- http://localhost:8080/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Fat JAR: Ktor plugin `com.github.johnrengelman.shadow`.

### docker-compose.yml

```yaml
services:
  kkalscan-api:
    image: kkalscan-api:latest
    container_name: kkalscan-api
    restart: unless-stopped
    ports:
      - "8080:8080"
    volumes:
      - kkalscan-data:/data
    env_file:
      - .env

volumes:
  kkalscan-data:
```

---

## 2. Сборка и деплой

**Production:** все секреты в [GitHub Actions Secrets](./github-secrets.md). CI генерирует `.env` и деплоит на push в `main`.

```bash
# Локально — только для dev
cp .env.example .env
./gradlew shadowJar && ./gradlew run
```

---

## 3. Firewall

```bash
sudo ufw allow 8080/tcp
```

---

## 4. Переменные окружения

Шаблон для локальной разработки: `.env.example`  
Production: **GitHub Secrets** → `scripts/generate-env.sh` → `.env` на сервере.

Полный список: [github-secrets.md](./github-secrets.md)

---

## 5. Healthcheck

- Docker: `GET /health`
- Ручной: `curl http://91.207.75.72:8080/health`

---

## 6. Логи

stdout JSON lines (CallLogging). Ротация через Docker log driver.

---

## 7. HTTPS (после DNS)

```
kkalscan.ru → nginx :443 → localhost:8080
certbot --nginx -d kkalscan.ru -d api.kkalscan.ru
```

Обновить webhook URL Точки на `https://api.kkalscan.ru/api/v1/payments/tochka/webhook`.

---

## 8. Backup SQLite

```bash
docker exec kkalscan-api sqlite3 /data/kkalscan.db ".backup /data/backup.db"
# cron daily copy off-server
```

---

## 9. История

| Версия | Дата | Изменения |
|--------|------|-----------|
| 1.0 | 2026-06-14 | Первая версия |
| 1.1 | 2026-06-14 | Секреты через GitHub Actions — [github-secrets.md](./github-secrets.md) |
