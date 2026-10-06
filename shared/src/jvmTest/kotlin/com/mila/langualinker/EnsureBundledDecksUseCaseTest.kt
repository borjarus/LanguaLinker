package com.mila.langualinker

import com.mila.langualinker.data.importer.BundledDeckImporter
import com.mila.langualinker.data.importer.BundledDeckSource
import com.mila.langualinker.data.settings.InMemoryAppSettingsRepository
import com.mila.langualinker.domain.usecase.EnsureBundledDecksUseCase
import com.mila.langualinker.testdb.createTestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * First-launch import.
 *
 * The important property is that the flag is an optimisation, not the safety mechanism: even
 * if it is lost, re-running must not duplicate the bundled decks.
 */
class EnsureBundledDecksUseCaseTest {

    private val files = mapOf(
        "manifest.json" to """{"schemaVersion": 1, "decks": ["words"]}""",
        "words.json" to """
            {"schemaVersion": 1, "contentVersion": 1, "name": "Words", "language": "de",
             "sourceLanguage": "pl", "deckType": "Linguistic", "cardType": "word",
             "cardsFile": "words.ndjson"}
        """.trimIndent(),
        "words.ndjson" to """{"front": "gehen", "back": "iść"}""",
    )

    private class FakeSource(
        private val files: Map<String, String>,
    ) : BundledDeckSource {
        var reads = 0
            private set

        override suspend fun readText(fileName: String): String {
            reads++
            return files[fileName] ?: error("Missing fixture file: $fileName")
        }
    }

    @Test
    fun firstLaunch_importsAndSetsTheFlag() = runTest {
        val db = createTestDatabase()
        val settings = InMemoryAppSettingsRepository()
        val useCase = EnsureBundledDecksUseCase(
            importer = BundledDeckImporter(database = db, source = FakeSource(files)),
            settingsRepository = settings,
        )

        val report = useCase()

        assertNotNull(report)
        assertEquals(1, report.importedDecks)
        assertTrue(settings.getSettings().first().bundledDecksImported)
        assertEquals(1, db.decksQueries.getAllDecks().executeAsList().size)
    }

    @Test
    fun secondLaunch_doesNoWorkAtAll() = runTest {
        val db = createTestDatabase()
        val settings = InMemoryAppSettingsRepository()
        val source = FakeSource(files)
        val useCase = EnsureBundledDecksUseCase(
            importer = BundledDeckImporter(database = db, source = source),
            settingsRepository = settings,
        )

        useCase()
        val readsAfterFirstLaunch = source.reads
        val second = useCase()

        assertNull(second, "the flag short-circuits the whole import")
        assertEquals(readsAfterFirstLaunch, source.reads, "no deck file should be re-read")
    }

    @Test
    fun lostFlag_stillDoesNotDuplicateDecks() = runTest {
        val db = createTestDatabase()
        val settings = InMemoryAppSettingsRepository()
        val useCase = EnsureBundledDecksUseCase(
            importer = BundledDeckImporter(database = db, source = FakeSource(files)),
            settingsRepository = settings,
        )

        useCase()
        // Simulate a cleared DataStore or an interrupted first launch.
        settings.setBundledDecksImported(false)
        val second = useCase()

        assertNotNull(second)
        assertEquals(0, second.importedDecks)
        assertEquals(1, second.skippedDecks)
        assertEquals(1, db.decksQueries.getAllDecks().executeAsList().size)
        assertEquals(
            1,
            db.cardsQueries
                .getCardsByDeckId(db.decksQueries.getAllDecks().executeAsList().single().id)
                .executeAsList().size,
        )
    }

    @Test
    fun force_reRunsTheImporterEvenWhenTheFlagIsSet() = runTest {
        val db = createTestDatabase()
        val settings = InMemoryAppSettingsRepository()
        val useCase = EnsureBundledDecksUseCase(
            importer = BundledDeckImporter(database = db, source = FakeSource(files)),
            settingsRepository = settings,
        )
        useCase()

        val forced = useCase(force = true)

        assertNotNull(forced)
        assertEquals(1, forced.skippedDecks)
    }
}
