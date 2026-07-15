package ru.kkalscan.domain.port

import ru.kkalscan.domain.model.Actor
import ru.kkalscan.domain.model.DishDto
import ru.kkalscan.domain.model.MealType
import ru.kkalscan.domain.model.WorkoutParseResult
import ru.kkalscan.domain.model.OAuthProvider
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

interface IdentityResolver {
    suspend fun resolve(deviceId: UUID, bearerToken: String?): Actor
}

interface ScanService {
    suspend fun analyzePhoto(
        actor: Actor,
        photoBytes: ByteArray,
        localDate: LocalDate,
        timezoneOffsetMinutes: Int,
    ): ScanResult

    suspend fun analyzeDescription(
        actor: Actor,
        description: String,
        localDate: LocalDate,
        timezoneOffsetMinutes: Int,
    ): ScanResult

    data class ScanResult(
        val scanId: UUID,
        val dishes: List<DishDto>,
        val totals: ru.kkalscan.domain.model.MacroTotals,
        val scansLeft: Int?,
        val isPro: Boolean,
    )
}

interface QuotaService {
    suspend fun canStartScan(actor: Actor, localDate: LocalDate): Boolean

    suspend fun consumeScan(
        actor: Actor,
        localDate: LocalDate,
        scanSessionId: UUID?,
    ): Int?

    suspend fun grantAdBonus(actor: Actor, localDate: LocalDate): BonusResult

    suspend fun getScansLeft(actor: Actor, localDate: LocalDate): Int?

    data class BonusResult(
        val scansLeft: Int,
        val bonusGranted: Boolean,
    )
}

interface DiaryService {
    suspend fun getDay(
        actor: Actor,
        date: LocalDate,
        timezoneOffsetMinutes: Int,
    ): DiaryDayResponse

    suspend fun addEntry(
        actor: Actor,
        request: CreateDiaryEntryRequest,
        localDate: LocalDate,
    ): CreateDiaryEntryResponse

    suspend fun deleteEntry(actor: Actor, entryId: UUID)

    suspend fun addWorkout(
        actor: Actor,
        request: CreateWorkoutRequest,
        localDate: LocalDate,
    ): CreateWorkoutResponse

    suspend fun deleteWorkout(actor: Actor, workoutId: UUID)

    suspend fun parseWorkoutDescription(actor: Actor, description: String): WorkoutParseResult

    suspend fun syncActivity(
        actor: Actor,
        request: SyncActivityRequest,
        localDate: LocalDate,
        timezoneOffsetMinutes: Int,
    ): DiaryDayResponse

    data class CreateDiaryEntryRequest(
        val mealType: MealType,
        val scanId: UUID? = null,
        val dishes: List<DishDto>? = null,
    )

    data class CreateWorkoutRequest(
        val name: String,
        val kcal: Int,
    )

    data class SyncActivityRequest(
        val steps: Int,
        val kcal: Int,
        val source: ActivitySourceKind,
    )
}

interface SubscriptionService {
    suspend fun getStatus(actor: Actor): SubscriptionStatus

    suspend fun activatePro(deviceId: UUID, tariff: String, paidAt: Instant)

    data class SubscriptionStatus(
        val isPro: Boolean,
        val proUntil: Instant?,
        val accountLinked: Boolean,
        val linkedProviders: List<OAuthProvider>,
        val tariff: String?,
    )
}

interface AuthService {
    suspend fun linkVk(deviceId: UUID, vkAccessToken: String): AuthTokenResponse

    suspend fun linkYandex(deviceId: UUID, yandexToken: String): AuthTokenResponse

    suspend fun getMe(userId: UUID): MeResponse

    fun issueJwt(userId: UUID, deviceId: UUID?): String
}

interface AccountMergeService {
    suspend fun mergeDeviceToUser(deviceId: UUID, userId: UUID)
}

enum class ActivityEmulatorMode {
    population_default,
    diary_based,
}

data class ActivityEmulatorResponse(
    val mode: ActivityEmulatorMode,
    val estimatedActiveKcal: Int,
    val estimatedSteps: Int,
    val avgConsumedKcalPerDay: Int?,
    val diaryDaysWithEntries: Int,
    val lookbackDays: Int,
)

interface ActivityEmulatorService {
    suspend fun getEmulator(
        deviceId: UUID,
        today: LocalDate,
        timezoneOffsetMinutes: Int,
    ): ActivityEmulatorResponse
}

interface PaymentService {
    suspend fun createTochkaPayment(deviceId: UUID, tariff: String = "pro_monthly_199"): PaymentCreateResponse

    suspend fun startProSubscription(deviceId: UUID, tariff: String = "pro_monthly_199"): ProSubscriptionStartResult

    suspend fun handleTochkaWebhook(rawBody: String, signature: String?)

    suspend fun activateTestPayment(deviceId: UUID, secret: String): TestPaymentResult

