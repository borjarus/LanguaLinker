package com.mila.langualinker.domain.model

data class SentenceWordLink(
    val id: Long,
    val sentenceCardId: Long,
    val wordCardId: Long,
    val positionInSentence: Int,
    val surfaceForm: String,
)
