package ru.kkalscan

import ru.kkalscan.data.memory.InMemoryRepositories
import ru.kkalscan.domain.model.Actor
import ru.kkalscan.domain.model.MealType
import ru.kkalscan.domain.port.DiaryService
import java.util.UUID

object TestFixtures {
    val deviceId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")

    fun guestActor(deviceId: UUID = this.deviceId) = Actor(
        deviceId = deviceId,
        userId = null,
        isPro = false,
        accountLinked = false,
        linkedProviders = emptyList(),
    )

    fun proActor(deviceId: UUID = this.deviceId) = guestActor(deviceId).copy(isPro = true)

    fun freshModule(): AppModule = AppModule(repos = InMemoryRepositories())
}
