package com.mila.langualinker.data.repository

import com.mila.langualinker.domain.model.Card
import com.mila.langualinker.fsrs.CardFsrsState
import kotlinx.coroutines.flow.Flow

interface CardRepository {
    fun getCardsByDeckId(deckId: Long): Flow<List<Card>>
    suspend fun getCardById(id: Long): Card?
    suspend fun getDueCards(deckId: Long, today: String): List<Card>
    suspend fun getNewCards(deckId: Long): List<Card>
    suspend fun insertCard(card: Card): Long
    suspend fun updateCard(card: Card)
    suspend fun updateCardFsrsState(cardId: Long, fsrsState: CardFsrsState)
    suspend fun deleteCard(id: Long)
    suspend fun deleteCardsByDeckId(deckId: Long)
}
