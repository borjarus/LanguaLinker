package com.mila.langualinker.domain.model

data class CardTemplate(
    val id: Long,
    val deckId: Long,
    val name: String,
    val frontTemplate: String,
    val backTemplate: String,
)
