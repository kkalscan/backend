package ru.kkalscan.api.dto

import kotlinx.serialization.Serializable
import ru.kkalscan.domain.model.DishDto
import ru.kkalscan.domain.model.MealType
import ru.kkalscan.domain.port.ActivityEmulatorResponse
import ru.kkalscan.domain.port.CreateDiaryEntryResponse
import ru.kkalscan.domain.port.ActivitySourceKind
import ru.kkalscan.domain.port.DiaryDayResponse
import ru.kkalscan.domain.port.DiaryEntryDto
import ru.kkalscan.domain.port.ScanService
import ru.kkalscan.domain.port.SubscriptionService
import ru.kkalscan.domain.port.WorkoutEntryDto
import java.time.format.DateTimeFormatter

@Serializable
data class ApiErrorResponse(
    val error: String,
    val message: String,
    val scans_left: Int? = null,
)

@Serializable
data class HealthResponse(
    val status: String,
    val version: String,
    val vision: VisionHealthInfo,
)

@Serializable
data class VisionHealthInfo(
    val provider: String,
    val api_key_configured: Boolean,
    val model: String? = null,
)

@Serializable
data class ScanResponse(
    val scan_id: String,
    val dishes: List<DishDto>,
    val total_kcal: Int,
    val total_protein: Double,
    val total_fat: Double,
    val total_carbs: Double,
    val total_fiber: Double,
    val scans_left: Int? = null,
    val is_pro: Boolean,
    val disclaimer: String = "Оценка приблизительная, не медицинский совет",
)

@Serializable
data class BonusResponse(
    val scans_left: Int,
    val bonus_granted: Boolean,
)

@Serializable
data class DeviceIdBody(val device_id: String)

@Serializable
data class ScanBonusRequest(val device_id: String)

@Serializable
data class ScanTextRequest(
    val device_id: String,
    val description: String,
    val timezone_offset_minutes: Int = 180,
)

@Serializable
data class DiaryEntryRequest(
    val device_id: String,
    val meal_type: MealType,
    val scan_id: String? = null,
    val dishes: List<DishDto>? = null,
)

@Serializable
data class ActivityEmulatorJson(
    val mode: String,
    val estimated_active_kcal: Int,
    val estimated_steps: Int,
    val avg_consumed_kcal_per_day: Int? = null,
    val diary_days_with_entries: Int,
    val lookback_days: Int,
)

@Serializable
data class DiaryEntryResponse(
    val entry: DiaryEntryJson,
    val scans_left: Int? = null,
)

@Serializable
data class DiaryDayJson(
    val date: String,
    val total_kcal: Int,
    val total_burned_kcal: Int = 0,
    val net_kcal: Int = total_kcal,
    val activity_kcal: Int = 0,
    val activity_steps: Int? = null,
    val activity_source: String = "none",
    val scans_left: Int? = null,
    val is_pro: Boolean,
    val account_linked: Boolean,
    val linked_providers: List<String>,
    val entries: List<DiaryEntryJson>,
    val workouts: List<WorkoutEntryJson> = emptyList(),
)

@Serializable
data class DiaryEntryJson(
    val id: String,
    val created_at: String,
    val meal_type: MealType,
    val total_kcal: Int,
    val dishes: List<DishDto>,
)

@Serializable
data class WorkoutEntryJson(
    val id: String,
    val created_at: String,
    val name: String,
    val kcal: Int,
)

@Serializable
data class WorkoutRequest(
    val device_id: String,
    val name: String,
    val kcal: Int,
)

@Serializable
data class WorkoutTextRequest(
    val device_id: String,
    val description: String,
)

@Serializable
data class WorkoutParseResponse(
    val title: String,
    val burned_kcal: Int,
    val duration_minutes: Int? = null,
)

@Serializable
data class WorkoutResponse(
    val workout: WorkoutEntryJson,
)

@Serializable
data class ActivitySyncRequest(
    val device_id: String,
    val steps: Int,
    val kcal: Int,
    val source: String = "none",
    val timezone_offset_minutes: Int = 180,
)

@Serializable
data class SubscriptionStatusResponse(
    val is_pro: Boolean,
    val pro_until: String? = null,
    val account_linked: Boolean,
    val linked_providers: List<String>,
    val tariff: String? = null,
)

@Serializable
data class VkAuthRequest(val device_id: String, val access_token: String)

@Serializable
data class AuthTokenJson(
    val access_token: String,
    val token_type: String,
    val expires_in: Long,
    val user_id: String,
    val is_pro: Boolean,
    val account_linked: Boolean,
    val linked_providers: List<String>,
)

@Serializable
data class TestPaymentActivateRequest(
    val device_id: String,
    val secret: String,
)

@Serializable
data class TestPaymentActivateResponse(
    val is_pro: Boolean,
    val pro_until: String,
    val tariff: String,
    val email_sent: Boolean,
    val message: String,
)

@Serializable
data class PaymentCreateRequest(val device_id: String, val tariff: String = "pro_monthly_199")

