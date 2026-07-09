package com.mila.langualinker.data.repository

import com.mila.langualinker.domain.model.Card

/** @deprecated Use [CardRepository] instead */
@Deprecated("Use CardRepository instead")
interface FlashcardRepository {
    suspend fun save(card: Card)
    suspend fun getById(id: String): Card?
}
