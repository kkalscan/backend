package ru.kkalscan.domain.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.slf4j.LoggerFactory
import ru.kkalscan.AppConfig
import ru.kkalscan.domain.BadRequestException
import ru.kkalscan.domain.ForbiddenException
import ru.kkalscan.domain.VisionBudgetExceededException
import ru.kkalscan.domain.VisionUnavailableException
import ru.kkalscan.domain.model.Actor
import ru.kkalscan.domain.port.DiaryRepository
import ru.kkalscan.domain.port.DietitianInsightResponse
import ru.kkalscan.domain.port.InsightService
import ru.kkalscan.domain.port.VisionBudgetRepository
import ru.kkalscan.domain.port.VisionClient
import ru.kkalscan.domain.port.WorkoutRepository
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

class InsightServiceImpl(
    private val diaryRepository: DiaryRepository,
    private val workoutRepository: WorkoutRepository,
    private val visionClient: VisionClient,
    private val visionBudgetRepository: VisionBudgetRepository,
) : InsightService {

    private val log = LoggerFactory.getLogger(InsightServiceImpl::class.java)
    private val json = Json { prettyPrint = false }

    override suspend fun dietitianInsight(
        actor: Actor,
        weekStart: LocalDate,
        timezoneOffsetMinutes: Int,
    ): DietitianInsightResponse {
        if (!actor.isPro) {
            throw ForbiddenException("Доступно в Pro")
        }

        val days = (0..6).map { weekStart.plusDays(it.toLong()) }
        val dayPayloads = days.map { date ->
            val entries = diaryRepository.findEntriesByDevice(actor.deviceId, date, timezoneOffsetMinutes)
            val workouts = workoutRepository.findByDevice(actor.deviceId, date, timezoneOffsetMinutes)
            val kcal = entries.sumOf { it.totalKcal }
            val protein = entries.sumOf { e -> e.dishes.sumOf { it.protein } }
            val fat = entries.sumOf { e -> e.dishes.sumOf { it.fat } }
            val carbs = entries.sumOf { e -> e.dishes.sumOf { it.carbs } }
            val burned = workouts.sumOf { it.kcal }
            val dishNames = entries.flatMap { it.dishes.map { d -> d.name } }.distinct().take(5)
            DayAgg(
                date = date,
                kcal = kcal,
                protein = protein,
                fat = fat,
                carbs = carbs,
                burned = burned,
                dishNames = dishNames,
                hasData = entries.isNotEmpty() || workouts.isNotEmpty(),
            )
        }

        val daysWithData = dayPayloads.count { it.hasData }
        if (daysWithData < MIN_DAYS_WITH_DATA) {
            throw BadRequestException("Нужно минимум $MIN_DAYS_WITH_DATA дня с записями")
        }

        val month = YearMonth.now()
        if (visionBudgetRepository.getMonthCost(month) >= AppConfig.visionMonthlyBudgetRub) {
            throw VisionBudgetExceededException()
        }

        val weekJson = buildWeekJson(weekStart, dayPayloads, daysWithData)
        val insight = try {
            visionClient.analyzeDietitianWeek(weekJson)
        } catch (e: VisionUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw VisionUnavailableException(cause = e)
        }

        visionBudgetRepository.addCost(month, AppConfig.visionCostPerRequestRub)

        log.info(
            "dietitian insight device={} weekStart={} daysWithData={}",
            actor.deviceId.toString().take(8) + "…",
            weekStart,
            daysWithData,
        )

        return DietitianInsightResponse(
            weekStart = weekStart.toString(),
            generatedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            headline = insight.headline,
            sections = insight.sections,
        )
    }

    private fun buildWeekJson(weekStart: LocalDate, days: List<DayAgg>, daysWithData: Int): String {
        val avgKcal = days.filter { it.hasData }.map { it.kcal }.average().let {
            if (it.isNaN()) 0 else it.toInt()
        }
        val obj = buildJsonObject {
            put("week_start", weekStart.toString())
            put("days_with_data", daysWithData)
            put("avg_kcal", avgKcal)
            put(
                "days",
                buildJsonArray {
                    for (d in days) {
                        add(
                            buildJsonObject {
                                put("date", d.date.toString())
                                put("kcal", d.kcal)
                                put("protein", d.protein)
                                put("fat", d.fat)
                                put("carbs", d.carbs)
                                put("burned_kcal", d.burned)
                                put(
                                    "dishes",
                                    buildJsonArray {
                                        d.dishNames.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
        return json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), obj)
    }

    private data class DayAgg(
        val date: LocalDate,
        val kcal: Int,
        val protein: Double,
        val fat: Double,
        val carbs: Double,
        val burned: Int,
        val dishNames: List<String>,
        val hasData: Boolean,
    )

    private companion object {
        const val MIN_DAYS_WITH_DATA = 3
    }
}
