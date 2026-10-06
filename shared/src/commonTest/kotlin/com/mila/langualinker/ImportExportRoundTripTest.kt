package com.mila.langualinker

import com.mila.langualinker.data.importer.ExportFormat
import com.mila.langualinker.data.importer.ImportFormat
import com.mila.langualinker.domain.model.Card
import com.mila.langualinker.domain.model.CardType
import com.mila.langualinker.domain.model.DeckType
import com.mila.langualinker.domain.model.GrammarTip
import com.mila.langualinker.domain.model.GrammarTipSource
import com.mila.langualinker.domain.model.SentenceWordLink
import com.mila.langualinker.fsrs.CardFsrsState
import com.mila.langualinker.fsrs.CardState
import com.mila.langualinker.testutil.FakeRepositories
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImportExportRoundTripTest {

    // ── JSON ──────────────────────────────────────────────────────────────────

    @Test
    fun exportJsonAndReimport_preservesFsrsState() = runBlocking {
        val source = FakeRepositories()
        val sourceDeckId = source.seedDeckWithCard()

        val json = source.createExportUseCase().export(sourceDeckId, ExportFormat.JSON)

        val target = FakeRepositories()
        val importedDeckId = target.createImportUseCase().import(json, ImportFormat.JSON)
        val importedCard = target.cardRepository.getCardsByDeckId(importedDeckId).first().single()

        assertFsrs(importedCard)
        assertReviewDates(importedCard)
    }

    @Test
    fun exportJsonAndReimport_preservesDeckMetadata() = runBlocking {
        val source = FakeRepositories()
        val sourceDeckId = source.seedDeckWithCard()

        val json = source.createExportUseCase().export(sourceDeckId, ExportFormat.JSON)

        val target = FakeRepositories()
        val importedDeckId = target.createImportUseCase().import(json, ImportFormat.JSON)
        val deck = target.deckRepository.getDeckById(importedDeckId)!!

        assertEquals("German A1", deck.name)
        assertEquals("de", deck.language)
        assertEquals(DeckType.Linguistic, deck.type)
    }

    @Test
    fun exportJsonAndReimport_preservesGrammarTipsAndWordLinks() = runBlocking {
        val source = FakeRepositories()
        val sourceDeckId = source.seedDeckWithCard()

        val json = source.createExportUseCase().export(sourceDeckId, ExportFormat.JSON)

        val target = FakeRepositories()
        val importedDeckId = target.createImportUseCase().import(json, ImportFormat.JSON)
        val importedCard = target.cardRepository.getCardsByDeckId(importedDeckId).first().single()

        val tips = target.grammarTipRepository.getTipsByCardId(importedCard.id).first()
        assertEquals(listOf("Verb second position."), tips.map { it.content })
        assertEquals(GrammarTipSource.Bundled, tips.single().source)

        val links = target.sentenceWordLinkRepository.getLinksBySentenceCardId(importedCard.id)
        assertEquals(listOf("Ich"), links.map { it.surfaceForm })
    }

    @Test
    fun exportJsonAndReimport_preservesCardContentAndTags() = runBlocking {
        val source = FakeRepositories()
        val sourceDeckId = source.seedDeckWithCard()

        val json = source.createExportUseCase().export(sourceDeckId, ExportFormat.JSON)

        val target = FakeRepositories()
        val importedDeckId = target.createImportUseCase().import(json, ImportFormat.JSON)
        val importedCard = target.cardRepository.getCardsByDeckId(importedDeckId).first().single()

        assertEquals("**Ich gehe heute Abend ins Kino.**", importedCard.front)
        assertEquals("Idę dzisiaj wieczorem do kina.", importedCard.back)
        assertEquals(listOf("a1", "travel"), importedCard.tags)
        assertEquals(CardType.Sentence, importedCard.cardType)
    }

    // ── NDJSON ────────────────────────────────────────────────────────────────

    @Test
    fun exportNdjsonAndReimport_preservesFsrsState() = runBlocking {
        val source = FakeRepositories()
        val sourceDeckId = source.seedDeckWithCard()

        val ndjson = source.createExportUseCase().export(sourceDeckId, ExportFormat.NDJSON)

        val target = FakeRepositories()
        val importedDeckId = target.createImportUseCase().import(
            content = ndjson,
            format = ImportFormat.NDJSON,
            deckName = "Imported NDJSON",
            language = "de",
            deckType = DeckType.Linguistic,
        )
        val importedCard = target.cardRepository.getCardsByDeckId(importedDeckId).first().single()

        assertFsrs(importedCard)
        assertReviewDates(importedCard)
    }

    // ── CSV — explicit Phase-4 requirement ────────────────────────────────────

    @Test
    fun exportCsvAndReimport_preservesFsrsState() = runBlocking {
        val source = FakeRepositories()
        val sourceDeckId = source.seedDeckWithCard()

        val csv = source.createExportUseCase().export(sourceDeckId, ExportFormat.CSV)

        val target = FakeRepositories()
        val importedDeckId = target.createImportUseCase().import(
            content = csv,
            format = ImportFormat.CSV,
            deckName = "Imported CSV",
            language = "de",
            deckType = DeckType.Linguistic,
        )
        val importedCard = target.cardRepository.getCardsByDeckId(importedDeckId).first().single()

        assertFsrs(importedCard)
        assertReviewDates(importedCard)
        assertEquals("**Ich gehe heute Abend ins Kino.**", importedCard.front)
        assertEquals("Idę dzisiaj wieczorem do kina.", importedCard.back)
        assertEquals(listOf("a1", "travel"), importedCard.tags)
    }

    @Test
    fun exportCsvAndReimport_handlesCommasQuotesAndNewlines() = runBlocking {
        val source = FakeRepositories()
        val deckId = source.deckRepository.insertDeck("Tricky", "de", DeckType.Linguistic.name)
        source.cardRepository.insertCard(
            card(
                deckId = deckId,
                front = "Er sagte: \"Hallo, Welt!\"",
                back = "Line one\nLine two, with comma",
                position = 0,
            )
        )

        val csv = source.createExportUseCase().export(deckId, ExportFormat.CSV)

        val target = FakeRepositories()
        val importedDeckId = target.createImportUseCase().import(csv, ImportFormat.CSV)
        val importedCard = target.cardRepository.getCardsByDeckId(importedDeckId).first().single()

        assertEquals("Er sagte: \"Hallo, Welt!\"", importedCard.front)
        assertEquals("Line one\nLine two, with comma", importedCard.back)
    }

    @Test
    fun exportCsvAndReimport_preservesCardOrderForMultipleCards() = runBlocking {
        val source = FakeRepositories()
        val deckId = source.deckRepository.insertDeck("Multi", "de", DeckType.Linguistic.name)
        repeat(3) { i ->
            source.cardRepository.insertCard(
                card(deckId = deckId, front = "front-$i", back = "back-$i", position = i)
            )
        }

        val csv = source.createExportUseCase().export(deckId, ExportFormat.CSV)

        val target = FakeRepositories()
        val importedDeckId = target.createImportUseCase().import(csv, ImportFormat.CSV)
        val importedCards = target.cardRepository.getCardsByDeckId(importedDeckId).first()

        assertEquals(3, importedCards.size)
        assertEquals(listOf("front-0", "front-1", "front-2"), importedCards.map { it.front })
        assertEquals(listOf(0, 1, 2), importedCards.map { it.position })
    }

    @Test
    fun importCsv_headerOnly_createsEmptyDeck() = runBlocking {
        val target = FakeRepositories()
        val headerOnly = "front,back,tags,cardType,due,stability,difficulty,reps,lapses," +
            "easeFactor,averageInterval,scheduledDays,elapsedDays,cardState,lastReviewDate,nextReviewDate"

        val importedDeckId = target.createImportUseCase().import(headerOnly, ImportFormat.CSV)
        val importedCards = target.cardRepository.getCardsByDeckId(importedDeckId).first()

        assertTrue(importedCards.isEmpty())
    }

    // ── Shared assertions and fixtures ────────────────────────────────────────

    private fun assertFsrs(card: Card) {
        assertEquals(172800L, card.fsrsState.due)
        assertEquals(12.5f, card.fsrsState.stability)
        assertEquals(4.2f, card.fsrsState.difficulty)
        assertEquals(2.65f, card.fsrsState.easeFactor)
        assertEquals(10, card.fsrsState.averageInterval)
        assertEquals(2, card.fsrsState.scheduledDays)
        assertEquals(1, card.fsrsState.elapsedDays)
        assertEquals(7, card.fsrsState.reps)
        assertEquals(2, card.fsrsState.lapses)
        assertEquals(CardState.Review, card.fsrsState.state)
    }

    private fun assertReviewDates(card: Card) {
        assertEquals(LocalDate(2026, 7, 20), card.fsrsState.lastReviewDate)
        assertEquals(LocalDate(2026, 7, 22), card.fsrsState.nextReviewDate)
    }

    private fun card(deckId: Long, front: String, back: String, position: Int) = Card(
        id = 0L,
        deckId = deckId,
        front = front,
        back = back,
        tags = emptyList(),
        cardType = CardType.Sentence,
        position = position,
        fsrsState = CardFsrsState(
            due = 0L,
            stability = 0f,
            difficulty = 0f,
            retrievability = 0f,
            easeFactor = 2.5f,
            averageInterval = 0,
            lastReviewDate = LocalDate(2026, 1, 1),
            nextReviewDate = LocalDate(2026, 1, 1),
            scheduledDays = 0,
            elapsedDays = 0,
            reps = 0,
            lapses = 0,
            state = CardState.New,
        ),
        createdAt = Clock.System.now(),
    )

    private suspend fun FakeRepositories.seedDeckWithCard(): Long {
        val deckId = deckRepository.insertDeck("German A1", "de", DeckType.Linguistic.name)
        val cardId = cardRepository.insertCard(
            Card(
                id = 0L,
                deckId = deckId,
                front = "**Ich gehe heute Abend ins Kino.**",
                back = "Idę dzisiaj wieczorem do kina.",
                tags = listOf("a1", "travel"),
                cardType = CardType.Sentence,
                position = 0,
                fsrsState = CardFsrsState(
                    due = 172800L,
                    stability = 12.5f,
                    difficulty = 4.2f,
                    retrievability = 0.84f,
                    easeFactor = 2.65f,
                    averageInterval = 10,
                    lastReviewDate = LocalDate(2026, 7, 20),
                    nextReviewDate = LocalDate(2026, 7, 22),
                    scheduledDays = 2,
                    elapsedDays = 1,
                    reps = 7,
                    lapses = 2,
                    state = CardState.Review,
                ),
                createdAt = Clock.System.now(),
            )
        )
        grammarTipRepository.insertTip(
            GrammarTip(
                id = 0L,
                cardId = cardId,
                content = "Verb second position.",
                order = 0,
                source = GrammarTipSource.Bundled,
                createdAt = Clock.System.now(),
            )
        )
        sentenceWordLinkRepository.insertLink(
            SentenceWordLink(
                id = 0L,
                sentenceCardId = cardId,
                wordCardId = 0L,
                positionInSentence = 0,
                surfaceForm = "Ich",
            )
        )
        return deckId
    }
}
