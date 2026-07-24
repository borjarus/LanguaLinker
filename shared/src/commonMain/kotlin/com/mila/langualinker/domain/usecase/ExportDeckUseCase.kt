package com.mila.langualinker.domain.usecase

import com.mila.langualinker.data.importer.CardImportData
import com.mila.langualinker.data.importer.DeckImportData
import com.mila.langualinker.data.importer.ExportFormat
import com.mila.langualinker.data.repository.CardRepository
import com.mila.langualinker.data.repository.DeckRepository
import com.mila.langualinker.data.repository.GrammarTipRepository
import com.mila.langualinker.data.repository.SentenceWordLinkRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ExportDeckUseCase(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val grammarTipRepository: GrammarTipRepository,
    private val sentenceWordLinkRepository: SentenceWordLinkRepository,
) {
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }
    private val ndjson = Json { encodeDefaults = true }

    suspend fun export(deckId: Long, format: ExportFormat): String {
        val deck = deckRepository.getDeckById(deckId) ?: error("Deck $deckId not found")
        val cards = cardRepository.getCardsByDeckId(deckId).first()

        val cardDataList = cards.map { card ->
            val tips = grammarTipRepository.getTipsByCardId(card.id).first()
            val links = sentenceWordLinkRepository.getLinksBySentenceCardId(card.id)
            CardImportData(
                front = card.front,
                back = card.back,
                tags = card.tags.joinToString(","),
                cardType = card.cardType.name,
                position = card.position,
                grammarTips = tips.map { it.content },
                wordLinks = links.map { it.surfaceForm },
                due = card.fsrsState.due,
                stability = card.fsrsState.stability,
                difficulty = card.fsrsState.difficulty,
                retrievability = card.fsrsState.retrievability,
                easeFactor = card.fsrsState.easeFactor,
                averageInterval = card.fsrsState.averageInterval,
                reps = card.fsrsState.reps,
                lapses = card.fsrsState.lapses,
                scheduledDays = card.fsrsState.scheduledDays,
                elapsedDays = card.fsrsState.elapsedDays,
                cardState = card.fsrsState.state.name,
                lastReviewDate = card.fsrsState.lastReviewDate.toString(),
                nextReviewDate = card.fsrsState.nextReviewDate.toString(),
            )
        }

        val deckData = DeckImportData(
            name = deck.name,
            language = deck.language,
            type = deck.type.name,
            cards = cardDataList,
        )

        return when (format) {
            ExportFormat.JSON -> json.encodeToString(deckData)
            ExportFormat.NDJSON -> buildString {
                cardDataList.forEach { appendLine(ndjson.encodeToString(it)) }
            }
            ExportFormat.CSV -> buildCsv(cardDataList)
            ExportFormat.APKG -> error("APKG export not supported in commonMain; use platform-specific exporter")
        }
    }

    private fun buildCsv(cards: List<CardImportData>): String {
        val header = "front,back,tags,cardType,due,stability,difficulty,reps,lapses,easeFactor,averageInterval,scheduledDays,elapsedDays,cardState,lastReviewDate,nextReviewDate"
        val rows = cards.map { c ->
            listOf(
                c.front.csvEscape(),
                c.back.csvEscape(),
                c.tags.csvEscape(),
                c.cardType.csvEscape(),
                c.due.toString(),
                c.stability.toString(),
                c.difficulty.toString(),
                c.reps.toString(),
                c.lapses.toString(),
                c.easeFactor.toString(),
                c.averageInterval.toString(),
                c.scheduledDays.toString(),
                c.elapsedDays.toString(),
                c.cardState.csvEscape(),
                (c.lastReviewDate ?: "").csvEscape(),
                (c.nextReviewDate ?: "").csvEscape(),
            ).joinToString(",")
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    private fun String.csvEscape(): String {
        return if (contains(',') || contains('"') || contains('\n') || contains('\r')) {
            "\"${replace("\"", "\"\"")}\""
        } else {
            this
        }
    }
}
