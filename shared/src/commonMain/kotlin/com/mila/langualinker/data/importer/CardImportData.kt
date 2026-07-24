package com.mila.langualinker.data.importer

import kotlinx.serialization.Serializable

@Serializable
data class CardImportData(
    val front: String,
    val back: String,
    val tags: String = "",
    val cardType: String = "Sentence",
    val position: Int = 0,
    val grammarTips: List<String> = emptyList(),
    val wordLinks: List<String> = emptyList(),
    val due: Long = 0L,
    val stability: Float = 0f,
    val difficulty: Float = 0f,
    val retrievability: Float = 0f,
    val easeFactor: Float = 2.5f,
    val averageInterval: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val scheduledDays: Int = 0,
    val elapsedDays: Int = 0,
    val cardState: String = "New",
    val lastReviewDate: String? = null,
    val nextReviewDate: String? = null,
)
