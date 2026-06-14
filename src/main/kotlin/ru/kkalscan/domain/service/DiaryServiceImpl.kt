package ru.kkalscan.domain.service

import ru.kkalscan.domain.BadRequestException
import ru.kkalscan.domain.ForbiddenException
import ru.kkalscan.domain.NotFoundException
import ru.kkalscan.domain.model.Actor
import ru.kkalscan.domain.model.DishDto
import ru.kkalscan.domain.model.MacroTotals
import ru.kkalscan.domain.port.CreateDiaryEntryResponse
import ru.kkalscan.domain.port.DiaryDayResponse
import ru.kkalscan.domain.port.DiaryEntryDto
import ru.kkalscan.domain.port.DiaryEntryRecord
import ru.kkalscan.domain.port.DiaryRepository
import ru.kkalscan.domain.port.DiaryService
import ru.kkalscan.domain.port.QuotaService
import ru.kkalscan.domain.port.ScanSessionRepository
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class DiaryServiceImpl(
    private val diaryRepository: DiaryRepository,
    private val quotaService: QuotaService,
    private val scanSessionRepository: ScanSessionRepository,
) : DiaryService {

    override suspend fun getDay(
        actor: Actor,
        date: LocalDate,
        timezoneOffsetMinutes: Int,
    ): DiaryDayResponse {
        val entries = loadEntries(actor, date, timezoneOffsetMinutes)
        return DiaryDayResponse(
            date = date,
            totalKcal = entries.sumOf { it.totalKcal },
            scansLeft = quotaService.getScansLeft(actor, date),
            isPro = actor.isPro,
            accountLinked = actor.accountLinked,
            linkedProviders = actor.linkedProviders,
            entries = entries.map { it.toDto() },
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

    private suspend fun resolveDishes(actor: Actor, request: DiaryService.CreateDiaryEntryRequest): List<DishDto> {
        request.scanId?.let { scanId ->
            val session = scanSessionRepository.findById(scanId)
                ?: throw BadRequestException("scan_id не найден")
            if (session.deviceId != actor.deviceId) throw ForbiddenException()
            return session.dishes
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

    private fun ownsEntry(actor: Actor, entry: DiaryEntryRecord): Boolean =
        entry.deviceId == actor.deviceId ||
            (actor.userId != null && entry.userId == actor.userId)

    private fun DiaryEntryRecord.toDto() = DiaryEntryDto(
        id = id,
        createdAt = createdAt,
        mealType = mealType,
        totalKcal = totalKcal,
        dishes = dishes,
    )
}
