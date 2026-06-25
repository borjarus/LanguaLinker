package com.mila.langualinker.fsrs

import kotlinx.datetime.LocalDate

data class CardFsrsState(
    val stability: Float,
    val difficulty: Float,
    val retrievability: Float,
    val lastReviewDate: LocalDate,
    val nextReviewDate: LocalDate,
    val scheduledDays: Int,
    val elapsedDays: Int,
    val reps: Int,
    val lapses: Int,
    val state: CardState,
)

enum class Rating {
    Again,
    Hard,
    Good,
    Easy,
}

enum class CardState {
    New,
    Learning,
    Review,
    Relearning,
}
