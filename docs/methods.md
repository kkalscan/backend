# Методы backend — каталог v1

| Поле | Значение |
|------|----------|
| Версия | 1.0 |
| Дата | 2026-06-14 |
| Стек | Kotlin 2.1, Ktor 3 |

**Связанные документы:** [api.md](./api.md), [architecture.md](./architecture.md), [database.md](./database.md), [scan-quota.md](./scan-quota.md), [auth.md](./auth.md)

---

## 1. Карта слоёв

```
HTTP Request
    → routes.*Routes          (parse, status, DTO mapping)
    → domain.service.*Service (бизнес-логика)
    → domain.port.*Repository / *Client (интерфейсы)
    → data.*RepositoryImpl    (Exposed)
    → integrations.*Client    (Vision, VK, Tochka)
```

---

## 2. HTTP → Route → Service

| # | HTTP | Route handler | Service method |
|---|------|---------------|----------------|
| 1 | `GET /health` | `HealthRoutes.get` | — |
| 2 | `POST /api/v1/scan` | `ScanRoutes.postScan` | `ScanService.analyzePhoto` |
| 3 | `POST /api/v1/scan/bonus` | `ScanRoutes.postBonus` | `QuotaService.grantAdBonus` |
| 4 | `GET /api/v1/diary` | `DiaryRoutes.getDiary` | `DiaryService.getDay` |
| 4b | `GET /api/v1/activity/emulator` | `ApiRoutes` | `ActivityEmulatorService.getEmulator` |
| 5 | `POST /api/v1/diary/entries` | `DiaryRoutes.postEntry` | `DiaryService.addEntry` |
| 6 | `DELETE /api/v1/diary/entries/{id}` | `DiaryRoutes.deleteEntry` | `DiaryService.deleteEntry` |
| 7 | `GET /api/v1/subscription/status` | `SubscriptionRoutes.getStatus` | `SubscriptionService.getStatus` |
| 8 | `POST /api/v1/auth/vk` | `AuthRoutes.postVk` | `AuthService.linkVk` |
| 9 | `POST /api/v1/auth/yandex` | `AuthRoutes.postYandex` | `AuthService.linkYandex` |
| 10 | `GET /api/v1/auth/me` | `AuthRoutes.getMe` | `AuthService.getMe` |
| 11 | `POST /api/v1/payments/tochka/create` | `PaymentRoutes.create` | `PaymentService.createTochkaPayment` |
| 12 | `POST /api/v1/payments/tochka/webhook` | `PaymentRoutes.webhook` | `PaymentService.handleTochkaWebhook` |
| 13 | `GET /pay` | `PaymentRoutes.payPage` | `PaymentService.renderPayPage` |
| 14 | `GET /privacy` | `StaticRoutes.privacy` | — |
| 15 | `POST /api/v1/feedback/bug` | `FeedbackRoutes.postBug` | `BugReportService.submitBugReport` |

---

## 3. IdentityResolver

Единая точка идентификации для всех сервисов.

```kotlin
interface IdentityResolver {
    /** Guest или JWT → Actor с device_id и опциональным user_id */
    suspend fun resolve(deviceId: UUID, bearerToken: String?): Actor
}

data class Actor(
    val deviceId: UUID,
    val userId: UUID? = null,
    val isPro: Boolean,
    val accountLinked: Boolean,
    val linkedProviders: List<OAuthProvider>,
)
```

| Метод | Вход | Выход | Побочные эффекты |
|-------|------|-------|------------------|
| `resolve` | `deviceId`, optional JWT | `Actor` | `devices.last_seen_at = now()`; auto-create device |

**Правила:**

- JWT invalid → `401 unauthorized`
- `deviceId` invalid UUID → `400 bad_request`
- `isPro` = max(`users.pro_until`, `devices.pro_until`) > now

---

## 4. ScanService

**Файл:** `domain/service/ScanService.kt`  
**Route:** `routes/ScanRoutes.kt`

### `analyzePhoto`

```kotlin
suspend fun analyzePhoto(
    actor: Actor,
    photoBytes: ByteArray,
    localDate: LocalDate,
    timezoneOffsetMinutes: Int,
): ScanResult
```

