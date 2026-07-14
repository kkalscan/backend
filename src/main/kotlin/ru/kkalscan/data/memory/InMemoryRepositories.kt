package ru.kkalscan.data.memory

import ru.kkalscan.domain.model.DishDto
import ru.kkalscan.domain.model.MealType
import ru.kkalscan.domain.model.OAuthProvider
import ru.kkalscan.domain.port.BugReportRecord
import ru.kkalscan.domain.port.BugReportRepository
import ru.kkalscan.domain.features.FeatureSearchCatalog
import ru.kkalscan.domain.port.FeatureSearchItemRecord
import ru.kkalscan.domain.port.FeatureSearchRepository
import ru.kkalscan.domain.port.SearchLogRecord
import ru.kkalscan.domain.port.SearchLogRepository
import ru.kkalscan.domain.port.SearchQueryStat
import ru.kkalscan.domain.port.DailyActivityRecord
import ru.kkalscan.domain.port.DailyActivityRepository
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
import ru.kkalscan.domain.port.WorkoutRecord
import ru.kkalscan.domain.port.WorkoutRepository
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

    override suspend fun consumedKcalByDay(
        deviceId: UUID,
        from: LocalDate,
        to: LocalDate,
        tzOffsetMin: Int,
    ): Map<LocalDate, Int> {
        val offset = ZoneOffset.ofTotalSeconds(tzOffsetMin * 60)
        return entries.values
            .asSequence()
            .filter { it.deviceId == deviceId }
            .map { entry ->
                entry.createdAt.atOffset(offset).toLocalDate() to entry.totalKcal
            }
            .filter { (date, _) -> !date.isBefore(from) && !date.isAfter(to) }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, kcals) -> kcals.sum() }
    }

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

class InMemoryWorkoutRepository : WorkoutRepository {
    private val workouts = ConcurrentHashMap<UUID, WorkoutRecord>()

    override suspend fun findByDevice(deviceId: UUID, date: LocalDate, tzOffsetMin: Int): List<WorkoutRecord> =
        filterByDate(workouts.values.filter { it.deviceId == deviceId }, date, tzOffsetMin)

    override suspend fun findByUser(userId: UUID, date: LocalDate, tzOffsetMin: Int): List<WorkoutRecord> =
        filterByDate(workouts.values.filter { it.userId == userId }, date, tzOffsetMin)

    private fun filterByDate(list: List<WorkoutRecord>, date: LocalDate, tzOffsetMin: Int): List<WorkoutRecord> {
        val offset = ZoneOffset.ofTotalSeconds(tzOffsetMin * 60)
        return list.filter { it.createdAt.atOffset(offset).toLocalDate() == date }
            .sortedBy { it.createdAt }
    }

    override suspend fun insert(workout: WorkoutRecord): WorkoutRecord {
        workouts[workout.id] = workout
        return workout
    }

    override suspend fun delete(id: UUID) {
        workouts.remove(id)
    }

    override suspend fun findById(id: UUID): WorkoutRecord? = workouts[id]

    override suspend fun updateUserIdForDevice(deviceId: UUID, userId: UUID) {
        workouts.values.filter { it.deviceId == deviceId }.forEach { workout ->
            workouts[workout.id] = workout.copy(userId = userId)
        }
    }
}

class InMemoryDailyActivityRepository : DailyActivityRepository {
    private data class Key(val deviceId: UUID, val localDate: LocalDate)

    private val activities = ConcurrentHashMap<Key, DailyActivityRecord>()

    override suspend fun findByDevice(deviceId: UUID, date: LocalDate): DailyActivityRecord? =
        activities[Key(deviceId, date)]

    override suspend fun findByUser(userId: UUID, date: LocalDate): DailyActivityRecord? =
        activities.values
            .filter { it.userId == userId && it.localDate == date }
            .maxByOrNull { it.updatedAt }

    override suspend fun upsert(record: DailyActivityRecord): DailyActivityRecord {
        activities[Key(record.deviceId, record.localDate)] = record
        return record
    }

