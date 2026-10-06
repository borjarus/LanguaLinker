package com.mila.langualinker

import com.mila.langualinker.data.importer.BundledDeckImporter
import com.mila.langualinker.data.importer.BundledDeckSource
import com.mila.langualinker.database.AppDatabase
import com.mila.langualinker.domain.model.CardType
import com.mila.langualinker.domain.model.DeckType
import com.mila.langualinker.testdb.createTestDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Importer behaviour against fixture decks and a real database.
 *
 * The fixtures deliberately include the hard cases the plan calls out: an inflected surface
 * form, a contraction, a reference to a word nobody authored, and a homograph.
 */
class BundledDeckImporterTest {

    private class FakeSource(private val files: Map<String, String>) : BundledDeckSource {
        override suspend fun readText(fileName: String): String =
            files[fileName] ?: error("Missing fixture file: $fileName")
    }

    private fun importer(database: AppDatabase, files: Map<String, String>) =
        BundledDeckImporter(database = database, source = FakeSource(files))

    private val manifest = """
        {"schemaVersion": 1, "decks": ["words", "sentences"]}
    """.trimIndent()

    private val wordsHeader = """
        {"schemaVersion": 1, "contentVersion": 1, "name": "Words", "language": "de",
         "sourceLanguage": "pl", "deckType": "Linguistic", "cardType": "word",
         "cardsFile": "words.ndjson"}
    """.trimIndent()

    private val sentencesHeader = """
        {"schemaVersion": 1, "contentVersion": 1, "name": "Sentences", "language": "de",
         "sourceLanguage": "pl", "deckType": "Linguistic", "cardType": "sentence",
         "cardsFile": "sentences.ndjson", "wordDeckRef": "words"}
    """.trimIndent()

    private val words = """
        {"front": "gehen", "back": "iść", "tags": ["verb", "A1"]}
        {"front": "das Kino", "back": "kino", "tags": ["noun"]}
        {"front": "in das", "back": "do", "tags": ["phrase"]}
    """.trimIndent()

    private val sentences = """
        {"front": "Ich gehe ins Kino.", "back": "Idę do kina.", "tags": ["phrase"], "grammarTips": ["tip one", "tip two"], "wordLinks": [{"surfaceForm": "gehe", "wordCardRef": "gehen"}, {"surfaceForm": "ins", "wordCardRef": "in das"}, {"surfaceForm": "Kino", "wordCardRef": "das Kino"}]}
    """.trimIndent()

    private fun fixtures(
        manifest: String = this.manifest,
        words: String = this.words,
        sentences: String = this.sentences,
    ) = mapOf(
        "manifest.json" to manifest,
        "words.json" to wordsHeader,
        "words.ndjson" to words,
        "sentences.json" to sentencesHeader,
        "sentences.ndjson" to sentences,
    )

    // ── Deck and card import ──────────────────────────────────────────────────

    @Test
    fun importAll_createsBothDecksWithMetadata() = runTest {
        val db = createTestDatabase()
        importer(db, fixtures()).importAll()

        val decks = db.decksQueries.getAllDecks().executeAsList()
        assertEquals(listOf("Words", "Sentences"), decks.map { it.name })
        assertTrue(decks.all { it.language == "de" })
        assertTrue(decks.all { it.type == DeckType.Linguistic.name })
    }

    @Test
    fun importAll_importsCardsAsNewWithSequentialPositions() = runTest {
        val db = createTestDatabase()
        importer(db, fixtures()).importAll()

        val wordsDeckId = db.decksQueries.getDeckByNameAndLanguage("Words", "de").executeAsOne().id
        val cards = db.cardsQueries.getCardsByDeckId(wordsDeckId).executeAsList()

        assertEquals(listOf("gehen", "das Kino", "in das"), cards.map { it.front })
        assertEquals(listOf(0L, 1L, 2L), cards.map { it.position })
        assertTrue(cards.all { it.card_state == "New" })
        assertTrue(cards.all { it.reps == 0L && it.lapses == 0L })
        assertTrue(cards.all { it.card_type == CardType.Word.name })
    }

    @Test
    fun importAll_insertsGrammarTipsInOrder() = runTest {
        val db = createTestDatabase()
        importer(db, fixtures()).importAll()

        val deckId = db.decksQueries.getDeckByNameAndLanguage("Sentences", "de").executeAsOne().id
        val cardId = db.cardsQueries.getCardsByDeckId(deckId).executeAsOne().id
        val tips = db.grammar_tipsQueries.getTipsByCardId(cardId).executeAsList()

        assertEquals(listOf("tip one", "tip two"), tips.map { it.content })
        assertEquals(listOf(0L, 1L), tips.map { it.sort_order })
        assertTrue(tips.all { it.source == "Bundled" })
    }