| Шаг | Действие |
|-----|----------|
| 1 | Validate JPEG, max 600 KB |
| 2 | `quotaService.canStartScan(actor, localDate)` → false → `LimitHit` |
| 3 | `visionBudgetService.checkBudget()` → exceeded → `VisionBudgetExceeded` |
| 4 | `visionClient.analyzeFood(photoBytes)` → dishes |
| 5 | `visionBudgetService.recordUsage(estimatedCostRub)` |
| 6 | `scanSessionRepository.create(deviceId, dishesJson)` → scanId |
| 7 | Return `ScanResult` (**квота не списывается**) |

**Ответ `ScanResult`:**

```kotlin
data class ScanResult(
    val scanId: UUID,
    val dishes: List<DishDto>,
    val totals: MacroTotals,
    val scansLeft: Int?,      // null если Pro
    val isPro: Boolean,
)
```

| Исключение | HTTP |
|------------|------|
| `LimitHitException` | 429 |
| `VisionBudgetExceededException` | 503 |
| `VisionUnavailableException` | 503 |
| `InvalidPhotoException` | 400 |

---

## 5. QuotaService

**Файл:** `domain/service/QuotaService.kt`

### `canStartScan`

```kotlin
fun canStartScan(actor: Actor, localDate: LocalDate): Boolean
```

- Pro → always `true`
- Free → `scansLeft(quota) > 0` **или** есть не-consumed scan_session с dishes (повторный diary без нового scan — отдельно)

Для `POST /scan`: нужен `scansLeft > 0` **или** Pro (не consumed sessions не дают новый scan без квоты).

**Уточнение:** новый scan требует `scansLeft > 0` (или Pro). Нельзя сканировать при 0 free.

### `consumeScan`

```kotlin
suspend fun consumeScan(
    actor: Actor,
    localDate: LocalDate,
    scanSessionId: UUID?,
): Int?  // scansLeft after consume; null if Pro
```

| Условие | Действие |
|---------|----------|
| Pro | skip, return null |
| `scanSessionId` valid, not consumed | mark consumed, `scans_used += 1` |
| no scanSessionId | `scans_used += 1` (v0.2 manual) |
| `scansLeft == 0` before consume | throw `LimitHitException` |

### `grantAdBonus`

```kotlin
suspend fun grantAdBonus(
    actor: Actor,
    localDate: LocalDate,
): BonusResult
```

| Результат | HTTP |
|-----------|------|
| `bonus_granted = true`, scansLeft +2 | 200 |
| already granted today | 409 `bonus_already_used` |

### `getScansLeft`

```kotlin
suspend fun getScansLeft(actor: Actor, localDate: LocalDate): Int?
```

---

## 6. DiaryService

**Файл:** `domain/service/DiaryService.kt`  
**Route:** `routes/DiaryRoutes.kt`

### `getDay`

```kotlin
suspend fun getDay(
    actor: Actor,
    date: LocalDate,
    timezoneOffsetMinutes: Int,
): DiaryDayResponse
```

- Загрузить entries: by `user_id` if linked else `device_id`
- Sum `total_kcal`
- Attach `scans_left`, `is_pro`, `account_linked`

### `addEntry`

```kotlin
suspend fun addEntry(
    actor: Actor,
    request: CreateDiaryEntryRequest,
    localDate: LocalDate,
): CreateDiaryEntryResponse
```

| Шаг | Действие |
|-----|----------|
| 1 | Resolve dishes: from `scan_id` session **или** request.dishes |
| 2 | If `scan_id`: verify belongs to actor.deviceId, not consumed |
| 3 | `quotaService.consumeScan(...)` |
| 4 | Insert `diary_entries` + `diary_dishes` |
| 5 | Return entry + scansLeft |

**`CreateDiaryEntryRequest`:**

```kotlin
data class CreateDiaryEntryRequest(
    val mealType: MealType,
    val scanId: UUID? = null,
    val dishes: List<DishDto>? = null,
)
```

Validation: `scanId != null XOR dishes != null` (v0.1: one required)

### `deleteEntry`

```kotlin
suspend fun deleteEntry(actor: Actor, entryId: UUID): Unit
```

