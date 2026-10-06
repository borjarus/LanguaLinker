package com.mila.langualinker

import com.mila.langualinker.data.importer.ImportFormat
import com.mila.langualinker.domain.model.CardType
import com.mila.langualinker.domain.model.DeckType
import com.mila.langualinker.fsrs.CardState
import com.mila.langualinker.testutil.FakeRepositories
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * CSV import against the formats users actually produce.
 *
 * The documented user format is `front; back; deck; tags; due` — semicolon-separated, because
 * that is what Excel writes in a Polish locale. The app's own export is comma-separated with
 * many more columns. Both must import, which is why columns are matched by header name.
 */
class CsvImportTest {

    private suspend fun importCsv(csv: String, deckName: String = "Imported") =
        FakeRepositories().let { repos ->
            val deckId = repos.createImportUseCase().import(
                content = csv,
                format = ImportFormat.CSV,
                deckName = deckName,
                language = "de",
                deckType = DeckType.Linguistic,
            )
            repos to deckId
        }

    // ── Delimiter and encoding detection ──────────────────────────────────────

    @Test
    fun importsSemicolonSeparatedFile() = runTest {
        val csv = """
            front;back;deck;tags;due
            der Abend;wieczór;German A1;noun,A1;2026-07-22
            das Kino;kino;German A1;noun;2026-07-25
        """.trimIndent()

        val (repos, _) = importCsv(csv)

        val allCards = repos.cardRepository.getCardsByDeckId(1L).first() +
            repos.cardRepository.getCardsByDeckId(2L).first()
        assertEquals(listOf("der Abend", "das Kino"), allCards.map { it.front })
        assertEquals(listOf("wieczór", "kino"), allCards.map { it.back })
    }

    @Test
    fun importsTabSeparatedFile() = runTest {
        val csv = "front\tback\ttags\nder Abend\twieczór\tnoun"
        val (repos, deckId) = importCsv(csv)

        val card = repos.cardRepository.getCardsByDeckId(deckId).first().single()
        assertEquals("der Abend", card.front)
        assertEquals("wieczór", card.back)
        assertEquals(listOf("noun"), card.tags)
    }

    @Test
    fun stripsUtf8ByteOrderMarkFromTheFirstHeader() = runTest {
        // Excel writes a BOM; without stripping it the first header never matches and every
        // row silently loses its front.
        val csv = "﻿front,back\nder Abend,wieczór"
        val (repos, deckId) = importCsv(csv)

        val card = repos.cardRepository.getCardsByDeckId(deckId).first().single()
        assertEquals("der Abend", card.front)
    }

    @Test
    fun quotedDelimiterInHeaderDoesNotConfuseDetection() = runTest {
        val csv = """
            "front, side";back;tags
            der Abend;wieczór;noun
        """.trimIndent()

        val (repos, deckId) = importCsv(csv)

        // "front, side" is not a recognised name, so the file is read positionally.
        val card = repos.cardRepository.getCardsByDeckId(deckId).first().first()
        assertEquals("front, side", card.front)
    }

    // ── Column mapping ────────────────────────────────────────────────────────

    @Test
    fun mapsColumnsByNameRegardlessOfOrder() = runTest {
        val csv = """
            tags,back,cardType,front
            noun;A1,wieczór,word,der Abend
        """.trimIndent()

        val (repos, deckId) = importCsv(csv)

        val card = repos.cardRepository.getCardsByDeckId(deckId).first().single()
        assertEquals("der Abend", card.front)
        assertEquals("wieczór", card.back)
        assertEquals(CardType.Word, card.cardType)
        assertEquals(listOf("noun", "A1"), card.tags)
    }

    @Test
    fun acceptsPolishHeaderNames() = runTest {
        val csv = """
            przód,tył,tagi
            der Abend,wieczór,noun
        """.trimIndent()

        val (repos, deckId) = importCsv(csv)

        val card = repos.cardRepository.getCardsByDeckId(deckId).first().single()
        assertEquals("der Abend", card.front)
        assertEquals("wieczór", card.back)
    }

    @Test
    fun headerlessFileIsReadPositionally() = runTest {
        val csv = "der Abend,wieczór,noun\ndas Kino,kino,noun"
        val (repos, deckId) = importCsv(csv)

        val cards = repos.cardRepository.getCardsByDeckId(deckId).first()
        assertEquals(listOf("der Abend", "das Kino"), cards.map { it.front })
        assertEquals(listOf(0, 1), cards.map { it.position })
    }

