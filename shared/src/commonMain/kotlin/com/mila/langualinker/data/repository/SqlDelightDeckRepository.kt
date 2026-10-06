package com.mila.langualinker.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mila.langualinker.database.AppDatabase
import com.mila.langualinker.domain.model.Deck
import com.mila.langualinker.domain.model.DeckSettings
import com.mila.langualinker.domain.model.DeckType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightDeckRepository(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : DeckRepository {

    private val queries get() = database.decksQueries
    private val settingsQueries get() = database.deck_settingsQueries

    override fun getAllDecks(): Flow<List<Deck>> =
        queries.getAllDecks().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getDeckById(id: Long): Deck? = withContext(dispatcher) {
        queries.getDeckById(id).executeAsOneOrNull()?.toDomain()
    }

    suspend fun getDeckByNameAndLanguage(name: String, language: String): Deck? =
        withContext(dispatcher) {
            queries.getDeckByNameAndLanguage(name, language).executeAsOneOrNull()?.toDomain()
        }

    override suspend fun insertDeck(name: String, language: String, type: String): Long =
        withContext(dispatcher) {
            queries.transactionWithResult {
                queries.insertDeck(name = name, language = language, type = type)
                queries.lastInsertRowId().executeAsOne()
            }
        }

    override suspend fun updateDeck(deck: Deck) {
        withContext(dispatcher) {
            queries.updateDeck(
                name = deck.name,
                language = deck.language,
                type = deck.type.name,
                id = deck.id,
            )
        }
    }

    /**
     * Deletes the deck together with everything that hangs off it. SQLite foreign keys are
     * not declared on these tables yet, so the cascade is explicit — and transactional, so a
     * failure cannot leave cards pointing at a deck that no longer exists.
     */
    override suspend fun deleteDeck(id: Long) {
        withContext(dispatcher) {
            database.transaction {
                database.cardsQueries.getCardsByDeckId(id).executeAsList().forEach { card ->
                    database.grammar_tipsQueries.deleteTipsByCardId(card.id)
                    database.associationsQueries.deleteAssociationsByCardId(card.id)
                    database.sentence_word_linksQueries.deleteLinksBySentenceCardId(card.id)
                    database.sentence_word_linksQueries.unresolveLinksByWordCardId(card.id)
                    database.review_logsQueries.deleteLogsByCardId(card.id)
                }
                database.cardsQueries.deleteCardsByDeckId(id)
                database.card_templatesQueries.deleteTemplatesByDeckId(id)
                settingsQueries.deleteByDeckId(id)
                queries.deleteDeck(id)
            }
        }
    }

    override suspend fun getDeckSettings(deckId: Long): DeckSettings? = withContext(dispatcher) {
        val deckType = queries.getDeckById(deckId).executeAsOneOrNull()?.type
            ?.let { runCatching { DeckType.valueOf(it) }.getOrNull() }
            ?: DeckType.Linguistic
        settingsQueries.getByDeckId(deckId).executeAsOneOrNull()?.let { row ->
            DeckSettings(
                deckId = row.deck_id,
                type = deckType,
                requestRetention = row.request_retention.toFloat(),
                maximumInterval = row.maximum_interval.toInt(),
                newCardsPerDay = row.new_cards_per_day.toInt(),
            )
        }
    }

    override suspend fun upsertDeckSettings(settings: DeckSettings) {
        withContext(dispatcher) {
            settingsQueries.upsert(
                deckId = settings.deckId,
                requestRetention = settings.requestRetention.toDouble(),
                maximumInterval = settings.maximumInterval.toLong(),
                newCardsPerDay = settings.newCardsPerDay.toLong(),
            )
        }
    }
}