- Verify ownership (device_id or user_id)
- Cascade delete dishes
- **Не** возвращать квоту

| Исключение | HTTP |
|------------|------|
| `NotFoundException` | 404 |
| `ForbiddenException` | 403 |

---

## 7. SubscriptionService

**Файл:** `domain/service/SubscriptionService.kt`

### `getStatus`

```kotlin
suspend fun getStatus(actor: Actor): SubscriptionStatus
```

```kotlin
data class SubscriptionStatus(
    val isPro: Boolean,
    val proUntil: Instant?,
    val accountLinked: Boolean,
    val linkedProviders: List<OAuthProvider>,
    val tariff: String?,  // pro_monthly_199 if active
)
```

### `activatePro`

```kotlin
suspend fun activatePro(
    deviceId: UUID,
    tariff: String,
    paidAt: Instant,
): Unit
```

- `pro_until = paidAt + 30 days` on device (or user if linked)
- Called from `PaymentService.handleTochkaWebhook`

---

## 8. AuthService

**Файл:** `domain/service/AuthService.kt`  
**Route:** `routes/AuthRoutes.kt`

### `linkVk`

```kotlin
suspend fun linkVk(
    deviceId: UUID,
    vkAccessToken: String,
): AuthTokenResponse
```

| Шаг | Действие |
|-----|----------|
| 1 | `vkClient.verifyToken(vkAccessToken)` → vkUserId |
| 2 | Find or create user + oauth_identity |
| 3 | `accountMergeService.mergeDeviceToUser(deviceId, userId)` |
| 4 | Issue JWT 30d |

### `linkYandex` (v0.1.1)

```kotlin
suspend fun linkYandex(deviceId: UUID, yandexToken: String): AuthTokenResponse
```

### `getMe`

```kotlin
suspend fun getMe(userId: UUID): MeResponse
```

### `issueJwt`

```kotlin
fun issueJwt(userId: UUID, deviceId: UUID?): String
```

---

## 9. AccountMergeService

**Файл:** `domain/service/AccountMergeService.kt`

### `mergeDeviceToUser`

```kotlin
suspend fun mergeDeviceToUser(deviceId: UUID, userId: UUID): Unit
```

Транзакция (см. database.md):

1. `devices.user_id = userId`
2. `diary_entries.user_id` for device
3. `pro_until = max(user, device)` on user; clear device.pro_until
4. Idempotent if already merged

---

## 10. PaymentService

**Файл:** `domain/service/PaymentService.kt`  
**Route:** `routes/PaymentRoutes.kt`

### `createTochkaPayment`

```kotlin
suspend fun createTochkaPayment(
    deviceId: UUID,
    tariff: String = "pro_monthly_199",
): PaymentCreateResponse
```

- Insert `payments` status=pending
- `tochkaClient.createPayment(19900 kopecks, metadata device_id)`
- Return payment_url

### `handleTochkaWebhook`

```kotlin
suspend fun handleTochkaWebhook(rawBody: String, signature: String?): Unit
```

| Шаг | Действие |
|-----|----------|
| 1 | Verify webhook signature |
| 2 | Parse payment status |
| 3 | If paid → `subscriptionService.activatePro(deviceId, ...)` |
| 4 | Update payments.status |

### `renderPayPage`

```kotlin
suspend fun renderPayPage(deviceId: UUID): String  // HTML
```

---

## 11. BugReportService

**Файл:** `domain/service/BugReportService.kt`  
**Route:** `routes/ApiRoutes.kt` → `POST /feedback/bug`

### `submitBugReport`

```kotlin
suspend fun submitBugReport(
    actor: Actor,
    email: String,
    description: String,
    screenshots: List<ByteArray>,
): BugReportResult
```

| Шаг | Действие |
|-----|----------|
| 1 | Validate email (обязателен), description (10…2000 символов), screenshots (0…3, ≤600 KB) |
| 2 | `bugReportRepository.hasReportForDevice(deviceId)` → true → `BugReportAlreadyUsedException` |
| 3 | Сохранить репорт (email, description, count screenshots) |
| 4 | `subscriptionService.activatePro(deviceId, pro_bug_report_30d, now)` — **+30 дней Pro** |
| 5 | `bugReportMailer.sendBugReportNotification(...)` → `mail@antonbutov.com` via `mail.antonbutov.com` |
| 6 | Return `BugReportResult` с `pro_until` |

