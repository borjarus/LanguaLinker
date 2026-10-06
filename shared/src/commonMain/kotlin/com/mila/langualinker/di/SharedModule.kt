package com.mila.langualinker.di

import app.cash.sqldelight.db.SqlDriver
import com.mila.langualinker.data.database.createAppDatabase
import com.mila.langualinker.data.importer.BundledDeckImporter
import com.mila.langualinker.data.importer.BundledDeckSource
import com.mila.langualinker.data.importer.ComposeResourcesBundledDeckSource
import com.mila.langualinker.data.repository.AssociationRepository
import com.mila.langualinker.data.repository.CardRepository
import com.mila.langualinker.data.repository.DeckRepository
import com.mila.langualinker.data.repository.GrammarTipRepository
import com.mila.langualinker.data.repository.ReviewLogRepository
import com.mila.langualinker.data.repository.SentenceWordLinkRepository
import com.mila.langualinker.data.repository.SqlDelightAssociationRepository
import com.mila.langualinker.data.repository.SqlDelightCardRepository
import com.mila.langualinker.data.repository.SqlDelightDeckRepository
import com.mila.langualinker.data.repository.SqlDelightGrammarTipRepository
import com.mila.langualinker.data.repository.SqlDelightReviewLogRepository
import com.mila.langualinker.data.repository.SqlDelightSentenceWordLinkRepository
import com.mila.langualinker.database.AppDatabase
import com.mila.langualinker.domain.usecase.EnsureBundledDecksUseCase
import com.mila.langualinker.domain.usecase.ExportDeckUseCase
import com.mila.langualinker.domain.usecase.ImportDeckUseCase
import com.mila.langualinker.fsrs.FsrsScheduler
import org.koin.dsl.module

/**
 * Wiring shared by every platform.
 *
 * Each platform contributes its own module supplying the one binding this module cannot
 * create for itself — a [SqlDriver] — plus a persistent `AppSettingsRepository`.
 */
val sharedModule = module {
    single { createAppDatabase(get<SqlDriver>()) }

    single<DeckRepository> { SqlDelightDeckRepository(get<AppDatabase>()) }
    single<CardRepository> { SqlDelightCardRepository(get<AppDatabase>()) }
    single<GrammarTipRepository> { SqlDelightGrammarTipRepository(get<AppDatabase>()) }
    single<SentenceWordLinkRepository> { SqlDelightSentenceWordLinkRepository(get<AppDatabase>()) }
    single<AssociationRepository> { SqlDelightAssociationRepository(get<AppDatabase>()) }
    single<ReviewLogRepository> { SqlDelightReviewLogRepository(get<AppDatabase>()) }

    single<BundledDeckSource> { ComposeResourcesBundledDeckSource() }
    single { BundledDeckImporter(database = get(), source = get()) }

    single { FsrsScheduler() }

    factory { EnsureBundledDecksUseCase(importer = get(), settingsRepository = get()) }
    factory {
        ImportDeckUseCase(
            deckRepository = get(),
            cardRepository = get(),
            grammarTipRepository = get(),
            sentenceWordLinkRepository = get(),
        )
    }
    factory {
        ExportDeckUseCase(
            deckRepository = get(),
            cardRepository = get(),
            grammarTipRepository = get(),
            sentenceWordLinkRepository = get(),
        )
    }
}
