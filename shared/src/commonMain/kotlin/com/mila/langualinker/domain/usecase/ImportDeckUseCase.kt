package com.mila.langualinker.domain.usecase

import com.mila.langualinker.data.importer.CsvDeckImporter
import com.mila.langualinker.data.importer.DeckImportData
import com.mila.langualinker.data.importer.ImportFormat
import com.mila.langualinker.data.importer.NdjsonDeckImporter
import com.mila.langualinker.data.repository.CardRepository
import com.mila.langualinker.data.repository.DeckRepository
import com.mila.langualinker.data.repository.GrammarTipRepository
import com.mila.langualinker.data.repository.SentenceWordLinkRepository
import com.mila.langualinker.domain.model.DeckType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ImportDeckUseCase(
    private val deckRepository: DeckRepository,
    private val cardRepository: CardRepository,
    private val grammarTipRepository: GrammarTipRepository,
    private val sentenceWordLinkRepository: SentenceWordLinkRepository,
) {
    private val ndjsonDeckImporter by lazy {
        NdjsonDeckImporter(
            deckRepository = deckRepository,
            cardRepository = cardRepository,
            grammarTipRepository = grammarTipRepository,
            sentenceWordLinkRepository = sentenceWordLinkRepository,
        )
    }

    suspend fun import(
        content: String,
        format: ImportFormat,
        deckName: String = "Imported Deck",
        language: String = "unknown",
        deckType: DeckType = DeckType.Linguistic,
    ): Long {
        return when (format) {
            ImportFormat.NDJSON -> ndjsonDeckImporter.importFromNdjson(deckName, language, deckType, content)
            ImportFormat.JSON -> {
                val deckData = Json { ignoreUnknownKeys = true }.decodeFromString<DeckImportData>(content)
                ndjsonDeckImporter.importFromCardList(
                    deckName = deckData.name,
                    language = deckData.language,
                    deckType = runCatching { DeckType.valueOf(deckData.type) }.getOrDefault(DeckType.Linguistic),
                    cards = deckData.cards,
                )
            }
            ImportFormat.CSV -> CsvDeckImporter(
                deckRepository = deckRepository,
                cardRepository = cardRepository,
                grammarTipRepository = grammarTipRepository,
            ).importFromCsv(deckName, language, deckType, content)
            ImportFormat.APKG -> error("APKG import requires platform-specific importer; use ApkgDeckImporter directly")
        }
    }
}
