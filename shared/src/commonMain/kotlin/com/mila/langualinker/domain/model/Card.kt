package com.mila.langualinker.domain.model

import com.mila.langualinker.fsrs.CardFsrsState
import kotlin.time.Instant

data class Card(
    val id: Long,
    val deckId: Long,
    val front: String,
    val back: String,
    val sortField: String,
    val tags: List<String>,
    val cardType: CardType,
    val position: Int,
    val fsrsState: CardFsrsState,
    val createdAt: Instant,
)

enum class CardType { Text, Sentence, Word }
