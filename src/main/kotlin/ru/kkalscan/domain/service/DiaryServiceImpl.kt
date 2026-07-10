package ru.kkalscan.domain.service

import org.slf4j.LoggerFactory
import ru.kkalscan.domain.BadRequestException
import ru.kkalscan.domain.ForbiddenException
import ru.kkalscan.domain.NotFoundException
import ru.kkalscan.domain.model.Actor
import ru.kkalscan.domain.model.DishDto
import ru.kkalscan.domain.model.WorkoutParseResult
import ru.kkalscan.domain.port.CreateDiaryEntryResponse
import ru.kkalscan.domain.port.CreateWorkoutResponse
import ru.kkalscan.domain.port.DailyActivityRecord
import ru.kkalscan.domain.port.DailyActivityRepository
import ru.kkalscan.domain.port.DiaryDayResponse
import ru.kkalscan.domain.port.DiaryEntryDto
import ru.kkalscan.domain.port.ActivitySourceKind
import ru.kkalscan.domain.port.DiaryEntryRecord
import ru.kkalscan.domain.port.DiaryRepository
import ru.kkalscan.domain.port.DiaryService
import ru.kkalscan.domain.port.QuotaService
import ru.kkalscan.domain.port.ScanSessionRepository
import ru.kkalscan.domain.port.VisionBudgetRepository
import ru.kkalscan.domain.port.VisionClient
import ru.kkalscan.domain.port.WorkoutEntryDto
import ru.kkalscan.domain.port.WorkoutRecord
import ru.kkalscan.domain.port.WorkoutRepository
import ru.kkalscan.AppConfig
import ru.kkalscan.domain.VisionBudgetExceededException
import ru.kkalscan.domain.VisionUnavailableException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class DiaryServiceImpl(
    private val diaryRepository: DiaryRepository,
    private val workoutRepository: WorkoutRepository,
    private val dailyActivityRepository: DailyActivityRepository,
    private val quotaService: QuotaService,
    private val scanSessionRepository: ScanSessionRepository,
    private val visionClient: VisionClient,
    private val visionBudgetRepository: VisionBudgetRepository,
) : DiaryService {

    private val log = LoggerFactory.getLogger(DiaryServiceImpl::class.java)

    override suspend fun getDay(
        actor: Actor,
        date: LocalDate,
        timezoneOffsetMinutes: Int,
    ): DiaryDayResponse {
        val entries = loadEntries(actor, date, timezoneOffsetMinutes)
        val workouts = loadWorkouts(actor, date, timezoneOffsetMinutes)
        val activity = loadActivity(actor, date)
        val consumed = entries.sumOf { it.totalKcal }
        val workoutBurned = workouts.sumOf { it.kcal }
        val activityBurned = activity?.kcal ?: 0
        val burned = workoutBurned + activityBurned
        log.debug(
            "diary get device={} date={} entries={} workouts={} activity={} kcal={} burned={}",
            mask(actor.deviceId),
            date,
            entries.size,
            workouts.size,
            activityBurned,
            consumed,
            burned,
        )
        return DiaryDayResponse(
            date = date,
            totalKcal = consumed,
            totalBurnedKcal = burned,
            netKcal = consumed - burned,
            activityKcal = activityBurned,
            activitySteps = activity?.steps?.takeIf { it > 0 },
            activitySource = activity?.source ?: ActivitySourceKind.None,
            scansLeft = quotaService.getScansLeft(actor, date),
            isPro = actor.isPro,
            accountLinked = actor.accountLinked,
            linkedProviders = actor.linkedProviders,
            entries = entries.map { it.toDto() },
            workouts = workouts.map { it.toDto() },
        )
    }

    override suspend fun addEntry(
        actor: Actor,
        request: DiaryService.CreateDiaryEntryRequest,
        localDate: LocalDate,
    ): CreateDiaryEntryResponse {
        val dishes = resolveDishes(actor, request)
        val scanSessionId = request.scanId

        if (scanSessionId != null) {
            val session = scanSessionRepository.findById(scanSessionId)
                ?: throw BadRequestException("scan_id не найден")
            if (session.deviceId != actor.deviceId) throw ForbiddenException()
            if (!session.consumed) {
                quotaService.consumeScan(actor, localDate, scanSessionId)
                scanSessionRepository.markConsumed(scanSessionId)
            }
        } else {
            quotaService.consumeScan(actor, localDate, null)
        }

        val entry = DiaryEntryRecord(
            id = UUID.randomUUID(),
            deviceId = actor.deviceId,
            userId = actor.userId,
            mealType = request.mealType,
            scanSessionId = scanSessionId,
            totalKcal = dishes.sumOf { it.kcal },
            createdAt = Instant.now(),
            dishes = dishes,
        )

        val saved = diaryRepository.insertEntry(entry, dishes)
        log.info(
            "diary add device={} entryId={} meal={} kcal={} scanId={} left={}",
            mask(actor.deviceId),
            saved.id.toString().take(8),
            request.mealType,
            saved.totalKcal,
            scanSessionId?.toString()?.take(8),
            quotaService.getScansLeft(actor, localDate),
        )
        return CreateDiaryEntryResponse(
            entry = saved.toDto(),
            scansLeft = quotaService.getScansLeft(actor, localDate),
        )
    }

    override suspend fun deleteEntry(actor: Actor, entryId: UUID) {
        val entry = diaryRepository.findEntry(entryId) ?: throw NotFoundException("Запись не найдена")
        if (!ownsEntry(actor, entry)) throw ForbiddenException()
        diaryRepository.deleteEntry(entryId)
    }

    override suspend fun addWorkout(
        actor: Actor,
        request: DiaryService.CreateWorkoutRequest,
        localDate: LocalDate,
    ): CreateWorkoutResponse {
        val name = request.name.trim()
        if (name.length < 2) throw BadRequestException("Название тренировки — минимум 2 символа")
        if (request.kcal !in 1..10_000) throw BadRequestException("Укажите калории от 1 до 10000")

        val saved = workoutRepository.insert(
            WorkoutRecord(
                id = UUID.randomUUID(),
                deviceId = actor.deviceId,
                userId = actor.userId,
                name = name,
                kcal = request.kcal,
                createdAt = Instant.now(),
            ),
        )
        log.info(
            "workout add device={} workoutId={} name={} kcal={}",
            mask(actor.deviceId),
            saved.id.toString().take(8),
            name,
            saved.kcal,
        )
        return CreateWorkoutResponse(workout = saved.toDto())
    }

    override suspend fun parseWorkoutDescription(actor: Actor, description: String): WorkoutParseResult {
        val trimmed = description.trim()
        if (trimmed.length < MIN_WORKOUT_DESCRIPTION_CHARS) {
            throw BadRequestException("Опишите тренировку — минимум $MIN_WORKOUT_DESCRIPTION_CHARS символа")
        }
        if (trimmed.length > MAX_WORKOUT_DESCRIPTION_CHARS) {
            throw BadRequestException("Описание слишком длинное — до $MAX_WORKOUT_DESCRIPTION_CHARS символов")
        }

        log.info(
            "workout parse start device={} chars={} provider={}",
            mask(actor.deviceId),
            trimmed.length,
            AppConfig.visionProvider,
        )

        val month = YearMonth.now()
        if (visionBudgetRepository.getMonthCost(month) >= AppConfig.visionMonthlyBudgetRub) {
            throw VisionBudgetExceededException()
        }

        val parsed = try {
            visionClient.analyzeWorkout(trimmed)
        } catch (e: Exception) {
            log.warn("workout parse vision_failed device={}: {}", mask(actor.deviceId), e.message)
            throw VisionUnavailableException(cause = e)
        }

        if (parsed.title.length < 2 || parsed.burnedKcal !in 1..10_000) {
            log.warn("workout parse invalid device={} title={} kcal={}", mask(actor.deviceId), parsed.title, parsed.burnedKcal)
            throw VisionUnavailableException("Не удалось понять описание. Уточните активность и длительность.")
        }

        visionBudgetRepository.addCost(month, AppConfig.visionCostPerRequestRub)

        log.info(
            "workout parse ok device={} title={} kcal={} min={}",
            mask(actor.deviceId),
            parsed.title,
            parsed.burnedKcal,
            parsed.durationMinutes,
        )
        return parsed
    }

    override suspend fun deleteWorkout(actor: Actor, workoutId: UUID) {
        val workout = workoutRepository.findById(workoutId) ?: throw NotFoundException("Тренировка не найдена")
        if (!ownsWorkout(actor, workout)) throw ForbiddenException()
        workoutRepository.delete(workoutId)
    }

    override suspend fun syncActivity(
        actor: Actor,
        request: DiaryService.SyncActivityRequest,
        localDate: LocalDate,
        timezoneOffsetMinutes: Int,
    ): DiaryDayResponse {
        if (request.steps !in 0..100_000) throw BadRequestException("Шаги должны быть от 0 до 100000")
        if (request.kcal !in 0..10_000) throw BadRequestException("Калории активности должны быть от 0 до 10000")

        val existing = loadActivity(actor, localDate)
        val shouldUpdate = existing == null ||
            request.kcal > existing.kcal ||
            request.steps > existing.steps ||
            (request.kcal == existing.kcal && request.steps == existing.steps && request.source != existing.source)

        if (shouldUpdate) {
            dailyActivityRepository.upsert(
                DailyActivityRecord(
                    deviceId = actor.deviceId,
                    userId = actor.userId,
                    localDate = localDate,
                    steps = request.steps,
                    kcal = request.kcal,
                    source = request.source,
                    updatedAt = Instant.now(),
                ),
            )
            log.info(
                "activity sync device={} date={} steps={} kcal={} source={}",
                mask(actor.deviceId),
                localDate,
                request.steps,
                request.kcal,
                request.source,
            )
        }

        return getDay(actor, localDate, timezoneOffsetMinutes)
    }

    private suspend fun resolveDishes(actor: Actor, request: DiaryService.CreateDiaryEntryRequest): List<DishDto> {
        request.scanId?.let { scanId ->
            val session = scanSessionRepository.findById(scanId)
                ?: throw BadRequestException("scan_id не найден")
            if (session.deviceId != actor.deviceId) throw ForbiddenException()
            return request.dishes?.takeIf { it.isNotEmpty() } ?: session.dishes
        }
        val dishes = request.dishes
        if (dishes.isNullOrEmpty()) throw BadRequestException("Укажите scan_id или dishes")
        return dishes
    }

    private suspend fun loadEntries(actor: Actor, date: LocalDate, tzOffsetMin: Int): List<DiaryEntryRecord> =
        if (actor.userId != null) {
            diaryRepository.findEntriesByUser(actor.userId, date, tzOffsetMin)
        } else {
            diaryRepository.findEntriesByDevice(actor.deviceId, date, tzOffsetMin)
        }

    private suspend fun loadWorkouts(actor: Actor, date: LocalDate, tzOffsetMin: Int): List<WorkoutRecord> =
        if (actor.userId != null) {
            workoutRepository.findByUser(actor.userId, date, tzOffsetMin)
        } else {
            workoutRepository.findByDevice(actor.deviceId, date, tzOffsetMin)
        }

    private suspend fun loadActivity(actor: Actor, date: LocalDate): DailyActivityRecord? =
        if (actor.userId != null) {
            dailyActivityRepository.findByUser(actor.userId, date)
                ?: dailyActivityRepository.findByDevice(actor.deviceId, date)
        } else {
            dailyActivityRepository.findByDevice(actor.deviceId, date)
        }

    private fun ownsEntry(actor: Actor, entry: DiaryEntryRecord): Boolean =
        entry.deviceId == actor.deviceId ||
            (actor.userId != null && entry.userId == actor.userId)

    private fun ownsWorkout(actor: Actor, workout: WorkoutRecord): Boolean =
        workout.deviceId == actor.deviceId ||
            (actor.userId != null && workout.userId == actor.userId)

    private fun DiaryEntryRecord.toDto() = DiaryEntryDto(
        id = id,
        createdAt = createdAt,
        mealType = mealType,
        totalKcal = totalKcal,
        dishes = dishes,
    )

    private fun WorkoutRecord.toDto() = WorkoutEntryDto(
        id = id,
        createdAt = createdAt,
        name = name,
        kcal = kcal,
    )

    private fun mask(deviceId: UUID): String = deviceId.toString().take(8) + "…"

    companion object {
        const val MIN_WORKOUT_DESCRIPTION_CHARS = 3
        const val MAX_WORKOUT_DESCRIPTION_CHARS = 500
    }
}
