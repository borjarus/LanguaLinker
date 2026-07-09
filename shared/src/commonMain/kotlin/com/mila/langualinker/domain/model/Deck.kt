package com.mila.langualinker.domain.model

data class Deck(
    val id: Long,
    val name: String,
    val language: String,
    val type: DeckType,
)