**Правила награды:**

- Один баг-репорт с наградой **на device_id** (повтор → 409 `bug_report_already_used`)
- Email обязателен — для связи с автором
- Скриншоты опциональны (до 3 шт.)
- Скриншоты сохраняются в SQLite: `bug_reports` + `bug_report_screenshots.data` (BLOB)
- Email на `BUG_REPORT_NOTIFY_TO` (default `mail@antonbutov.com`) через SMTP `SMTP_HOST` (default `mail.antonbutov.com:587`)

| Исключение | HTTP |
|------------|------|
| `BadRequestException` | 400 |
| `BugReportAlreadyUsedException` | 409 |

### BugReportRepository

```kotlin
interface BugReportRepository {
    suspend fun hasReportForDevice(deviceId: UUID): Boolean
    suspend fun create(
        deviceId: UUID,
        userId: UUID?,
        email: String,
        description: String,
        screenshots: List<ByteArray>,
    ): UUID
}
```

---

## 12. VisionClient (integration)

**Файл:** `integrations/vision/VisionClient.kt`

```kotlin
interface VisionClient {
    suspend fun analyzeFood(imageBytes: ByteArray): List<DishDto>
}
```

Implementations: `OpenRouterVisionClient` (prod), `StubVisionClient` (dev/test). Model: `OPENROUTER_MODEL`.

**Prompt contract:** JSON array, Russian names, fields: name, grams, kcal, protein, fat, carbs

---

## 12. VkAuthClient

```kotlin
interface VkAuthClient {
    suspend fun verifyToken(accessToken: String): VkUser
}

data class VkUser(val id: Long)
```

---

## 13. TochkaClient

```kotlin
interface TochkaClient {
    suspend fun createPayment(
        amountKopecks: Int,
        description: String,
        metadata: Map<String, String>,
    ): TochkaPayment

    fun verifyWebhookSignature(body: String, signature: String?): Boolean
}
```

---

## 14. Repositories (ports)

### DeviceRepository

```kotlin
interface DeviceRepository {
    suspend fun findById(id: UUID): Device?
    suspend fun getOrCreate(id: UUID): Device
    suspend fun updateLastSeen(id: UUID)
    suspend fun linkToUser(deviceId: UUID, userId: UUID)
    suspend fun setProUntil(deviceId: UUID, until: Instant?)
}
```

### ScanQuotaRepository

```kotlin
interface ScanQuotaRepository {
    suspend fun getOrCreate(deviceId: UUID, date: LocalDate): ScanQuota
    suspend fun incrementUsed(deviceId: UUID, date: LocalDate)
    suspend fun grantBonus(deviceId: UUID, date: LocalDate)
}
```

### ScanSessionRepository

```kotlin
interface ScanSessionRepository {
    suspend fun create(deviceId: UUID, dishes: List<DishDto>): UUID
    suspend fun findById(id: UUID): ScanSession?
    suspend fun markConsumed(id: UUID)
}
```

### DiaryRepository

```kotlin
interface DiaryRepository {
    suspend fun findEntriesByDevice(deviceId: UUID, date: LocalDate, tzOffsetMin: Int): List<DiaryEntry>
    suspend fun findEntriesByUser(userId: UUID, date: LocalDate, tzOffsetMin: Int): List<DiaryEntry>
    suspend fun insertEntry(entry: DiaryEntry, dishes: List<DishDto>): DiaryEntry
    suspend fun deleteEntry(id: UUID)
    suspend fun findEntry(id: UUID): DiaryEntry?
}
```

### UserRepository / OAuthRepository

```kotlin
interface UserRepository {
    suspend fun create(): UUID
    suspend fun findById(id: UUID): User?
    suspend fun setProUntil(userId: UUID, until: Instant?)
    suspend fun findDevices(userId: UUID): List<UUID>
}

interface OAuthRepository {
    suspend fun findByProvider(provider: OAuthProvider, providerUserId: String): UUID?
    suspend fun link(userId: UUID, provider: OAuthProvider, providerUserId: String)
    suspend fun listProviders(userId: UUID): List<OAuthProvider>
}
```

