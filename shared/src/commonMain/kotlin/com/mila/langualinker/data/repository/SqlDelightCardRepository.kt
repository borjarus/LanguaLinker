package com.mila.langualinker.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mila.langualinker.database.AppDatabase
import com.mila.langualinker.domain.model.Card
import com.mila.langualinker.fsrs.CardFsrsState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightCardRepository(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : CardRepository {

    private val queries get() = database.cardsQueries

    override fun getCardsByDeckId(deckId: Long): Flow<List<Card>> =
        queries.getCardsByDeckId(deckId).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getCardById(id: Long): Card? = withContext(dispatcher) {
        queries.getCardById(id).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun getDueCards(deckId: Long, today: String): List<Card> =
        withContext(dispatcher) {
            queries.getDueCards(deckId, today).executeAsList().map { it.toDomain() }
        }

    override suspend fun getNewCards(deckId: Long): List<Card> = withContext(dispatcher) {
        queries.getNewCards(deckId).executeAsList().map { it.toDomain() }
    }

    /** Resolves a bundled `wordCardRef` (a lemma) against a word deck. */
    suspend fun getCardByDeckIdAndFront(deckId: Long, front: String): Card? =
        withContext(dispatcher) {
            queries.getCardByDeckIdAndFront(deckId, front).executeAsOneOrNull()?.toDomain()
        }

    override suspend fun insertCard(card: Card): Long = withContext(dispatcher) {
        queries.transactionWithResult {
            insertCardInTransaction(card)
        }
    }

    /**
     * Insert without opening a transaction of its own, for callers that already hold one —
     * the bundled importer inserts a whole deck inside a single transaction.
     */
    internal fun insertCardInTransaction(card: Card): Long {
        val fsrs = card.fsrsState
        queries.insertCard(
            deckId = card.deckId,
            front = card.front,
            back = card.back,
            tags = card.tags.toTagString(),
            cardType = card.cardType.name,
            position = card.position.toLong(),
            due = fsrs.due,
            stability = fsrs.stability.toDouble(),
            difficulty = fsrs.difficulty.toDouble(),
            retrievability = fsrs.retrievability.toDouble(),
            easeFactor = fsrs.easeFactor.toDouble(),
            averageInterval = fsrs.averageInterval.toLong(),
            reps = fsrs.reps.toLong(),
            lapses = fsrs.lapses.toLong(),
            lastReviewDate = fsrs.lastReviewDate.toIsoString(),
            nextReviewDate = fsrs.nextReviewDate.toIsoString(),
            scheduledDays = fsrs.scheduledDays.toLong(),
            elapsedDays = fsrs.elapsedDays.toLong(),
            cardState = fsrs.state.name,
            createdAt = card.createdAt.toEpochMilliseconds(),
        )
        return queries.lastInsertRowId().executeAsOne()
    }

    override suspend fun updateCard(card: Card) {
        withContext(dispatcher) {
            queries.updateCard(
                front = card.front,
                back = card.back,
                tags = card.tags.toTagString(),
                id = card.id,
            )
        }
    }

    override suspend fun updateCardFsrsState(cardId: Long, fsrsState: CardFsrsState) {
        withContext(dispatcher) {
            queries.updateCardFsrsState(
                due = fsrsState.due,
                stability = fsrsState.stability.toDouble(),
                difficulty = fsrsState.difficulty.toDouble(),
                retrievability = fsrsState.retrievability.toDouble(),
                easeFactor = fsrsState.easeFactor.toDouble(),
                averageInterval = fsrsState.averageInterval.toLong(),
                reps = fsrsState.reps.toLong(),
                lapses = fsrsState.lapses.toLong(),
                lastReviewDate = fsrsState.lastReviewDate.toIsoString(),
                nextReviewDate = fsrsState.nextReviewDate.toIsoString(),
                scheduledDays = fsrsState.scheduledDays.toLong(),
                elapsedDays = fsrsState.elapsedDays.toLong(),
                cardState = fsrsState.state.name,
                id = cardId,
            )
        }
    }

    /**
     * Deleting a word card must not orphan the sentence links that point at it, so those
     * links fall back to `Unresolved` instead of keeping a dangling id.
     */
    override suspend fun deleteCard(id: Long) = withContext(dispatcher) {
        database.transaction {
            database.grammar_tipsQueries.deleteTipsByCardId(id)
            database.associationsQueries.deleteAssociationsByCardId(id)
            database.sentence_word_linksQueries.deleteLinksBySentenceCardId(id)
            database.sentence_word_linksQueries.unresolveLinksByWordCardId(id)
            database.review_logsQueries.deleteLogsByCardId(id)
            queries.deleteCard(id)
        }
    }

    override suspend fun deleteCardsByDeckId(deckId: Long) = withContext(dispatcher) {
        database.transaction {
            queries.getCardsByDeckId(deckId).executeAsList().forEach { card ->
                database.grammar_tipsQueries.deleteTipsByCardId(card.id)
                database.associationsQueries.deleteAssociationsByCardId(card.id)
                database.sentence_word_linksQueries.deleteLinksBySentenceCardId(card.id)
                database.sentence_word_linksQueries.unresolveLinksByWordCardId(card.id)
                database.review_logsQueries.deleteLogsByCardId(card.id)
            }
            queries.deleteCardsByDeckId(deckId)
        }
    }
}
