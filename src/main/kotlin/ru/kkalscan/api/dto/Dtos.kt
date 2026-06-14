package ru.kkalscan.api.dto

import kotlinx.serialization.Serializable
import ru.kkalscan.domain.model.DishDto
import ru.kkalscan.domain.model.MealType
import ru.kkalscan.domain.port.CreateDiaryEntryResponse
import ru.kkalscan.domain.port.DiaryDayResponse
import ru.kkalscan.domain.port.DiaryEntryDto
import ru.kkalscan.domain.port.ScanService
import ru.kkalscan.domain.port.SubscriptionService
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
data class DiaryEntryRequest(
    val device_id: String,
    val meal_type: MealType,
    val scan_id: String? = null,
    val dishes: List<DishDto>? = null,
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
    val scans_left: Int? = null,
    val is_pro: Boolean,
    val account_linked: Boolean,
    val linked_providers: List<String>,
    val entries: List<DiaryEntryJson>,
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
data class PaymentCreateRequest(val device_id: String, val tariff: String = "pro_monthly_199")

@Serializable
data class PaymentCreateJson(val payment_url: String, val payment_id: String)

@Serializable
data class WebhookAck(val ok: Boolean = true)

private val instantFormatter = DateTimeFormatter.ISO_INSTANT

fun ScanService.ScanResult.toResponse() = ScanResponse(
    scan_id = scanId.toString(),
    dishes = dishes,
    total_kcal = totals.kcal,
    total_protein = totals.protein,
    total_fat = totals.fat,
    total_carbs = totals.carbs,
    scans_left = scansLeft,
    is_pro = isPro,
)

fun DiaryDayResponse.toJson() = DiaryDayJson(
    date = date.toString(),
    total_kcal = totalKcal,
    scans_left = scansLeft,
    is_pro = isPro,
    account_linked = accountLinked,
    linked_providers = linkedProviders.map { it.name },
    entries = entries.map { it.toJson() },
)

fun DiaryEntryDto.toJson() = DiaryEntryJson(
    id = id.toString(),
    created_at = instantFormatter.format(createdAt),
    meal_type = mealType,
    total_kcal = totalKcal,
    dishes = dishes,
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
