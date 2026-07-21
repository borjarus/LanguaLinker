package com.mila.langualinker.data.repository

import com.mila.langualinker.domain.model.Deck
import com.mila.langualinker.domain.model.DeckSettings
import kotlinx.coroutines.flow.Flow

interface DeckRepository {
    fun getAllDecks(): Flow<List<Deck>>
    suspend fun getDeckById(id: Long): Deck?
    suspend fun insertDeck(name: String, language: String, type: String): Long
    suspend fun updateDeck(deck: Deck)
    suspend fun deleteDeck(id: Long)
    suspend fun getDeckSettings(deckId: Long): DeckSettings?
    suspend fun upsertDeckSettings(settings: DeckSettings)
}