### PaymentRepository

```kotlin
interface PaymentRepository {
    suspend fun create(payment: Payment): UUID
    suspend fun markPaid(id: UUID, tochkaPaymentId: String, paidAt: Instant)
    suspend fun findByTochkaId(tochkaPaymentId: String): Payment?
}
```

### VisionBudgetRepository

```kotlin
interface VisionBudgetRepository {
    suspend fun getMonthCost(month: YearMonth): Int
    suspend fun addCost(month: YearMonth, rub: Int)
}
```

---

## 15. DTO (shared models)

**Пакет:** `domain/model/`

```kotlin
@Serializable
enum class MealType { breakfast, lunch, dinner, snack }

@Serializable
enum class OAuthProvider { vk, yandex }

@Serializable
data class DishDto(
    val name: String,
    val grams: Int,
    val kcal: Int,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val fiber: Double = 0.0,
)

@Serializable
data class MacroTotals(
    val kcal: Int,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val fiber: Double,
)

@Serializable
data class ApiError(
    val error: String,
    val message: String,
    val scansLeft: Int? = null,
)
```

---

## 16. Ktor routes — сигнатуры

```kotlin
// routes/ScanRoutes.kt
fun Route.scanRoutes(scanService: ScanService, identityResolver: IdentityResolver) {
    post("/scan") { /* multipart → analyzePhoto */ }
    post("/scan/bonus") { /* → grantAdBonus */ }
}

// routes/DiaryRoutes.kt
fun Route.diaryRoutes(diaryService: DiaryService, identityResolver: IdentityResolver) {
    get("/diary") { /* query date */ }
    post("/diary/entries") { /* JSON body */ }
    delete("/diary/entries/{id}") { /* */ }
}

// routes/SubscriptionRoutes.kt
fun Route.subscriptionRoutes(subscriptionService: SubscriptionService, identityResolver: IdentityResolver) {
    get("/subscription/status") { /* */ }
}

// routes/AuthRoutes.kt
fun Route.authRoutes(authService: AuthService, identityResolver: IdentityResolver) {
    post("/auth/vk") { /* */ }
    post("/auth/yandex") { /* */ }
    get("/auth/me") { /* JWT required */ }
}

// routes/PaymentRoutes.kt
fun Route.paymentRoutes(paymentService: PaymentService) {
    post("/payments/tochka/create") { /* */ }
    post("/payments/tochka/webhook") { /* raw body */ }
}
fun Route.payPage(paymentService: PaymentService) {
    get("/pay") { /* HTML */ }
}
```

---

## 17. DI wiring (Application.module)

```kotlin
fun Application.module() {
    val db = DatabaseFactory.init(AppConfig.databaseUrl)
    // repositories
    val deviceRepo = DeviceRepositoryImpl()
    val quotaRepo = ScanQuotaRepositoryImpl()
    // ...
    val quotaService = QuotaService(quotaRepo, deviceRepo, userRepo)
    val scanService = ScanService(quotaService, visionClient, scanSessionRepo, visionBudget)
    val diaryService = DiaryService(diaryRepo, quotaService, scanSessionRepo)
    // plugins + routing
}
```

---

## 18. Порядок реализации

| Sprint | Methods |
|--------|---------|
| **S1** | IdentityResolver, QuotaService, DeviceRepository, ScanQuotaRepository, GET /diary (empty), GET /subscription/status |
| **S2** | VisionClient, ScanService.analyzePhoto, POST /scan |
| **S3** | DiaryService add/delete, POST/DELETE diary |
| **S4** | QuotaService.grantAdBonus, POST /scan/bonus |
| **S5** | PaymentService + Tochka, GET /pay, webhook |
| **S6** | AuthService.linkVk, AccountMergeService, JWT plugin |

---

## 19. История

| Версия | Дата | Изменения |
|--------|------|-----------|
| 1.1 | 2026-06-14 | `POST /feedback/bug`, BugReportService |
| 1.0 | 2026-06-14 | Полный каталог методов v1 |