    @Test
    fun importAll_skipsEmptyNdjsonLines() = runTest {
        val db = createTestDatabase()
        val padded = "\n{\"front\": \"eins\", \"back\": \"jeden\"}\n\n   \n"
        importer(db, fixtures(words = padded)).importAll()

        val deckId = db.decksQueries.getDeckByNameAndLanguage("Words", "de").executeAsOne().id
        assertEquals(1, db.cardsQueries.getCardsByDeckId(deckId).executeAsList().size)
    }

    // ── Word-link resolution ──────────────────────────────────────────────────

    @Test
    fun importAll_resolvesEveryWordCardRefToTheWordDeck() = runTest {
        val db = createTestDatabase()
        val report = importer(db, fixtures()).importAll()

        val sentenceDeckId = db.decksQueries.getDeckByNameAndLanguage("Sentences", "de")
            .executeAsOne().id
        val cardId = db.cardsQueries.getCardsByDeckId(sentenceDeckId).executeAsOne().id
        val links = db.sentence_word_linksQueries.getLinksBySentenceCardId(cardId).executeAsList()

        assertEquals(3, links.size)
        assertTrue(links.all { it.resolution == "Resolved" })
        assertTrue(links.all { it.word_card_id != null })
        assertEquals(0, report.unresolvedLinks)

        // The inflected "gehe" must point at the lemma card "gehen", not at itself.
        val gehe = links.single { it.surface_form == "gehe" }
        val gehenCardId = db.cardsQueries
            .getCardByDeckIdAndFront(
                db.decksQueries.getDeckByNameAndLanguage("Words", "de").executeAsOne().id,
                "gehen",
            )
            .executeAsOne().id
        assertEquals(gehenCardId, gehe.word_card_id)
    }

    @Test
    fun importAll_missingWordCardRef_degradesToUnresolvedInsteadOfCrashing() = runTest {
        val db = createTestDatabase()
        val withUnknownRef = """
            {"front": "Ich fliege.", "back": "Lecę.", "wordLinks": [{"surfaceForm": "fliege", "wordCardRef": "fliegen"}]}
        """.trimIndent()

        val report = importer(db, fixtures(sentences = withUnknownRef)).importAll()

        assertEquals(1, report.unresolvedLinks)
        val deckId = db.decksQueries.getDeckByNameAndLanguage("Sentences", "de").executeAsOne().id
        val cardId = db.cardsQueries.getCardsByDeckId(deckId).executeAsOne().id
        val link = db.sentence_word_linksQueries.getLinksBySentenceCardId(cardId).executeAsOne()

        assertNull(link.word_card_id)
        assertEquals("Unresolved", link.resolution)
    }

    @Test
    fun importAll_matchesOnNormalisedLemmaWhenExactMatchIsMissing() = runTest {
        val db = createTestDatabase()
        // The reference drops the article; the word card carries it.
        val refWithoutArticle = """
            {"front": "Das Kino ist gross.", "back": "Kino jest duże.", "wordLinks": [{"surfaceForm": "Kino", "wordCardRef": "Kino"}]}
        """.trimIndent()

        val report = importer(db, fixtures(sentences = refWithoutArticle)).importAll()

        assertEquals(0, report.unresolvedLinks)
        val deckId = db.decksQueries.getDeckByNameAndLanguage("Sentences", "de").executeAsOne().id
        val cardId = db.cardsQueries.getCardsByDeckId(deckId).executeAsOne().id
        val link = db.sentence_word_linksQueries.getLinksBySentenceCardId(cardId).executeAsOne()

        assertEquals("Resolved", link.resolution)
    }

    @Test
    fun importAll_homograph_isMarkedAmbiguousRatherThanGuessed() = runTest {
        val db = createTestDatabase()
        // Two word cards normalise to the same lemma "sie".
        val ambiguousWords = """
            {"front": "sie", "back": "ona"}
            {"front": "Sie", "back": "pan / pani"}
        """.trimIndent()
        val sentence = """
            {"front": "Wo ist sie?", "back": "Gdzie ona jest?", "wordLinks": [{"surfaceForm": "sie", "wordCardRef": "ihr"}]}
        """.trimIndent()

        val report = importer(
            db,
            fixtures(words = ambiguousWords, sentences = sentence),
        ).importAll()

        // "ihr" matches no card at all, so it stays Unresolved — the ambiguity check only
        // fires for a reference that normalises onto several cards.
        assertEquals(1, report.unresolvedLinks)

        val db2 = createTestDatabase()
        val ambiguousRef = """
            {"front": "Wo ist sie?", "back": "Gdzie ona jest?", "wordLinks": [{"surfaceForm": "sie", "wordCardRef": "SIE"}]}
        """.trimIndent()
        val report2 = importer(
            db2,
            fixtures(words = ambiguousWords, sentences = ambiguousRef),
        ).importAll()

        assertEquals(1, report2.ambiguousLinks)
        val deckId = db2.decksQueries.getDeckByNameAndLanguage("Sentences", "de").executeAsOne().id
        val cardId = db2.cardsQueries.getCardsByDeckId(deckId).executeAsOne().id
        val link = db2.sentence_word_linksQueries.getLinksBySentenceCardId(cardId).executeAsOne()
        assertNull(link.word_card_id, "an ambiguous link must not pick a card silently")
        assertEquals("Ambiguous", link.resolution)
    }