    override suspend fun updateUserIdForDevice(deviceId: UUID, userId: UUID) {
        activities.entries.filter { it.key.deviceId == deviceId }.forEach { (key, record) ->
            activities[key] = record.copy(userId = userId)
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

    override suspend fun findById(id: UUID): PaymentRecord? = payments[id]
}

class InMemoryVisionBudgetRepository : VisionBudgetRepository {
    private val costs = ConcurrentHashMap<YearMonth, Int>()

    override suspend fun getMonthCost(month: YearMonth): Int = costs[month] ?: 0

    override suspend fun addCost(month: YearMonth, rub: Int) {
        costs.merge(month, rub) { a, b -> a + b }
    }
}

class InMemorySearchLogRepository : SearchLogRepository {
    private val logs = mutableListOf<SearchLogRecord>()

    override suspend fun log(record: SearchLogRecord) {
        synchronized(logs) { logs.add(record) }
    }

    override suspend fun topQueries(days: Int, limit: Int): List<SearchQueryStat> {
        val cutoff = java.time.Instant.now().minus(java.time.Duration.ofDays(days.toLong()))
        return synchronized(logs) {
            logs
                .groupBy { it.queryNormalized }
                .map { (query, items) -> SearchQueryStat(query, items.size) }
                .sortedWith(compareByDescending<SearchQueryStat> { it.count }.thenBy { it.query })
                .take(limit)
        }
    }

    fun all(): List<SearchLogRecord> = synchronized(logs) { logs.toList() }
}

class InMemoryBugReportRepository : BugReportRepository {
    private val reports = ConcurrentHashMap<UUID, BugReportRecord>()
    private val byDevice = ConcurrentHashMap<UUID, UUID>()

    override suspend fun hasReportForDevice(deviceId: UUID): Boolean = byDevice.containsKey(deviceId)

    override suspend fun create(
        deviceId: UUID,
        userId: UUID?,
        email: String,
        description: String,
        screenshots: List<ByteArray>,
    ): UUID {
        val id = UUID.randomUUID()
        val record = BugReportRecord(
            id = id,
            deviceId = deviceId,
            userId = userId,
            email = email,
            description = description,
            screenshotCount = screenshots.size,
            createdAt = Instant.now(),
        )
        reports[id] = record
        byDevice[deviceId] = id
        return id
    }

    override suspend fun deleteReport(reportId: UUID) {
        reports.remove(reportId)
        byDevice.entries.removeIf { it.value == reportId }
    }
}

class InMemoryFeatureSearchRepository : FeatureSearchRepository {
    override suspend fun listEnabled(locale: String): List<FeatureSearchItemRecord> =
        FeatureSearchCatalog.items
}

data class InMemoryRepositories(
    val devices: InMemoryDeviceRepository = InMemoryDeviceRepository(),
    val quotas: InMemoryScanQuotaRepository = InMemoryScanQuotaRepository(),
    val scanSessions: InMemoryScanSessionRepository = InMemoryScanSessionRepository(),
    val diary: InMemoryDiaryRepository = InMemoryDiaryRepository(),
    val workouts: InMemoryWorkoutRepository = InMemoryWorkoutRepository(),
    val dailyActivity: InMemoryDailyActivityRepository = InMemoryDailyActivityRepository(),
    val users: InMemoryUserRepository = InMemoryUserRepository(),
    val oauth: InMemoryOAuthRepository = InMemoryOAuthRepository(),
    val payments: InMemoryPaymentRepository = InMemoryPaymentRepository(),
    val visionBudget: InMemoryVisionBudgetRepository = InMemoryVisionBudgetRepository(),
    val bugReports: InMemoryBugReportRepository = InMemoryBugReportRepository(),
    val searchLogs: InMemorySearchLogRepository = InMemorySearchLogRepository(),
    val featureSearch: InMemoryFeatureSearchRepository = InMemoryFeatureSearchRepository(),
    val promoCodes: InMemoryPromoCodeRepository = InMemoryPromoCodeRepository(),
    val devicePromoBindings: InMemoryDevicePromoBindingRepository = InMemoryDevicePromoBindingRepository(),
)
