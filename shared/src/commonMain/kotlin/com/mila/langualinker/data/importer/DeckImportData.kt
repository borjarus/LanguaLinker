package com.mila.langualinker.data.importer

import kotlinx.serialization.Serializable

@Serializable
data class DeckImportData(
    val name: String,
    val language: String,
    val type: String = "Linguistic",
    val cards: List<CardImportData> = emptyList(),
)
