package com.mila.langualinker.fsrs

import com.mila.langualinker.domain.model.Flashcard
import kotlin.time.Instant

interface FsrsEngine {
    fun nextReviewAt(card: Flashcard, now: Instant): Instant
}
