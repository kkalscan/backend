package ru.kkalscan.data.memory

import ru.kkalscan.domain.model.DishDto
import ru.kkalscan.domain.model.MealType
import ru.kkalscan.domain.model.OAuthProvider
import ru.kkalscan.domain.port.DeviceRecord
import ru.kkalscan.domain.port.DeviceRepository
import ru.kkalscan.domain.port.DiaryEntryRecord
import ru.kkalscan.domain.port.DiaryRepository
import ru.kkalscan.domain.port.OAuthRepository
import ru.kkalscan.domain.port.PaymentRecord
import ru.kkalscan.domain.port.PaymentRepository
import ru.kkalscan.domain.port.ScanQuotaRecord
import ru.kkalscan.domain.port.ScanQuotaRepository
import ru.kkalscan.domain.port.ScanSessionRecord
import ru.kkalscan.domain.port.ScanSessionRepository
import ru.kkalscan.domain.port.UserRecord
import ru.kkalscan.domain.port.UserRepository
import ru.kkalscan.domain.port.VisionBudgetRepository
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class InMemoryDeviceRepository : DeviceRepository {
    private val devices = ConcurrentHashMap<UUID, DeviceRecord>()

    override suspend fun findById(id: UUID): DeviceRecord? = devices[id]

    override suspend fun getOrCreate(id: UUID): DeviceRecord =
        devices.computeIfAbsent(id) { DeviceRecord(id = it, userId = null, proUntil = null) }

    override suspend fun updateLastSeen(id: UUID) {
        getOrCreate(id)
    }

    override suspend fun linkToUser(deviceId: UUID, userId: UUID) {
        val current = getOrCreate(deviceId)
        devices[deviceId] = current.copy(userId = userId, proUntil = null)
    }

    override suspend fun setProUntil(deviceId: UUID, until: Instant?) {
        val current = getOrCreate(deviceId)
        devices[deviceId] = current.copy(proUntil = until)
    }

    override suspend fun findByUserId(userId: UUID): List<DeviceRecord> =
        devices.values.filter { it.userId == userId }
}

class InMemoryScanQuotaRepository : ScanQuotaRepository {
    private val quotas = ConcurrentHashMap<Pair<UUID, LocalDate>, ScanQuotaRecord>()

    private fun key(deviceId: UUID, date: LocalDate) = deviceId to date

    override suspend fun getOrCreate(deviceId: UUID, date: LocalDate): ScanQuotaRecord =
        quotas.computeIfAbsent(key(deviceId, date)) {
            ScanQuotaRecord(deviceId, date, scansUsed = 0, bonusGranted = false, bonusScans = 0)
        }

    override suspend fun incrementUsed(deviceId: UUID, date: LocalDate) {
        val current = getOrCreate(deviceId, date)
        quotas[key(deviceId, date)] = current.copy(scansUsed = current.scansUsed + 1)
    }

    override suspend fun grantBonus(deviceId: UUID, date: LocalDate) {
        val current = getOrCreate(deviceId, date)
        quotas[key(deviceId, date)] = current.copy(bonusGranted = true, bonusScans = 2)
    }
}

class InMemoryScanSessionRepository : ScanSessionRepository {
    private val sessions = ConcurrentHashMap<UUID, ScanSessionRecord>()

    override suspend fun create(deviceId: UUID, dishes: List<DishDto>): UUID {
        val id = UUID.randomUUID()
        sessions[id] = ScanSessionRecord(id, deviceId, dishes, consumed = false)
        return id
    }

    override suspend fun findById(id: UUID): ScanSessionRecord? = sessions[id]

    override suspend fun markConsumed(id: UUID) {
        sessions[id]?.let { sessions[id] = it.copy(consumed = true) }
    }
}

class InMemoryDiaryRepository : DiaryRepository {
    private val entries = ConcurrentHashMap<UUID, DiaryEntryRecord>()

    override suspend fun findEntriesByDevice(deviceId: UUID, date: LocalDate, tzOffsetMin: Int): List<DiaryEntryRecord> =
        filterByDate(entries.values.filter { it.deviceId == deviceId }, date, tzOffsetMin)

    override suspend fun findEntriesByUser(userId: UUID, date: LocalDate, tzOffsetMin: Int): List<DiaryEntryRecord> =
        filterByDate(entries.values.filter { it.userId == userId }, date, tzOffsetMin)

