package com.mila.langualinker.testutil

import com.mila.langualinker.data.repository.CardRepository
import com.mila.langualinker.data.repository.DeckRepository
import com.mila.langualinker.data.repository.GrammarTipRepository
import com.mila.langualinker.data.repository.SentenceWordLinkRepository
import com.mila.langualinker.domain.model.Card
import com.mila.langualinker.domain.model.Deck
import com.mila.langualinker.domain.model.DeckSettings
import com.mila.langualinker.domain.model.DeckType
import com.mila.langualinker.domain.model.GrammarTip
import com.mila.langualinker.domain.model.SentenceWordLink
import com.mila.langualinker.domain.usecase.ExportDeckUseCase
import com.mila.langualinker.domain.usecase.ImportDeckUseCase
import com.mila.langualinker.fsrs.CardFsrsState
import com.mila.langualinker.fsrs.CardState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory repository set shared by importer/exporter tests. */
internal class FakeRepositories {
    val deckRepository = FakeDeckRepository()
    val cardRepository = FakeCardRepository()
    val grammarTipRepository = FakeGrammarTipRepository()
    val sentenceWordLinkRepository = FakeSentenceWordLinkRepository()

    fun createExportUseCase() = ExportDeckUseCase(
        deckRepository = deckRepository,
        cardRepository = cardRepository,
        grammarTipRepository = grammarTipRepository,
        sentenceWordLinkRepository = sentenceWordLinkRepository,
    )

    fun createImportUseCase() = ImportDeckUseCase(
        deckRepository = deckRepository,
        cardRepository = cardRepository,
        grammarTipRepository = grammarTipRepository,
        sentenceWordLinkRepository = sentenceWordLinkRepository,
    )
}

internal class FakeDeckRepository : DeckRepository {
    private var nextId = 1L
    private val decks = MutableStateFlow<List<Deck>>(emptyList())
    private val settings = mutableMapOf<Long, DeckSettings>()

    override fun getAllDecks(): Flow<List<Deck>> = decks

    override suspend fun getDeckById(id: Long): Deck? = decks.value.firstOrNull { it.id == id }

    override suspend fun insertDeck(name: String, language: String, type: String): Long {
        val deckId = nextId++
        val deckType = runCatching { DeckType.valueOf(type) }.getOrDefault(DeckType.Linguistic)
        decks.value = decks.value + Deck(deckId, name, language, deckType)
        return deckId
    }

    override suspend fun updateDeck(deck: Deck) {
        decks.value = decks.value.map { if (it.id == deck.id) deck else it }
    }

    override suspend fun deleteDeck(id: Long) {
        decks.value = decks.value.filterNot { it.id == id }
    }

    override suspend fun getDeckSettings(deckId: Long): DeckSettings? = settings[deckId]

    override suspend fun upsertDeckSettings(settings: DeckSettings) {
        this.settings[settings.deckId] = settings
    }
}

internal class FakeCardRepository : CardRepository {
    private var nextId = 1L
    private val cards = MutableStateFlow<List<Card>>(emptyList())

    override fun getCardsByDeckId(deckId: Long): Flow<List<Card>> = cards.map { list ->
        list.filter { it.deckId == deckId }.sortedBy { it.position }
    }

    override suspend fun getCardById(id: Long): Card? = cards.value.firstOrNull { it.id == id }

    override suspend fun getDueCards(deckId: Long, today: String): List<Card> =
        cards.value.filter { it.deckId == deckId && it.fsrsState.due > 0 }

    override suspend fun getNewCards(deckId: Long): List<Card> =
        cards.value.filter { it.deckId == deckId && it.fsrsState.state == CardState.New }

    override suspend fun insertCard(card: Card): Long {
        val id = nextId++
        cards.value = cards.value + card.copy(id = id)
        return id
    }

    override suspend fun updateCard(card: Card) {
        cards.value = cards.value.map { if (it.id == card.id) card else it }
    }

    override suspend fun updateCardFsrsState(cardId: Long, fsrsState: CardFsrsState) {
        cards.value = cards.value.map { card ->
            if (card.id == cardId) card.copy(fsrsState = fsrsState) else card
        }
    }

    override suspend fun deleteCard(id: Long) {
        cards.value = cards.value.filterNot { it.id == id }
    }

    override suspend fun deleteCardsByDeckId(deckId: Long) {
        cards.value = cards.value.filterNot { it.deckId == deckId }
    }
}

internal class FakeGrammarTipRepository : GrammarTipRepository {
    private var nextId = 1L
    private val tips = MutableStateFlow<List<GrammarTip>>(emptyList())

    override fun getTipsByCardId(cardId: Long): Flow<List<GrammarTip>> = tips.map { list ->
        list.filter { it.cardId == cardId }.sortedBy { it.order }
    }

    override suspend fun insertTip(tip: GrammarTip): Long {
        val id = nextId++
        tips.value = tips.value + tip.copy(id = id)
        return id
    }

    override suspend fun updateTipOrder(id: Long, order: Int) {
        tips.value = tips.value.map { if (it.id == id) it.copy(order = order) else it }
    }

    override suspend fun updateTipContent(id: Long, content: String) {
        tips.value = tips.value.map { if (it.id == id) it.copy(content = content) else it }
    }

    override suspend fun deleteTip(id: Long) {
        tips.value = tips.value.filterNot { it.id == id }
    }

    override suspend fun deleteTipsByCardId(cardId: Long) {
        tips.value = tips.value.filterNot { it.cardId == cardId }
    }
}

internal class FakeSentenceWordLinkRepository : SentenceWordLinkRepository {
    private var nextId = 1L
    private val links = mutableListOf<SentenceWordLink>()

    override suspend fun getLinksBySentenceCardId(sentenceCardId: Long): List<SentenceWordLink> =
        links.filter { it.sentenceCardId == sentenceCardId }.sortedBy { it.positionInSentence }

    override suspend fun insertLink(link: SentenceWordLink) {
        links += link.copy(id = nextId++)
    }

    override suspend fun deleteLink(id: Long) {
        links.removeAll { it.id == id }
    }

    override suspend fun deleteLinksBySentenceCardId(sentenceCardId: Long) {
        links.removeAll { it.sentenceCardId == sentenceCardId }
    }
}
