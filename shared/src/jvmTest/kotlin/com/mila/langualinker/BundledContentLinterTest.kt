package com.mila.langualinker

import com.mila.langualinker.data.importer.BUNDLED_SCHEMA_VERSION
import com.mila.langualinker.data.importer.BundledCard
import com.mila.langualinker.data.importer.BundledDeckHeader
import com.mila.langualinker.data.importer.BundledDeckImporter
import com.mila.langualinker.data.importer.BundledDeckSource
import com.mila.langualinker.data.importer.BundledManifest
import com.mila.langualinker.data.importer.normalizeLemma
import com.mila.langualinker.testdb.createTestDatabase
import java.io.File
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The build-time content linter, run as a test so CI fails on broken bundled content.
 *
 * These assertions are the reason a shipped deck cannot contain a word link pointing at a
 * card nobody wrote: the plan asks for that to fail the build rather than degrade silently in
 * a user's study session.
 */
class BundledContentLinterTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val decksDir: File by lazy {
        val relative = "src/commonMain/composeResources/files/decks"
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            File(dir, relative).takeIf(File::isDirectory)?.let { return@lazy it }
            File(dir, "shared/$relative").takeIf(File::isDirectory)?.let { return@lazy it }
            dir = dir.parentFile
        }
        fail("Could not locate the bundled decks directory")
    }

    private fun readDeckFile(name: String) = File(decksDir, name).readText()

    private fun manifest(): BundledManifest =
        json.decodeFromString(readDeckFile(BundledDeckImporter.MANIFEST_FILE))

    private fun header(deckKey: String): BundledDeckHeader =
        json.decodeFromString(readDeckFile("$deckKey.json"))

    private fun cards(header: BundledDeckHeader): List<BundledCard> =
        readDeckFile(header.cardsFile)
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .map { json.decodeFromString<BundledCard>(it) }
            .toList()

    // ── Structure ─────────────────────────────────────────────────────────────

    @Test
    fun manifestAndHeaders_declareTheSupportedSchemaVersion() {
        assertEquals(BUNDLED_SCHEMA_VERSION, manifest().schemaVersion)
        manifest().decks.forEach { deckKey ->
            assertEquals(
                BUNDLED_SCHEMA_VERSION,
                header(deckKey).schemaVersion,
                "$deckKey declares an unsupported schemaVersion",
            )
        }
    }

    @Test
    fun everyDeckDeclaresBothLanguages() {
        manifest().decks.forEach { deckKey ->
            val header = header(deckKey)
            assertTrue(header.language.isNotBlank(), "$deckKey is missing language (L2)")
            assertTrue(
                header.sourceLanguage.isNotBlank(),
                "$deckKey is missing sourceLanguage (L1)",
            )
            assertTrue(header.contentVersion > 0, "$deckKey needs a contentVersion")
        }
    }

    @Test
    fun everyDeckFileReferencedByTheManifestExists() {
        manifest().decks.forEach { deckKey ->
            assertTrue(
                File(decksDir, "$deckKey.json").isFile,
                "manifest lists $deckKey but $deckKey.json is missing",
            )
            val header = header(deckKey)
            assertTrue(
                File(decksDir, header.cardsFile).isFile,
                "$deckKey references ${header.cardsFile}, which is missing",
            )
        }
    }

    @Test
    fun wordDecksAreListedBeforeTheSentenceDecksThatReferenceThem() {
        val order = manifest().decks
        order.forEachIndexed { index, deckKey ->
            val wordDeckRef = header(deckKey).wordDeckRef ?: return@forEachIndexed
            val wordDeckIndex = order.indexOf(wordDeckRef)
            assertTrue(
                wordDeckIndex in 0 until index,
                "$deckKey references '$wordDeckRef', which must appear earlier in the manifest",
            )
        }
    }

    // ── Content ───────────────────────────────────────────────────────────────

    @Test
    fun noDeckContainsADuplicateFront() {
        manifest().decks.forEach { deckKey ->
            val fronts = cards(header(deckKey)).map { it.front }
            val duplicates = fronts.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
            assertTrue(duplicates.isEmpty(), "$deckKey has duplicate fronts: $duplicates")
        }
    }

    @Test
    fun everyCardHasBothSides() {
        manifest().decks.forEach { deckKey ->
            cards(header(deckKey)).forEach { card ->
                assertTrue(card.front.isNotBlank(), "$deckKey has a card with a blank front")
                assertTrue(
                    card.back.isNotBlank(),
                    "$deckKey card '${card.front}' has a blank back",
                )
            }
        }
    }

    @Test
    fun everyWordCardRefResolvesToACardInThePairedWordDeck() {
        manifest().decks.forEach { deckKey ->
            val header = header(deckKey)
            val wordDeckRef = header.wordDeckRef ?: return@forEach
            val wordFronts = cards(header(wordDeckRef)).map { it.front }
            val exact = wordFronts.toSet()
            val normalized = wordFronts.groupBy { normalizeLemma(it) }

            cards(header).forEach { card ->
                card.wordLinks.forEach { link ->
                    val resolvable = link.wordCardRef in exact ||
                        normalized[normalizeLemma(link.wordCardRef)]?.size == 1
                    assertTrue(
                        resolvable,
                        "$deckKey card '${card.front}' references '${link.wordCardRef}', " +
                            "which has no unambiguous match in '$wordDeckRef'",
                    )
                }
            }
        }
    }

    @Test
    fun everySurfaceFormActuallyAppearsInItsSentence() {
        manifest().decks.forEach { deckKey ->
            val header = header(deckKey)
            if (header.wordDeckRef == null) return@forEach
            cards(header).forEach { card ->
                card.wordLinks.forEach { link ->
                    assertTrue(
                        card.front.contains(link.surfaceForm),
                        "$deckKey card '${card.front}' links surface form " +
                            "'${link.surfaceForm}', which does not occur in the sentence",
                    )
                }
            }
        }
    }

    @Test
    fun everySentenceCardCarriesAtLeastOneGrammarTipAndWordLinks() {
        manifest().decks.forEach { deckKey ->
            val header = header(deckKey)
            if (header.cardType != "sentence") return@forEach
            cards(header).forEach { card ->
                assertTrue(
                    card.grammarTips.isNotEmpty(),
                    "$deckKey card '${card.front}' is in a Linguistic sentence deck and needs " +
                        "at least one grammar tip",
                )
                assertTrue(
                    card.wordLinks.isNotEmpty(),
                    "$deckKey card '${card.front}' has no word links",
                )
            }
        }
    }

    @Test
    fun grammarTipsFitTheDesignedCardArea() {
        // The study screen renders tips in a fixed area; an essay breaks the layout.
        val maxChars = 240
        manifest().decks.forEach { deckKey ->
            cards(header(deckKey)).forEach { card ->
                card.grammarTips.forEach { tip ->
                    assertTrue(
                        tip.length <= maxChars,
                        "$deckKey card '${card.front}' has a ${tip.length}-character tip; " +
                            "the designed maximum is $maxChars",
                    )
                }
            }
        }
    }

    // ── Reference counts: "German has N cards" is a test, not a hope ───────────

    @Test
    fun deckSizesMatchTheirReferenceCounts() {
        val expected = mapOf(
            "de_words_a1" to 43,
            "de_sentences_a1" to 12,
            "en_words_a1" to 46,
            "en_sentences_a1" to 12,
        )
        assertEquals(
            expected.keys,
            manifest().decks.toSet(),
            "the manifest and the reference counts have drifted apart",
        )
        expected.forEach { (deckKey, count) ->
            assertEquals(count, cards(header(deckKey)).size, "$deckKey card count changed")
        }
    }

    // ── End to end against the real files ─────────────────────────────────────

    @Test
    fun theRealBundledContentImportsWithZeroUnresolvedLinks() = runTest {
        val database = createTestDatabase()
        val source = object : BundledDeckSource {
            override suspend fun readText(fileName: String): String = readDeckFile(fileName)
        }

        val report = BundledDeckImporter(database = database, source = source).importAll()

        assertEquals(4, report.importedDecks)
        assertEquals(0, report.unresolvedLinks, "shipped content must have no unresolved links")
        assertEquals(0, report.ambiguousLinks, "shipped content must have no ambiguous links")
        assertEquals(113, report.importedCards)
    }
}