    private fun filterByDate(list: List<DiaryEntryRecord>, date: LocalDate, tzOffsetMin: Int): List<DiaryEntryRecord> {
        val offset = ZoneOffset.ofTotalSeconds(tzOffsetMin * 60)
        return list.filter { it.createdAt.atOffset(offset).toLocalDate() == date }
            .sortedBy { it.createdAt }
    }

    override suspend fun insertEntry(entry: DiaryEntryRecord, dishes: List<DishDto>): DiaryEntryRecord {
        val stored = entry.copy(dishes = dishes)
        entries[entry.id] = stored
        return stored
    }

    override suspend fun deleteEntry(id: UUID) {
        entries.remove(id)
    }

    override suspend fun findEntry(id: UUID): DiaryEntryRecord? = entries[id]

    suspend fun updateUserIdForDevice(deviceId: UUID, userId: UUID) {
        entries.values.filter { it.deviceId == deviceId }.forEach { entry ->
            entries[entry.id] = entry.copy(userId = userId)
        }
    }
}

class InMemoryUserRepository : UserRepository {
    private val users = ConcurrentHashMap<UUID, UserRecord>()

    override suspend fun create(): UUID {
        val id = UUID.randomUUID()
        users[id] = UserRecord(id, proUntil = null)
        return id
    }

    override suspend fun findById(id: UUID): UserRecord? = users[id]

    override suspend fun setProUntil(userId: UUID, until: Instant?) {
        val existing = users[userId] ?: UserRecord(userId, until)
        users[userId] = existing.copy(proUntil = until)
    }

    override suspend fun findDevices(userId: UUID): List<UUID> = emptyList()
}

class InMemoryOAuthRepository : OAuthRepository {
    private data class Key(val provider: OAuthProvider, val providerUserId: String)
    private val links = ConcurrentHashMap<Key, UUID>()
    private val byUser = ConcurrentHashMap<UUID, MutableSet<OAuthProvider>>()

    override suspend fun findByProvider(provider: OAuthProvider, providerUserId: String): UUID? =
        links[Key(provider, providerUserId)]

    override suspend fun link(userId: UUID, provider: OAuthProvider, providerUserId: String) {
        links[Key(provider, providerUserId)] = userId
        byUser.computeIfAbsent(userId) { mutableSetOf() }.add(provider)
    }

    override suspend fun listProviders(userId: UUID): List<OAuthProvider> =
        byUser[userId]?.toList() ?: emptyList()
}

class InMemoryPaymentRepository : PaymentRepository {
    private val payments = ConcurrentHashMap<UUID, PaymentRecord>()
    private val byTochkaId = ConcurrentHashMap<String, UUID>()

    override suspend fun create(payment: PaymentRecord): UUID {
        payments[payment.id] = payment
        payment.tochkaPaymentId?.let { byTochkaId[it] = payment.id }
        return payment.id
    }

    override suspend fun markPaid(id: UUID, tochkaPaymentId: String, paidAt: Instant) {
        payments[id]?.let { current ->
            val updated = current.copy(status = "paid", tochkaPaymentId = tochkaPaymentId, paidAt = paidAt)
            payments[id] = updated
            byTochkaId[tochkaPaymentId] = id
        }
    }

    override suspend fun findByTochkaId(tochkaPaymentId: String): PaymentRecord? =
        byTochkaId[tochkaPaymentId]?.let { payments[it] }
}

class InMemoryVisionBudgetRepository : VisionBudgetRepository {
    private val costs = ConcurrentHashMap<YearMonth, Int>()

    override suspend fun getMonthCost(month: YearMonth): Int = costs[month] ?: 0

    override suspend fun addCost(month: YearMonth, rub: Int) {
        costs.merge(month, rub) { a, b -> a + b }
    }
}

data class InMemoryRepositories(
    val devices: InMemoryDeviceRepository = InMemoryDeviceRepository(),
    val quotas: InMemoryScanQuotaRepository = InMemoryScanQuotaRepository(),
    val scanSessions: InMemoryScanSessionRepository = InMemoryScanSessionRepository(),
    val diary: InMemoryDiaryRepository = InMemoryDiaryRepository(),
    val users: InMemoryUserRepository = InMemoryUserRepository(),
    val oauth: InMemoryOAuthRepository = InMemoryOAuthRepository(),
    val payments: InMemoryPaymentRepository = InMemoryPaymentRepository(),
    val visionBudget: InMemoryVisionBudgetRepository = InMemoryVisionBudgetRepository(),
)
