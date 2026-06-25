package com.mila.langualinker.domain.model

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class Flashcard(
    val id: String,
    val front: String,
    val back: String,
    val createdAt: Instant,
)
