package ru.kkalscan.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class MealType {
    breakfast,
    lunch,
    dinner,
    snack,
}

@Serializable
enum class OAuthProvider {
    vk,
    yandex,
}

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
) {
    companion object {
        fun from(dishes: List<DishDto>): MacroTotals =
            MacroTotals(
                kcal = dishes.sumOf { it.kcal },
                protein = dishes.sumOf { it.protein },
                fat = dishes.sumOf { it.fat },
                carbs = dishes.sumOf { it.carbs },
                fiber = dishes.sumOf { it.fiber },
            )
    }
}

@Serializable
data class ApiError(
    val error: String,
    val message: String,
    val scansLeft: Int? = null,
)

data class Actor(
    val deviceId: UUID,
    val userId: UUID? = null,
    val isPro: Boolean,
    val accountLinked: Boolean,
    val linkedProviders: List<OAuthProvider>,
)