    @Test
    fun unknownColumnsAreIgnored() = runTest {
        val csv = """
            front,back,notes,colour
            der Abend,wieczór,irrelevant,blue
        """.trimIndent()

        val (repos, deckId) = importCsv(csv)

        val card = repos.cardRepository.getCardsByDeckId(deckId).first().single()
        assertEquals("der Abend", card.front)
        assertEquals("wieczór", card.back)
    }

    // ── The deck column ───────────────────────────────────────────────────────

    @Test
    fun deckColumnRoutesRowsIntoSeparateDecks() = runTest {
        val csv = """
            front;back;deck
            der Abend;wieczór;Nouns
            gehen;iść;Verbs
            das Kino;kino;Nouns
        """.trimIndent()

        val (repos, _) = importCsv(csv)

        val decks = repos.deckRepository.getAllDecks().first()
        val nouns = decks.single { it.name == "Nouns" }
        val verbs = decks.single { it.name == "Verbs" }

        assertEquals(
            listOf("der Abend", "das Kino"),
            repos.cardRepository.getCardsByDeckId(nouns.id).first().map { it.front },
        )
        assertEquals(
            listOf("gehen"),
            repos.cardRepository.getCardsByDeckId(verbs.id).first().map { it.front },
        )
    }

    @Test
    fun positionsRestartPerDeck() = runTest {
        val csv = """
            front;back;deck
            eins;jeden;A
            zwei;dwa;B
            drei;trzy;A
        """.trimIndent()

        val (repos, _) = importCsv(csv)

        val deckA = repos.deckRepository.getAllDecks().first().single { it.name == "A" }
        assertEquals(
            listOf(0, 1),
            repos.cardRepository.getCardsByDeckId(deckA.id).first().map { it.position },
        )
    }

    // ── Scheduling fields ─────────────────────────────────────────────────────

    @Test
    fun readsDueAsIsoDateAndAsRawNumber() = runTest {
        val isoCsv = "front,back,due\nder Abend,wieczór,2026-07-22"
        val (isoRepos, isoDeckId) = importCsv(isoCsv)
        val isoCard = isoRepos.cardRepository.getCardsByDeckId(isoDeckId).first().single()
        assertEquals(LocalDate(2026, 7, 22).toEpochDays().toLong(), isoCard.fsrsState.due)

        val numericCsv = "front,back,due\nder Abend,wieczór,172800"
        val (numRepos, numDeckId) = importCsv(numericCsv)
        val numCard = numRepos.cardRepository.getCardsByDeckId(numDeckId).first().single()
        assertEquals(172_800L, numCard.fsrsState.due)
    }

    @Test
    fun missingSchedulingColumnsProduceANewCard() = runTest {
        val csv = "front,back\nder Abend,wieczór"
        val (repos, deckId) = importCsv(csv)

        val fsrs = repos.cardRepository.getCardsByDeckId(deckId).first().single().fsrsState
        assertEquals(CardState.New, fsrs.state)
        assertEquals(0, fsrs.reps)
        assertEquals(0, fsrs.lapses)
        assertEquals(2.5f, fsrs.easeFactor)
    }

    @Test
    fun malformedNumbersFallBackToDefaultsInsteadOfFailing() = runTest {
        val csv = "front,back,stability,reps,cardState\nder Abend,wieczór,not-a-number,oops,Nonsense"
        val (repos, deckId) = importCsv(csv)

        val fsrs = repos.cardRepository.getCardsByDeckId(deckId).first().single().fsrsState
        assertEquals(0f, fsrs.stability)
        assertEquals(0, fsrs.reps)
        assertEquals(CardState.New, fsrs.state)
    }

    // ── Robustness ────────────────────────────────────────────────────────────

    @Test
    fun blankAndIncompleteRowsAreSkipped() = runTest {
        val csv = """
            front,back
            der Abend,wieczór

            ,missing front
            das Kino,kino
        """.trimIndent()

        val (repos, deckId) = importCsv(csv)

        val cards = repos.cardRepository.getCardsByDeckId(deckId).first()
        assertEquals(listOf("der Abend", "das Kino"), cards.map { it.front })
    }

    @Test
    fun emptyFileStillCreatesTheTargetDeck() = runTest {
        val (repos, deckId) = importCsv("")

        assertTrue(repos.cardRepository.getCardsByDeckId(deckId).first().isEmpty())
        assertEquals("Imported", repos.deckRepository.getDeckById(deckId)?.name)
    }
}
