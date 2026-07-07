package ru.kkalscan.domain.service

import ru.kkalscan.data.memory.InMemoryDiaryRepository
import ru.kkalscan.domain.port.AccountMergeService
import ru.kkalscan.domain.port.DeviceRepository
import ru.kkalscan.domain.port.DiaryRepository
import ru.kkalscan.domain.port.UserRepository
import ru.kkalscan.domain.port.WorkoutRepository
import java.time.Instant
import java.util.UUID

class AccountMergeServiceImpl(
    private val deviceRepository: DeviceRepository,
    private val userRepository: UserRepository,
    private val diaryRepository: DiaryRepository,
    private val workoutRepository: WorkoutRepository,
) : AccountMergeService {

    override suspend fun mergeDeviceToUser(deviceId: UUID, userId: UUID) {
        val device = deviceRepository.getOrCreate(deviceId)
        userRepository.findById(userId) ?: error("User not found")

        val mergedUntil = maxInstant(
            userRepository.findById(userId)?.proUntil,
            device.proUntil,
        )
        if (mergedUntil != null) {
            userRepository.setProUntil(userId, mergedUntil)
        }

        deviceRepository.linkToUser(deviceId, userId)
        deviceRepository.setProUntil(deviceId, null)

        if (diaryRepository is InMemoryDiaryRepository) {
            diaryRepository.updateUserIdForDevice(deviceId, userId)
        }
        workoutRepository.updateUserIdForDevice(deviceId, userId)
    }

    private fun maxInstant(a: Instant?, b: Instant?): Instant? =
        when {
            a == null -> b
            b == null -> a
            a.isAfter(b) -> a
            else -> b
        }
}
