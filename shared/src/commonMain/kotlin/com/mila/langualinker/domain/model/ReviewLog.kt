package com.mila.langualinker.domain.model

import com.mila.langualinker.fsrs.Rating
import kotlin.time.Instant

data class ReviewLog(
    val id: Long,
    val cardId: Long,
    val rating: Rating,
    val reviewedAt: Instant,
    val scheduledDays: Int,
    val elapsedDays: Int,
    val stability: Float,
    val difficulty: Float,
)
