package ru.kkalscan.domain.service

import ru.kkalscan.domain.model.DishDto
import ru.kkalscan.domain.model.EncouragementMood
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EncouragementServiceTest {
    private val service = EncouragementService()

    @Test
    fun highFiberMeal_returnsGoodMoodAndNonBlankText() {
        val result = service.forDishes(
            listOf(DishDto("Салат", 250, 120, 8.0, 4.0, 12.0, fiber = 9.0)),
        )
        assertEquals(EncouragementMood.good, result.mood)
        assertTrue(result.text.isNotBlank())
        assertEquals("rules", result.source)
    }

    @Test
    fun lowProteinLowFiberDenseMeal_returnsBadMood() {
        val result = service.forDishes(
            listOf(DishDto("Чипсы", 100, 520, 3.0, 30.0, 55.0, fiber = 0.5)),
        )
        assertEquals(EncouragementMood.bad, result.mood)
        assertTrue(result.text.isNotBlank())
    }

    @Test
    fun ordinaryMeal_returnsOkMood() {
        val result = service.forDishes(
            listOf(DishDto("Овсянка", 200, 180, 6.0, 4.0, 30.0, fiber = 2.0)),
        )
        assertEquals(EncouragementMood.ok, result.mood)
        assertTrue(result.text.isNotBlank())
    }

    @Test
    fun emptyDishes_returnsOkAndCelebratesLogging() {
        val result = service.forDishes(emptyList())
        assertEquals(EncouragementMood.ok, result.mood)
        assertTrue(result.text.isNotBlank())
    }

    @Test
    fun textComesFromMoodBank() {
        val dishes = listOf(DishDto("Салат", 250, 120, 8.0, 4.0, 12.0, fiber = 9.0))
        val result = service.forDishes(dishes)
        assertTrue(EncouragementService.phrasesFor(EncouragementMood.good).contains(result.text))
    }
}
