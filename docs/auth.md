# Auth на backend

| Поле | Значение |
|------|----------|
| Версия | 1.0 |
| Дата | 2026-06-14 |
| Модель | Guest по device_id; OAuth **только после Pro** |

**Клиент:** [mobile/docs/auth-after-pro.md](https://github.com/kkalscan/mobile/blob/main/docs/auth-after-pro.md)

---

## 1. Режимы

| Режим | Headers | Backend resolve |
|-------|---------|-----------------|
| Guest | `X-Device-Id` | `devices.id` |
| Authenticated | `Authorization: Bearer` + `X-Device-Id` | `users.id` + link device |

Free-пользователю JWT **не выдаётся**.

---

## 2. JWT

| Claim | Value |
|-------|-------|
| `sub` | user_id (UUID) |
| `iss` | `kkalscan` |
| `exp` | 30 days |
| `device_id` | optional, device при link |

Ktor: `Authentication` plugin, `JWT` verifier HS256, secret из `JWT_SECRET`.

---

## 3. VK ID verify

```
POST https://api.vk.com/method/users.get
  access_token={token}&v=5.131
```

- HTTP 200 + valid user → `provider_user_id = response.id`
- Find or create `users` + `oauth_identities`
- Run merge transaction (see database.md)

**v0.1:** только VK. Yandex: `GET https://login.yandex.ru/info` с OAuth token.

---

## 4. Merge rules

1. Если VK уже привязан к другому user → login as that user (не создавать duplicate Pro).
2. Текущий `device_id` → `user_id`.
3. `diary_entries` и `pro_until` переносятся на user.
4. Если оба device и user had Pro → `pro_until = max()`.

---

## 5. Restore на новом телефоне

```
POST /auth/vk { device_id: NEW, access_token }
→ user found by vk id
→ link NEW device to user
→ JWT with is_pro from user.pro_until
```

---

## 6. Endpoints auth-gated

| Endpoint | Guest | JWT |
|----------|-------|-----|
| GET /diary | ✓ device | ✓ all user devices |
| POST /diary/entries | ✓ | ✓ |
| GET /auth/me | ✗ | ✓ |
| POST /auth/vk | ✓ (needs device_id) | ✓ optional |

---

## 7. Security MVP

- HTTPS — после домена; MVP HTTP по IP.
- Rate limit по IP на `/scan` — v0.1.1.
- Webhook Tochka — verify signature.
- JWT secret — env, не в repo.

---

## 8. История

| Версия | Дата | Изменения |
|--------|------|-----------|
| 1.0 | 2026-06-14 | Первая версия |
