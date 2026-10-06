package com.mila.langualinker.data.importer

import com.mila.langualinker.data.repository.CardRepository
import com.mila.langualinker.data.repository.DeckRepository
import com.mila.langualinker.data.repository.GrammarTipRepository
import com.mila.langualinker.domain.model.Card
import com.mila.langualinker.domain.model.CardType
import com.mila.langualinker.domain.model.DeckType
import com.mila.langualinker.fsrs.CardFsrsState
import com.mila.langualinker.fsrs.CardState
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * CSV import.
 *
 * Real-world CSV is not one format, so three things are detected rather than assumed:
 *
 *  - **Delimiter.** Excel in a Polish locale writes semicolons, not commas; exports from
 *    other tools use tabs. The delimiter is inferred from the header line.
 *  - **BOM.** A UTF-8 BOM written by Excel would otherwise become part of the first column
 *    name, so the first header cell never matches and every row silently loses its `front`.
 *  - **Column order.** Columns are matched by header name, so both the documented user format
 *    (`front; back; deck; tags; due`) and this app's own richer export are accepted. A file
 *    with no recognisable header names falls back to positional order.
 *
 * A `deck` column routes rows into decks by name, creating each on demand.
 */
class CsvDeckImporter(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    @Suppress("unused") private val grammarTipRepository: GrammarTipRepository,
) {
    suspend fun importFromCsv(
        deckName: String,
        language: String,
        deckType: DeckType,
        csvContent: String,
    ): Long {
        val content = csvContent.removePrefix(BOM)
        val delimiter = detectDelimiter(content)
        val rows = parseCsv(content, delimiter).filter { row -> row.any(String::isNotBlank) }
        val defaultDeckId = deckRepository.insertDeck(deckName, language, deckType.name)
        if (rows.isEmpty()) return defaultDeckId

        val header = rows.first()
        val columns = ColumnMap.from(header)
        val dataRows = if (columns.hasNamedHeader) rows.drop(1) else rows

        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val deckIdsByName = mutableMapOf<String, Long>()
        val positionsByDeck = mutableMapOf<Long, Int>()

        for (cols in dataRows) {
            val front = columns.value(cols, Column.Front)
            if (front.isBlank()) continue

            val rowDeckName = columns.value(cols, Column.Deck).takeIf(String::isNotBlank)
            val deckId = when (rowDeckName) {
                null -> defaultDeckId
                deckName -> defaultDeckId
                else -> deckIdsByName.getOrPut(rowDeckName) {
                    deckRepository.insertDeck(rowDeckName, language, deckType.name)
                }
            }
            val position = positionsByDeck.getOrElse(deckId) { 0 }
            positionsByDeck[deckId] = position + 1

            cardRepository.insertCard(
                Card(
                    id = 0L,
                    deckId = deckId,
                    front = front,
                    back = columns.value(cols, Column.Back),
                    tags = columns.value(cols, Column.Tags)
                        .split(',', ';')
                        .map(String::trim)
                        .filter { it.isNotBlank() },
                    cardType = columns.value(cols, Column.CardType).toCardTypeOrSentence(),
                    position = position,
                    fsrsState = columns.readFsrsState(cols, today),
                    createdAt = Clock.System.now(),
                )
            )
        }

        return defaultDeckId
    }

    // ── Format detection ──────────────────────────────────────────────────────

    private fun detectDelimiter(content: String): Char {
        val headerLine = content.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        // Count outside quotes only, so a quoted "Hallo, Welt" cannot outvote the real
        // delimiter in a semicolon-separated file.
        var inQuotes = false
        val counts = mutableMapOf(',' to 0, ';' to 0, '\t' to 0)
        headerLine.forEach { char ->
            when {
                char == '"' -> inQuotes = !inQuotes
                !inQuotes && counts.containsKey(char) -> counts[char] = counts.getValue(char) + 1
            }
        }
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    private fun parseCsv(content: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < content.length) {
            val char = content[index]
            when {
                char == '"' && inQuotes && index + 1 < content.length && content[index + 1] == '"' -> {
                    currentField.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == delimiter && !inQuotes -> {
                    currentRow += currentField.toString()
                    currentField.clear()
                }
                (char == '\n' || char == '\r') && !inQuotes -> {
                    if (char == '\r' && index + 1 < content.length && content[index + 1] == '\n') {
                        index++
                    }
                    currentRow += currentField.toString()
                    currentField.clear()
                    rows += currentRow.toList()
                    currentRow.clear()
                }
                else -> currentField.append(char)
            }
            index++
        }

        if (currentField.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow += currentField.toString()
            rows += currentRow.toList()
        }

        return rows
    }

    private companion object {
        const val BOM = "﻿"
    }
}

private fun String.toCardTypeOrSentence(): CardType = when (trim().lowercase()) {
    "word" -> CardType.Word
    "text" -> CardType.Text
    else -> CardType.Sentence
}

/** Columns the importer knows how to read. Anything else in the file is ignored. */
private enum class Column(val headerNames: Set<String>, val positionalIndex: Int?) {
    Front(setOf("front", "przód", "przod"), 0),
    Back(setOf("back", "tył", "tyl"), 1),
    Deck(setOf("deck", "talia"), null),
    Tags(setOf("tags", "tagi"), 2),
    CardType(setOf("cardtype", "card_type", "typ"), 3),
    Due(setOf("due", "oczekuje"), 4),
    Stability(setOf("stability", "stabilność", "stabilnosc"), 5),
    Difficulty(setOf("difficulty", "trudność", "trudnosc"), 6),
    Reps(setOf("reps", "powtórki", "powtorki"), 7),
    Lapses(setOf("lapses", "pomyłki", "pomylki"), 8),
    EaseFactor(setOf("easefactor", "ease_factor", "ease"), 9),
    AverageInterval(setOf("averageinterval", "average_interval"), 10),
    ScheduledDays(setOf("scheduleddays", "scheduled_days"), 11),
    ElapsedDays(setOf("elapseddays", "elapsed_days"), 12),
    CardState(setOf("cardstate", "card_state", "state"), 13),
    LastReviewDate(setOf("lastreviewdate", "last_review_date"), 14),
    NextReviewDate(setOf("nextreviewdate", "next_review_date"), 15),
}

private class ColumnMap(
    private val indices: Map<Column, Int>,
    val hasNamedHeader: Boolean,
) {
    fun value(row: List<String>, column: Column): String {
        val index = indices[column] ?: return ""
        return row.getOrNull(index)?.trim() ?: ""
    }

    fun readFsrsState(row: List<String>, today: LocalDate): CardFsrsState = CardFsrsState(
        due = value(row, Column.Due).toDueOrZero(),
        stability = value(row, Column.Stability).toFloatOrNull() ?: 0f,
        difficulty = value(row, Column.Difficulty).toFloatOrNull() ?: 0f,
        // Retrievability is a function of stability and elapsed time, so it is recomputed
        // rather than trusted from a file that may be months old.
        retrievability = 0f,
        easeFactor = value(row, Column.EaseFactor).toFloatOrNull() ?: 2.5f,
        averageInterval = value(row, Column.AverageInterval).toIntOrNull() ?: 0,
        lastReviewDate = value(row, Column.LastReviewDate).toLocalDateOrNull() ?: today,
        nextReviewDate = value(row, Column.NextReviewDate).toLocalDateOrNull() ?: today,
        scheduledDays = value(row, Column.ScheduledDays).toIntOrNull() ?: 0,
        elapsedDays = value(row, Column.ElapsedDays).toIntOrNull() ?: 0,
        reps = value(row, Column.Reps).toIntOrNull() ?: 0,
        lapses = value(row, Column.Lapses).toIntOrNull() ?: 0,
        state = value(row, Column.CardState)
            .let { raw -> runCatching { CardState.valueOf(raw) }.getOrDefault(CardState.New) },
    )

    companion object {
        fun from(header: List<String>): ColumnMap {
            val normalized = header.map { it.removePrefix("﻿").trim().lowercase() }
            val byName = Column.entries.mapNotNull { column ->
                val index = normalized.indexOfFirst { it in column.headerNames }
                if (index >= 0) column to index else null
            }.toMap()

            if (byName.containsKey(Column.Front)) {
                return ColumnMap(byName, hasNamedHeader = true)
            }
            // No recognisable header: treat the file as headerless and read by position.
            val positional = Column.entries
                .mapNotNull { column -> column.positionalIndex?.let { column to it } }
                .toMap()
            return ColumnMap(positional, hasNamedHeader = false)
        }
    }
}

/**
 * `due` is accepted either as a raw number (this app's own export) or as an ISO-8601 date,
 * which is what the plan asks a human-readable CSV to contain.
 */
private fun String.toDueOrZero(): Long {
    if (isBlank()) return 0L
    toLongOrNull()?.let { return it }
    return runCatching { LocalDate.parse(this).toEpochDays().toLong() }.getOrDefault(0L)
}

private fun String.toLocalDateOrNull(): LocalDate? =
    takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