@Serializable
data class PaymentCreateJson(val payment_url: String, val payment_id: String)

@Serializable
data class ProSubscriptionStartRequest(val device_id: String, val tariff: String = "pro_monthly_199")

@Serializable
data class ProSubscriptionStartResponse(
    val is_pro: Boolean,
    val pro_until: String? = null,
    val tariff: String,
    val payment_required: Boolean,
    val payment_url: String? = null,
    val payment_id: String? = null,
    val message: String? = null,
)

@Serializable
data class SubscriptionOfferJson(
    val tariff: String,
    val title: String,
    val price_rub: Int,
    val amount_rub: Int,
    val amount_kopecks: Int,
    val discount_percent: Int = 0,
    val promo_code: String? = null,
)

@Serializable
data class SubscriptionOffersResponse(
    val offers: List<SubscriptionOfferJson>,
)

@Serializable
data class PromoApplyRequest(
    val device_id: String,
    val promo_code: String,
)

@Serializable
data class PromoApplyResponse(
    val promo_code: String,
    val discount_percent: Int,
)

@Serializable
data class WebhookAck(val ok: Boolean = true)

@Serializable
data class FeatureSearchItemJson(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val deeplink: String,
    val icon: String,
)

@Serializable
data class FeatureSearchResponse(
    val query: String,
    val items: List<FeatureSearchItemJson>,
    val total: Int,
    @kotlinx.serialization.SerialName("popular_fallback")
    val popularFallback: Boolean = false,
)

@Serializable
data class FeatureSearchIntentRequest(
    val query: String,
)

@Serializable
data class FeatureSearchIntentResponse(
    val query: String,
    @kotlinx.serialization.SerialName("is_food_intent")
    val isFoodIntent: Boolean,
)

@Serializable
data class FoodSearchResponse(
    val query: String,
    val items: List<DishDto>,
    val total: Int,
)

@Serializable
data class SearchQueryStatJson(
    val query: String,
    val count: Int,
)

@Serializable
data class SearchTopResponse(
    val days: Int,
    val queries: List<SearchQueryStatJson>,
)

@Serializable
data class BugReportResponse(
    val report_id: String,
    val is_pro: Boolean,
    val pro_until: String? = null,
    val message: String,
)

private val instantFormatter = DateTimeFormatter.ISO_INSTANT

fun ScanService.ScanResult.toResponse() = ScanResponse(
    scan_id = scanId.toString(),
    dishes = dishes,
    total_kcal = totals.kcal,
    total_protein = totals.protein,
    total_fat = totals.fat,
    total_carbs = totals.carbs,
    total_fiber = totals.fiber,
    scans_left = scansLeft,
    is_pro = isPro,
)

fun DiaryDayResponse.toJson() = DiaryDayJson(
    date = date.toString(),
    total_kcal = totalKcal,
    total_burned_kcal = totalBurnedKcal,
    net_kcal = netKcal,
    activity_kcal = activityKcal,
    activity_steps = activitySteps,
    activity_source = activitySource.toWire(),
    scans_left = scansLeft,
    is_pro = isPro,
    account_linked = accountLinked,
    linked_providers = linkedProviders.map { it.name },
    entries = entries.map { it.toJson() },
    workouts = workouts.map { it.toJson() },
)

private fun ActivitySourceKind.toWire(): String =
    when (this) {
        ActivitySourceKind.DeviceSensor -> "device_sensor"
        ActivitySourceKind.Emulator -> "emulator"
        ActivitySourceKind.None -> "none"
    }

fun parseActivitySource(raw: String): ActivitySourceKind =
    when (raw.trim().lowercase()) {
        "device_sensor" -> ActivitySourceKind.DeviceSensor
        "emulator" -> ActivitySourceKind.Emulator
        else -> ActivitySourceKind.None
    }

fun DiaryEntryDto.toJson() = DiaryEntryJson(
    id = id.toString(),
    created_at = instantFormatter.format(createdAt),
    meal_type = mealType,
    total_kcal = totalKcal,
    dishes = dishes,
)

fun WorkoutEntryDto.toJson() = WorkoutEntryJson(
    id = id.toString(),
    created_at = instantFormatter.format(createdAt),
    name = name,
    kcal = kcal,
)

fun CreateDiaryEntryResponse.toJson() = DiaryEntryResponse(
    entry = entry.toJson(),
    scans_left = scansLeft,
)

fun SubscriptionService.SubscriptionStatus.toJson() = SubscriptionStatusResponse(
    is_pro = isPro,
    pro_until = proUntil?.let { instantFormatter.format(it) },
    account_linked = accountLinked,
    linked_providers = linkedProviders.map { it.name },
    tariff = tariff,
)

fun ActivityEmulatorResponse.toJson() = ActivityEmulatorJson(
    mode = mode.name,
    estimated_active_kcal = estimatedActiveKcal,
    estimated_steps = estimatedSteps,
    avg_consumed_kcal_per_day = avgConsumedKcalPerDay,
    diary_days_with_entries = diaryDaysWithEntries,
    lookback_days = lookbackDays,
)