    suspend fun renderPayPage(deviceId: UUID): String

    suspend fun renderPaySuccessPage(deviceId: UUID): String

    suspend fun renderPayFailPage(): String

    data class ProSubscriptionStartResult(
        val isPro: Boolean,
        val proUntil: Instant?,
        val tariff: String,
        val paymentRequired: Boolean,
        val paymentUrl: String? = null,
        val paymentId: UUID? = null,
        val message: String? = null,
    )

    data class TestPaymentResult(
        val isPro: Boolean,
        val proUntil: Instant,
        val tariff: String,
        val emailSent: Boolean,
    )
}

interface BugReportService {
    suspend fun submitBugReport(
        actor: Actor,
        email: String,
        description: String,
        screenshots: List<ByteArray>,
    ): BugReportResult

    data class BugReportResult(
        val reportId: UUID,
        val isPro: Boolean,
        val proUntil: Instant?,
        val message: String,
    )
}

interface BugReportRepository {
    suspend fun hasReportForDevice(deviceId: UUID): Boolean

    suspend fun create(
        deviceId: UUID,
        userId: UUID?,
        email: String,
        description: String,
        screenshots: List<ByteArray>,
    ): UUID

    suspend fun deleteReport(reportId: UUID)
}

data class BugReportRecord(
    val id: UUID,
    val deviceId: UUID,
    val userId: UUID?,
    val email: String,
    val description: String,
    val screenshotCount: Int,
    val createdAt: Instant,
)

data class BugReportScreenshotRecord(
    val id: UUID,
    val reportId: UUID,
    val position: Int,
    val contentType: String,
    val sizeBytes: Long,
    val createdAt: Instant,
)

// Response stubs — serialized in routes layer
data class DiaryDayResponse(
    val date: LocalDate,
    val totalKcal: Int,
    val totalBurnedKcal: Int,
    val netKcal: Int,
    val activityKcal: Int = 0,
    val activitySteps: Int? = null,
    val activitySource: ActivitySourceKind = ActivitySourceKind.None,
    val scansLeft: Int?,
    val isPro: Boolean,
    val accountLinked: Boolean,
    val linkedProviders: List<OAuthProvider>,
    val entries: List<DiaryEntryDto>,
    val workouts: List<WorkoutEntryDto>,
)

data class DiaryEntryDto(
    val id: UUID,
    val createdAt: Instant,
    val mealType: MealType,
    val totalKcal: Int,
    val dishes: List<DishDto>,
)

data class CreateDiaryEntryResponse(
    val entry: DiaryEntryDto,
    val scansLeft: Int?,
)

data class WorkoutEntryDto(
    val id: UUID,
    val createdAt: Instant,
    val name: String,
    val kcal: Int,
)

data class CreateWorkoutResponse(
    val workout: WorkoutEntryDto,
)

data class AuthTokenResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val userId: UUID,
    val isPro: Boolean,
    val accountLinked: Boolean,
    val linkedProviders: List<OAuthProvider>,
)

data class MeResponse(
    val userId: UUID,
    val isPro: Boolean,
    val proUntil: Instant?,
    val linkedProviders: List<OAuthProvider>,
    val devices: List<UUID>,
)

data class PaymentCreateResponse(
    val paymentUrl: String,
    val paymentId: UUID,
)

interface DeviceRepository {
    suspend fun findById(id: UUID): DeviceRecord?

    suspend fun getOrCreate(id: UUID): DeviceRecord

    suspend fun updateLastSeen(id: UUID)

    suspend fun linkToUser(deviceId: UUID, userId: UUID)

    suspend fun setProUntil(deviceId: UUID, until: Instant?)

    suspend fun findByUserId(userId: UUID): List<DeviceRecord>
}

interface ScanQuotaRepository {
    suspend fun getOrCreate(deviceId: UUID, date: LocalDate): ScanQuotaRecord

    suspend fun incrementUsed(deviceId: UUID, date: LocalDate)

    suspend fun grantBonus(deviceId: UUID, date: LocalDate)
}

interface ScanSessionRepository {
    suspend fun create(deviceId: UUID, dishes: List<DishDto>): UUID

    suspend fun findById(id: UUID): ScanSessionRecord?

    suspend fun markConsumed(id: UUID)
}

interface DiaryRepository {
    suspend fun findEntriesByDevice(deviceId: UUID, date: LocalDate, tzOffsetMin: Int): List<DiaryEntryRecord>

    suspend fun findEntriesByUser(userId: UUID, date: LocalDate, tzOffsetMin: Int): List<DiaryEntryRecord>

    suspend fun consumedKcalByDay(
        deviceId: UUID,
        from: LocalDate,
        to: LocalDate,
        tzOffsetMin: Int,
    ): Map<LocalDate, Int>

