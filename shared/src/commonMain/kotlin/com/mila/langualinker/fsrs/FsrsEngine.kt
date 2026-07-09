package com.mila.langualinker.fsrs

import com.mila.langualinker.domain.model.Card
import kotlin.time.Instant

interface FsrsEngine {
    fun nextReviewAt(card: Card, now: Instant): Instant
}
