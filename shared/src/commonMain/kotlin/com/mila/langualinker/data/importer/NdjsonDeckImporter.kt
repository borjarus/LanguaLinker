package com.mila.langualinker.data.importer

import com.mila.langualinker.data.repository.CardRepository
import com.mila.langualinker.data.repository.DeckRepository
import com.mila.langualinker.data.repository.GrammarTipRepository
import com.mila.langualinker.data.repository.SentenceWordLinkRepository
import com.mila.langualinker.domain.model.Card
import com.mila.langualinker.domain.model.CardType
import com.mila.langualinker.domain.model.DeckType
import com.mila.langualinker.domain.model.GrammarTip
import com.mila.langualinker.domain.model.GrammarTipSource
import com.mila.langualinker.domain.model.SentenceWordLink
import com.mila.langualinker.fsrs.CardFsrsState
import com.mila.langualinker.fsrs.CardState
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json

class NdjsonDeckImporter(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val grammarTipRepository: GrammarTipRepository,
    private val sentenceWordLinkRepository: SentenceWordLinkRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun importFromNdjson(deckName: String, language: String, deckType: DeckType, ndjsonContent: String): Long {
        val cards = ndjsonContent.lines()
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .map { json.decodeFromString<CardImportData>(it) }
        return importFromCardList(deckName, language, deckType, cards)
    }

    suspend fun importFromCardList(
        deckName: String,
        language: String,
        deckType: DeckType,
        cards: List<CardImportData>,
    ): Long {
        val deckId = deckRepository.insertDeck(deckName, language, deckType.name)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        cards.forEachIndexed { index, data ->
            importCard(deckId, data, data.position.takeIf { it > 0 } ?: index, today)
        }
        return deckId
    }

    private suspend fun importCard(deckId: Long, data: CardImportData, position: Int, today: LocalDate) {
        val lastReview = data.lastReviewDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
        val nextReview = data.nextReviewDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
        val cardType = when (data.cardType.lowercase()) {
            "word" -> CardType.Word
            "text" -> CardType.Text
            else -> CardType.Sentence
        }
        val cardState = runCatching { CardState.valueOf(data.cardState) }.getOrDefault(CardState.New)
        val fsrs = CardFsrsState(
            due = data.due,
            stability = data.stability,
            difficulty = data.difficulty,
            retrievability = data.retrievability,
            easeFactor = data.easeFactor,
            averageInterval = data.averageInterval,
            lastReviewDate = lastReview,
            nextReviewDate = nextReview,
            scheduledDays = data.scheduledDays,
            elapsedDays = data.elapsedDays,
            reps = data.reps,
            lapses = data.lapses,
            state = cardState,
        )
        val card = Card(
            id = 0L,
            deckId = deckId,
            front = data.front,
            back = data.back,
            tags = data.tags.split(',').map(String::trim).filter { it.isNotBlank() },
            cardType = cardType,
            position = position,
            fsrsState = fsrs,
            createdAt = Clock.System.now(),
        )
        val cardId = cardRepository.insertCard(card)
        data.grammarTips.forEachIndexed { idx, tip ->
            grammarTipRepository.insertTip(
                GrammarTip(
                    id = 0L,
                    cardId = cardId,
                    content = tip,
                    order = idx,
                    source = GrammarTipSource.Bundled,
                    createdAt = Clock.System.now(),
                )
            )
        }
        if (cardType == CardType.Sentence) {
            data.wordLinks.forEachIndexed { idx, word ->
                sentenceWordLinkRepository.insertLink(
                    SentenceWordLink(
                        id = 0L,
                        sentenceCardId = cardId,
                        wordCardId = 0L,
                        positionInSentence = idx,
                        surfaceForm = word,
                    )
                )
            }
        }
    }
}
