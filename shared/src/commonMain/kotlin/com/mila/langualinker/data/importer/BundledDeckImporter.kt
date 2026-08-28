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
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import langualinker.shared.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

class BundledDeckImporter(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val grammarTipRepository: GrammarTipRepository,
    private val sentenceWordLinkRepository: SentenceWordLinkRepository,
) {
    @OptIn(ExperimentalResourceApi::class)
    suspend fun importAll() {
        val deckJson = Res.readBytes("files/de_a1.json").decodeToString()
        val cardsNdjson = Res.readBytes("files/de_phrases_cards_a1.ndjson").decodeToString()
        importDeck(deckJson, cardsNdjson)
    }

    private suspend fun importDeck(deckJsonStr: String, cardsNdjsonStr: String) {
        val deckObj = Json.parseToJsonElement(deckJsonStr).jsonObject
        val deckName = deckObj["name"]!!.jsonPrimitive.content
        val language = deckObj["language"]!!.jsonPrimitive.content
        val typeInt = deckObj["type"]?.jsonPrimitive?.intOrNull ?: 1
        val deckType = when (typeInt) {
            2 -> DeckType.TextWithAssociations
            3 -> DeckType.Simple
            else -> DeckType.Linguistic
        }

        val deckId = deckRepository.insertDeck(deckName, language, deckType.name)
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())

        var position = 0
        for (line in cardsNdjsonStr.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val cardObj = Json.parseToJsonElement(trimmed).jsonObject

            val front = cardObj["front"]!!.jsonPrimitive.content
            val back = cardObj["back"]!!.jsonPrimitive.content
            val tags = cardObj["tags"]?.jsonPrimitive?.content ?: ""
            val cardTypeStr = cardObj["cardType"]?.jsonPrimitive?.content ?: "sentence"
            val cardType = when (cardTypeStr.lowercase()) {
                "word" -> CardType.Word
                "text" -> CardType.Text
                else -> CardType.Sentence
            }
            val grammarTips = cardObj["grammarTips"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()

            val fsrsState = CardFsrsState(
                due = 0L,
                stability = 0f,
                difficulty = 0f,
                retrievability = 0f,
                easeFactor = 2.5f,
                averageInterval = 0,
                lastReviewDate = today,
                nextReviewDate = today,
                scheduledDays = 0,
                elapsedDays = 0,
                reps = 0,
                lapses = 0,
                state = CardState.New,
            )
            val card = Card(
                id = 0L,
                deckId = deckId,
                front = front,
                back = back,
                tags = tags.split(',').map(String::trim).filter { it.isNotBlank() },
                cardType = cardType,
                position = position++,
                fsrsState = fsrsState,
                createdAt = Clock.System.now(),
            )
            val cardId = cardRepository.insertCard(card)

            grammarTips.forEachIndexed { idx, tip ->
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
                val wordLinks = cardObj["wordLinks"]?.jsonArray?.map { it.jsonPrimitive.content }
                    ?: extractWords(front)
                wordLinks.forEachIndexed { idx, word ->
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

    private fun extractWords(front: String): List<String> {
        val clean = front
            .replace(Regex("/[^/]+/"), "")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .trim()

        return clean.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .map { it.trimEnd('.', ',', '!', '?', ';', ':') }
            .filter { it.isNotBlank() }
    }
}
