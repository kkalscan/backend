package ru.kkalscan.domain.service

import org.slf4j.LoggerFactory
import ru.kkalscan.domain.BadRequestException
import ru.kkalscan.domain.ForbiddenException
import ru.kkalscan.domain.NotFoundException
import ru.kkalscan.domain.model.Actor
import ru.kkalscan.domain.model.DishDto
import ru.kkalscan.domain.port.CreateDiaryEntryResponse
import ru.kkalscan.domain.port.CreateWorkoutResponse
import ru.kkalscan.domain.port.DiaryDayResponse
import ru.kkalscan.domain.port.DiaryEntryDto
import ru.kkalscan.domain.port.DiaryEntryRecord
import ru.kkalscan.domain.port.DiaryRepository
import ru.kkalscan.domain.port.DiaryService
import ru.kkalscan.domain.port.QuotaService
import ru.kkalscan.domain.port.ScanSessionRepository
import ru.kkalscan.domain.port.WorkoutEntryDto
import ru.kkalscan.domain.port.WorkoutRecord
import ru.kkalscan.domain.port.WorkoutRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class DiaryServiceImpl(
    private val diaryRepository: DiaryRepository,
    private val workoutRepository: WorkoutRepository,
    private val quotaService: QuotaService,
    private val scanSessionRepository: ScanSessionRepository,
) : DiaryService {

    private val log = LoggerFactory.getLogger(DiaryServiceImpl::class.java)

    override suspend fun getDay(
        actor: Actor,
        date: LocalDate,
        timezoneOffsetMinutes: Int,
    ): DiaryDayResponse {
        val entries = loadEntries(actor, date, timezoneOffsetMinutes)
        val workouts = loadWorkouts(actor, date, timezoneOffsetMinutes)
        val consumed = entries.sumOf { it.totalKcal }
        val burned = workouts.sumOf { it.kcal }
        log.debug(
            "diary get device={} date={} entries={} workouts={} kcal={} burned={}",
            mask(actor.deviceId),
            date,
            entries.size,
            workouts.size,
            consumed,
            burned,
        )
        return DiaryDayResponse(
            date = date,
            totalKcal = consumed,
            totalBurnedKcal = burned,
            netKcal = consumed - burned,
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

    override suspend fun deleteWorkout(actor: Actor, workoutId: UUID) {
        val workout = workoutRepository.findById(workoutId) ?: throw NotFoundException("Тренировка не найдена")
        if (!ownsWorkout(actor, workout)) throw ForbiddenException()
        workoutRepository.delete(workoutId)
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
}