    @Test
    fun importAll_wordDeckBeforeSentenceDeck_isEnforced() = runTest {
        val db = createTestDatabase()
        val wrongOrder = """{"schemaVersion": 1, "decks": ["sentences", "words"]}"""

        val failure = assertFailsWith<IllegalArgumentException> {
            importer(db, fixtures(manifest = wrongOrder)).importAll()
        }
        assertTrue(
            failure.message!!.contains("has not been imported yet"),
            "the ordering violation must be reported, not silently produce unresolved links",
        )
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    @Test
    fun importAll_runTwice_doesNotDuplicateAnything() = runTest {
        val db = createTestDatabase()
        val imp = importer(db, fixtures())

        val first = imp.importAll()
        val second = imp.importAll()

        assertEquals(2, first.importedDecks)
        assertEquals(0, first.skippedDecks)
        assertEquals(0, second.importedDecks)
        assertEquals(2, second.skippedDecks)

        assertEquals(2, db.decksQueries.getAllDecks().executeAsList().size)

        val sentenceDeckId = db.decksQueries.getDeckByNameAndLanguage("Sentences", "de")
            .executeAsOne().id
        val cards = db.cardsQueries.getCardsByDeckId(sentenceDeckId).executeAsList()
        assertEquals(1, cards.size)
        assertEquals(
            3,
            db.sentence_word_linksQueries.getLinksBySentenceCardId(cards.single().id)
                .executeAsList().size,
        )
        assertEquals(
            2,
            db.grammar_tipsQueries.getTipsByCardId(cards.single().id).executeAsList().size,
        )
    }

    // ── Schema versioning ─────────────────────────────────────────────────────

    @Test
    fun importAll_unknownManifestSchemaVersion_isRejected() = runTest {
        val db = createTestDatabase()
        val future = """{"schemaVersion": 99, "decks": ["words"]}"""

        assertFailsWith<IllegalArgumentException> {
            importer(db, fixtures(manifest = future)).importAll()
        }
        assertTrue(db.decksQueries.getAllDecks().executeAsList().isEmpty())
    }

    @Test
    fun importAll_unknownDeckSchemaVersion_isRejected() = runTest {
        val db = createTestDatabase()
        val futureHeader = """
            {"schemaVersion": 99, "contentVersion": 1, "name": "Words", "language": "de",
             "sourceLanguage": "pl", "deckType": "Linguistic", "cardType": "word",
             "cardsFile": "words.ndjson"}
        """.trimIndent()
        val files = fixtures().toMutableMap().apply { put("words.json", futureHeader) }

        assertFailsWith<IllegalArgumentException> {
            importer(db, files).importAll()
        }
    }

    // ── Transactionality ──────────────────────────────────────────────────────

    @Test
    fun importDeck_malformedCardMidFile_leavesNoPartialDeck() = runTest {
        val db = createTestDatabase()
        // The second card is missing its required `front`, which is the shape a content
        // authoring mistake actually takes.
        val brokenSentences = """
            {"front": "Erste.", "back": "Pierwsze."}
            {"back": "Zweite ohne Vorderseite."}
        """.trimIndent()

        assertFailsWith<Exception> {
            importer(db, fixtures(sentences = brokenSentences)).importAll()
        }

        // The whole file is parsed before anything is written, so the sentence deck must be
        // absent entirely rather than present with only its first card.
        assertNull(db.decksQueries.getDeckByNameAndLanguage("Sentences", "de").executeAsOneOrNull())
    }

    @Test
    fun importAll_failureOnSecondDeck_keepsTheFirstDeckIntact() = runTest {
        val db = createTestDatabase()
        val failingSource = object : BundledDeckSource {
            private val files = fixtures()
            override suspend fun readText(fileName: String): String {
                if (fileName == "sentences.ndjson") error("simulated read failure")
                return files[fileName] ?: error("Missing fixture file: $fileName")
            }
        }

        assertFailsWith<IllegalStateException> {
            BundledDeckImporter(database = db, source = failingSource).importAll()
        }

        // One transaction per deck: the word deck is committed and usable, the sentence deck
        // never existed. A half-imported collection is the outcome this guards against.
        val wordsDeck = db.decksQueries.getDeckByNameAndLanguage("Words", "de").executeAsOne()
        assertEquals(3, db.cardsQueries.getCardsByDeckId(wordsDeck.id).executeAsList().size)
        assertNull(db.decksQueries.getDeckByNameAndLanguage("Sentences", "de").executeAsOneOrNull())
    }
}
