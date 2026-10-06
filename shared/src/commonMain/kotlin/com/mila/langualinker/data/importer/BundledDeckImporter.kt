package com.mila.langualinker.data.importer

import com.mila.langualinker.database.AppDatabase
import com.mila.langualinker.domain.model.CardType
import com.mila.langualinker.domain.model.DeckType
import com.mila.langualinker.domain.model.GrammarTipSource
import com.mila.langualinker.domain.model.LinkResolution
import com.mila.langualinker.fsrs.CardState
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json

/**
 * Imports the decks that ship inside the app.
 *
 * The contract this implements, in the order the failures would otherwise bite:
 *
 *  - **Ordered.** The manifest lists word decks before the sentence decks that reference
 *    them. Importing a sentence deck first would leave every word link unresolved, and
 *    nothing would report it.
 *  - **Transactional.** One transaction per deck. A crash mid-import leaves the whole deck or
 *    nothing — never a half-imported deck whose links point at cards that were rolled back.
 *  - **Idempotent.** A deck that is already present is skipped, so running the importer twice
 *    cannot duplicate content. Until deck GUIDs land in the Phase 3.5 retrofit, `(name,
 *    language)` is the natural key.
 *  - **Version-checked.** A file declaring an unknown `schemaVersion` is rejected outright
 *    rather than half-parsed by a build that does not understand it.
 *
 * Unresolved word references degrade to a non-tappable word rather than failing the import;
 * they are counted in [BundledImportReport] so a CI test can fail the build on bundled
 * content that references a word card nobody authored.
 */
