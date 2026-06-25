package com.mila.langualinker.data.repository

import com.mila.langualinker.domain.model.Flashcard

interface FlashcardRepository {
    suspend fun save(card: Flashcard)
    suspend fun getById(id: String): Flashcard?
}
