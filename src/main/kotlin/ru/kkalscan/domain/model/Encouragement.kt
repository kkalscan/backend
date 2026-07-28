package ru.kkalscan.domain.model

enum class EncouragementMood {
    good,
    ok,
    bad,
}

data class Encouragement(
    val mood: EncouragementMood,
    val text: String,
    val source: String = "rules",
)
