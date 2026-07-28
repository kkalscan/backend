package ru.kkalscan.domain.service

import ru.kkalscan.domain.model.DishDto
import ru.kkalscan.domain.model.Encouragement
import ru.kkalscan.domain.model.EncouragementMood
import kotlin.math.abs

class EncouragementService {
    fun forDishes(dishes: List<DishDto>): Encouragement {
        val mood = scoreMood(dishes)
        val bank = phrasesFor(mood)
        val text = bank[pickIndex(dishes, bank.size)]
        return Encouragement(mood = mood, text = text, source = "rules")
    }

    fun scoreMood(dishes: List<DishDto>): EncouragementMood {
        if (dishes.isEmpty()) return EncouragementMood.ok
        val kcal = dishes.sumOf { it.kcal }.coerceAtLeast(1)
        val protein = dishes.sumOf { it.protein }
        val fiber = dishes.sumOf { it.fiber }
        val proteinShare = (protein * 4.0) / kcal

        if (fiber >= 5.0 || proteinShare >= 0.22) return EncouragementMood.good
        if (fiber < 1.5 && proteinShare < 0.12 && kcal >= 400) return EncouragementMood.bad
        return EncouragementMood.ok
    }

    private fun pickIndex(dishes: List<DishDto>, size: Int): Int {
        if (size <= 0) return 0
        val seed = dishes.joinToString("|") { "${it.name}:${it.kcal}:${it.fiber}" }
        return abs(seed.hashCode()) % size
    }

    companion object {
        fun phrasesFor(mood: EncouragementMood): List<String> = when (mood) {
            EncouragementMood.good -> GOOD
            EncouragementMood.ok -> OK
            EncouragementMood.bad -> BAD
        }

        private val GOOD = listOf(
            "Записал — уже победа. Сбалансированный приём, так держать.",
            "Отличная запись. Белок и клетчатка на месте.",
            "Красивый рацион. Дневник любит такие приёмы пищи.",
            "Супер: еда в дневнике и макросы выглядят здорово.",
        )

        private val OK = listOf(
            "Еда в дневнике — отлично. Главное — не пропускать записи.",
            "Записал, молодец. Можно чуть подкрутить белок или овощи в следующий раз.",
            "Нормально. Привычка вести дневник важнее идеального блюда.",
            "Ок, запись сохранена. Продолжай в том же темпе.",
        )

        private val BAD = listOf(
            "Записал — супер. В следующий раз добавь клетчатку или белок.",
            "Дневник живой — это главное. Овощи и вода помогут балансу.",
            "Ок, факт записи уже плюс. Попробуй чуть легче на жирах/сахаре дальше.",
            "Спасибо за запись. Следующий приём можно сделать насыщеннее белком.",
        )
    }
}