    suspend fun insertEntry(entry: DiaryEntryRecord, dishes: List<DishDto>): DiaryEntryRecord

    suspend fun deleteEntry(id: UUID)

    suspend fun findEntry(id: UUID): DiaryEntryRecord?
}

interface WorkoutRepository {
    suspend fun findByDevice(deviceId: UUID, date: LocalDate, tzOffsetMin: Int): List<WorkoutRecord>

    suspend fun findByUser(userId: UUID, date: LocalDate, tzOffsetMin: Int): List<WorkoutRecord>

    suspend fun insert(workout: WorkoutRecord): WorkoutRecord

    suspend fun delete(id: UUID)

    suspend fun findById(id: UUID): WorkoutRecord?

    suspend fun updateUserIdForDevice(deviceId: UUID, userId: UUID)
}

enum class ActivitySourceKind {
    DeviceSensor,
    Emulator,
    None,
}

data class DailyActivityRecord(
    val deviceId: UUID,
    val userId: UUID?,
    val localDate: LocalDate,
    val steps: Int,
    val kcal: Int,
    val source: ActivitySourceKind,
    val updatedAt: Instant,
)

interface DailyActivityRepository {
    suspend fun findByDevice(deviceId: UUID, date: LocalDate): DailyActivityRecord?

    suspend fun findByUser(userId: UUID, date: LocalDate): DailyActivityRecord?

    suspend fun upsert(record: DailyActivityRecord): DailyActivityRecord

    suspend fun updateUserIdForDevice(deviceId: UUID, userId: UUID)
}

interface UserRepository {
    suspend fun create(): UUID

    suspend fun findById(id: UUID): UserRecord?

    suspend fun setProUntil(userId: UUID, until: Instant?)

    suspend fun findDevices(userId: UUID): List<UUID>
}

interface OAuthRepository {
    suspend fun findByProvider(provider: OAuthProvider, providerUserId: String): UUID?

    suspend fun link(userId: UUID, provider: OAuthProvider, providerUserId: String)

    suspend fun listProviders(userId: UUID): List<OAuthProvider>
}

interface PaymentRepository {
    suspend fun create(payment: PaymentRecord): UUID

    suspend fun markPaid(id: UUID, tochkaPaymentId: String, paidAt: Instant)

    suspend fun findById(id: UUID): PaymentRecord?

    suspend fun findByTochkaId(tochkaPaymentId: String): PaymentRecord?
}

interface VisionBudgetRepository {
    suspend fun getMonthCost(month: YearMonth): Int

    suspend fun addCost(month: YearMonth, rub: Int)
}

interface VisionClient {
    suspend fun analyzeFood(imageBytes: ByteArray): List<DishDto>

    suspend fun analyzeDescription(description: String): List<DishDto>

    suspend fun analyzeWorkout(description: String): WorkoutParseResult
}

interface VkAuthClient {
    suspend fun verifyToken(accessToken: String): VkUser

    data class VkUser(val id: Long)
}

interface TochkaClient {
    suspend fun createPayment(
        amountKopecks: Int,
        description: String,
        metadata: Map<String, String>,
    ): TochkaPayment

    fun parseWebhook(rawBody: String, signature: String?): TochkaWebhookEvent?

    data class TochkaPayment(val id: String, val paymentUrl: String)

    data class TochkaWebhookEvent(
        val operationId: String?,
        val paymentLinkId: String?,
        val status: String,
        val webhookType: String? = null,
    )
}

// Internal records (not API DTOs)
data class DeviceRecord(
    val id: UUID,
    val userId: UUID?,
    val proUntil: Instant?,
)

data class ScanQuotaRecord(
    val deviceId: UUID,
    val quotaDate: LocalDate,
    val scansUsed: Int,
    val bonusGranted: Boolean,
    val bonusScans: Int,
)

data class ScanSessionRecord(
    val id: UUID,
    val deviceId: UUID,
    val dishes: List<DishDto>,
    val consumed: Boolean,
)

data class DiaryEntryRecord(
    val id: UUID,
    val deviceId: UUID,
    val userId: UUID?,
    val mealType: MealType,
    val scanSessionId: UUID?,
    val totalKcal: Int,
    val createdAt: Instant,
    val dishes: List<DishDto>,
)

data class WorkoutRecord(
    val id: UUID,
    val deviceId: UUID,
    val userId: UUID?,
    val name: String,
    val kcal: Int,
    val createdAt: Instant,
)

data class UserRecord(
    val id: UUID,
    val proUntil: Instant?,
)

data class PaymentRecord(
    val id: UUID,
    val deviceId: UUID,
    val userId: UUID?,
    val tochkaPaymentId: String?,
    val amountKopecks: Int,
    val tariff: String,
    val status: String,
    val paidAt: Instant?,
    val promoCode: String? = null,
    val discountPercent: Int = 0,
    val listAmountKopecks: Int = 0,
)