class BundledDeckImporter(
    private val database: AppDatabase,
    private val source: BundledDeckSource = ComposeResourcesBundledDeckSource(),
    private val clock: Clock = Clock.System,
    private val timeZone: TimeZone = TimeZone.currentSystemDefault(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun importAll(): BundledImportReport = withContext(dispatcher) {
        val manifest = json.decodeFromString<BundledManifest>(source.readText(MANIFEST_FILE))
        require(manifest.schemaVersion == BUNDLED_SCHEMA_VERSION) {
            "Unsupported bundled manifest schemaVersion ${manifest.schemaVersion}; " +
                "this build understands $BUNDLED_SCHEMA_VERSION"
        }

        val deckIdsByKey = mutableMapOf<String, Long>()
        val results = manifest.decks.map { deckKey ->
            importDeck(deckKey, deckIdsByKey)
        }
        BundledImportReport(results)
    }

    private suspend fun importDeck(
        deckKey: String,
        deckIdsByKey: MutableMap<String, Long>,
    ): BundledDeckImportResult {
        val header = json.decodeFromString<BundledDeckHeader>(source.readText("$deckKey.json"))
        require(header.schemaVersion == BUNDLED_SCHEMA_VERSION) {
            "Unsupported schemaVersion ${header.schemaVersion} in $deckKey.json; " +
                "this build understands $BUNDLED_SCHEMA_VERSION"
        }

        val existing = database.decksQueries
            .getDeckByNameAndLanguage(header.name, header.language)
            .executeAsOneOrNull()
        if (existing != null) {
            deckIdsByKey[deckKey] = existing.id
            return BundledDeckImportResult(deckKey, existing.id, skipped = true)
        }

        val wordDeckId = header.wordDeckRef?.let { ref ->
            requireNotNull(deckIdsByKey[ref]) {
                "Deck '$deckKey' references word deck '$ref', which has not been imported yet. " +
                    "List word decks before sentence decks in ${MANIFEST_FILE}."
            }
        }
        val wordIndex = wordDeckId?.let { buildWordIndex(it) } ?: WordIndex.EMPTY

        val cards = parseCards(source.readText(header.cardsFile))
        val today = clock.todayIn(timeZone)
        val createdAt = clock.now().toEpochMilliseconds()
        val defaultCardType = header.cardType.toCardType()
        val deckType = runCatching { DeckType.valueOf(header.deckType) }
            .getOrDefault(DeckType.Linguistic)

        var unresolvedLinks = 0
        var ambiguousLinks = 0

        val deckId = database.transactionWithResult {
            database.decksQueries.insertDeck(
                name = header.name,
                language = header.language,
                type = deckType.name,
            )
            val newDeckId = database.decksQueries.lastInsertRowId().executeAsOne()

            cards.forEachIndexed { position, card ->
                val cardType = card.cardTypeOverride?.toCardType() ?: defaultCardType
                val cardId = insertCard(newDeckId, card, cardType, position, today, createdAt)

                card.grammarTips.forEachIndexed { index, tip ->
                    database.grammar_tipsQueries.insertTip(
                        cardId = cardId,
                        content = tip,
                        sortOrder = index.toLong(),
                        source = GrammarTipSource.Bundled.name,
                        createdAt = createdAt,
                    )
                }

                card.wordLinks.forEachIndexed { index, link ->
                    val resolved = wordIndex.resolve(link.wordCardRef)
                    when (resolved.resolution) {
                        LinkResolution.Unresolved -> unresolvedLinks++
                        LinkResolution.Ambiguous -> ambiguousLinks++
                        else -> Unit
                    }
                    database.sentence_word_linksQueries.insertLink(
                        sentenceCardId = cardId,
                        wordCardId = resolved.wordCardId,
                        positionInSentence = index.toLong(),
                        surfaceForm = link.surfaceForm,
                        resolution = resolved.resolution.name,
                    )
                }
            }
            newDeckId
        }

        deckIdsByKey[deckKey] = deckId
        return BundledDeckImportResult(
            deckKey = deckKey,
            deckId = deckId,
            skipped = false,
            importedCards = cards.size,
            unresolvedLinks = unresolvedLinks,
            ambiguousLinks = ambiguousLinks,
        )
    }

    private fun insertCard(
        deckId: Long,
        card: BundledCard,
        cardType: CardType,
        position: Int,
        today: LocalDate,
        createdAt: Long,
    ): Long {
        database.cardsQueries.insertCard(
            deckId = deckId,
            front = card.front,
            back = card.back,
            tags = card.tags.joinToString(","),
            cardType = cardType.name,
            position = position.toLong(),
            due = 0L,
            stability = 0.0,
            difficulty = 0.0,
            retrievability = 0.0,
            easeFactor = 2.5,
            averageInterval = 0L,
            reps = 0L,
            lapses = 0L,
            lastReviewDate = today.toString(),
            nextReviewDate = today.toString(),
            scheduledDays = 0L,
            elapsedDays = 0L,
            cardState = CardState.New.name,
            createdAt = createdAt,
        )
        return database.cardsQueries.lastInsertRowId().executeAsOne()
    }

    internal fun parseCards(ndjson: String): List<BundledCard> =
        ndjson.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .map { json.decodeFromString<BundledCard>(it) }
            .toList()

    private fun buildWordIndex(wordDeckId: Long): WordIndex {
        val rows = database.cardsQueries.getCardsByDeckId(wordDeckId).executeAsList()
        val exact = mutableMapOf<String, Long>()
        val normalized = mutableMapOf<String, MutableList<Long>>()
        rows.forEach { row ->
            if (!exact.containsKey(row.front)) exact[row.front] = row.id
            normalized.getOrPut(normalizeLemma(row.front)) { mutableListOf() } += row.id
        }
        return WordIndex(exact, normalized)
    }

    companion object {
        const val MANIFEST_FILE = "manifest.json"
    }
}

private fun String.toCardType(): CardType = when (lowercase()) {
    "word" -> CardType.Word
    "text" -> CardType.Text
    else -> CardType.Sentence
}

/**
 * Strips the case and the leading article so that `der Abend` also matches a reference
 * written as `Abend`, and English `to go` matches `go`.
 */
internal fun normalizeLemma(value: String): String {
    val lower = value.trim().lowercase()
    val articles = listOf("der ", "die ", "das ", "ein ", "eine ", "the ", "a ", "an ", "to ")
    val stripped = articles.firstOrNull { lower.startsWith(it) }
        ?.let { lower.removePrefix(it) }
        ?: lower
    return stripped.trim()
}

/**
 * Lookup table for one word deck.
 *
 * Exact matches win. A normalised match that hits more than one card is reported as
 * [LinkResolution.Ambiguous] rather than guessed at — German `sie` and `der` are the standard
 * examples, and picking one silently produces a link that is confidently wrong.
 */
internal class WordIndex(
    private val exact: Map<String, Long>,
    private val normalized: Map<String, List<Long>>,
) {
    fun resolve(wordCardRef: String): ResolvedLink {
        exact[wordCardRef]?.let { return ResolvedLink(it, LinkResolution.Resolved) }
        val candidates = normalized[normalizeLemma(wordCardRef)].orEmpty()
        return when (candidates.size) {
            0 -> ResolvedLink(null, LinkResolution.Unresolved)
            1 -> ResolvedLink(candidates.single(), LinkResolution.Resolved)
            else -> ResolvedLink(null, LinkResolution.Ambiguous)
        }
    }

    companion object {
        val EMPTY = WordIndex(emptyMap(), emptyMap())
    }
}

internal data class ResolvedLink(val wordCardId: Long?, val resolution: LinkResolution)

data class BundledDeckImportResult(
    val deckKey: String,
    val deckId: Long,
    val skipped: Boolean,
    val importedCards: Int = 0,
    val unresolvedLinks: Int = 0,
    val ambiguousLinks: Int = 0,
)

data class BundledImportReport(val decks: List<BundledDeckImportResult>) {
    val importedDecks: Int get() = decks.count { !it.skipped }
    val skippedDecks: Int get() = decks.count { it.skipped }
    val importedCards: Int get() = decks.sumOf { it.importedCards }
    val unresolvedLinks: Int get() = decks.sumOf { it.unresolvedLinks }
    val ambiguousLinks: Int get() = decks.sumOf { it.ambiguousLinks }
}
