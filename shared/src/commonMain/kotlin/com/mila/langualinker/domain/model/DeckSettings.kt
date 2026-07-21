package com.mila.langualinker.domain.model

data class DeckSettings(
    val deckId: Long,
    val type: DeckType,
    val requestRetention: Float,
    val maximumInterval: Int,
    val newCardsPerDay: Int,
)
