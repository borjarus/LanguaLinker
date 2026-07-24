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

class CsvDeckImporter(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    @Suppress("unused") private val grammarTipRepository: GrammarTipRepository,
) {
    suspend fun importFromCsv(deckName: String, language: String, deckType: DeckType, csvContent: String): Long {
        val deckId = deckRepository.insertDeck(deckName, language, deckType.name)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val rows = parseCsv(csvContent)
        if (rows.size < 2) return deckId

        rows.drop(1).filter { row -> row.any(String::isNotBlank) }.forEachIndexed { position, cols ->
            if (cols.size < 16) return@forEachIndexed
            val front = cols[0]
            val back = cols[1]
            val tags = cols[2]
            val cardTypeStr = cols[3]
            val due = cols[4].toLongOrNull() ?: 0L
            val stability = cols[5].toFloatOrNull() ?: 0f
            val difficulty = cols[6].toFloatOrNull() ?: 0f
            val reps = cols[7].toIntOrNull() ?: 0
            val lapses = cols[8].toIntOrNull() ?: 0
            val easeFactor = cols[9].toFloatOrNull() ?: 2.5f
            val averageInterval = cols[10].toIntOrNull() ?: 0
            val scheduledDays = cols[11].toIntOrNull() ?: 0
            val elapsedDays = cols[12].toIntOrNull() ?: 0
            val cardState = runCatching { CardState.valueOf(cols[13]) }.getOrDefault(CardState.New)
            val lastReview = cols[14].takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
            val nextReview = cols[15].takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
            val cardType = when (cardTypeStr.lowercase()) {
                "word" -> CardType.Word
                "text" -> CardType.Text
                else -> CardType.Sentence
            }
            val fsrs = CardFsrsState(
                due = due,
                stability = stability,
                difficulty = difficulty,
                retrievability = 0f,
                easeFactor = easeFactor,
                averageInterval = averageInterval,
                lastReviewDate = lastReview,
                nextReviewDate = nextReview,
                scheduledDays = scheduledDays,
                elapsedDays = elapsedDays,
                reps = reps,
                lapses = lapses,
                state = cardState,
            )
            val card = Card(
                id = 0L,
                deckId = deckId,
                front = front,
                back = back,
                tags = tags.split(',').map(String::trim).filter { it.isNotBlank() },
                cardType = cardType,
                position = position,
                fsrsState = fsrs,
                createdAt = Clock.System.now(),
            )
            cardRepository.insertCard(card)
        }

        return deckId
    }

    private fun parseCsv(content: String): List<List<String>> {
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
                char == ',' && !inQuotes -> {
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
}
