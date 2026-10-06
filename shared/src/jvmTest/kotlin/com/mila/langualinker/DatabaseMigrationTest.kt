package com.mila.langualinker

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.mila.langualinker.database.AppDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Upgrades a fixture database from the previous released schema and asserts no data is lost.
 *
 * This is the test the plan's Definition of Done requires for every schema change. It matters
 * more than most: a migration bug is discovered by users, on their only copy of their review
 * history.
 */
class DatabaseMigrationTest {

    @Test
    fun schemaVersion_isTwo() {
        assertEquals(2L, AppDatabase.Schema.version)
    }

    @Test
    fun migrateFromV1_preservesLinksAndConvertsZeroWordCardIdToNull() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createV1Schema(driver)

        // Two links as the old importer wrote them: one genuinely resolved, one with the
        // 0 placeholder that stood for "no matching word card".
        driver.execute(
            null,
            """
            INSERT INTO sentence_word_links (id, sentence_card_id, word_card_id, position_in_sentence, surface_form)
            VALUES (1, 100, 42, 0, 'gehe'), (2, 100, 0, 1, 'Kino')
            """.trimIndent(),
            0,
        )

        AppDatabase.Schema.migrate(driver, 1, 2)
        val database = AppDatabase(driver)

        val links = database.sentence_word_linksQueries
            .getLinksBySentenceCardId(100)
            .executeAsList()

        assertEquals(2, links.size, "migration must not drop rows")

        val resolved = links.single { it.surface_form == "gehe" }
        assertEquals(42L, resolved.word_card_id)
        assertEquals("Resolved", resolved.resolution)

        val unresolved = links.single { it.surface_form == "Kino" }
        assertNull(unresolved.word_card_id, "the 0 placeholder must become a real NULL")
        assertEquals("Unresolved", unresolved.resolution)
    }

    @Test
    fun migrateFromV1_preservesRowOrderAndIds() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createV1Schema(driver)
        driver.execute(
            null,
            """
            INSERT INTO sentence_word_links (id, sentence_card_id, word_card_id, position_in_sentence, surface_form)
            VALUES (7, 5, 1, 2, 'drei'), (8, 5, 2, 0, 'eins'), (9, 5, 3, 1, 'zwei')
            """.trimIndent(),
            0,
        )

        AppDatabase.Schema.migrate(driver, 1, 2)
        val database = AppDatabase(driver)

        val links = database.sentence_word_linksQueries.getLinksBySentenceCardId(5).executeAsList()
        assertEquals(listOf("eins", "zwei", "drei"), links.map { it.surface_form })
        assertEquals(listOf(8L, 9L, 7L), links.map { it.id }, "primary keys must survive")
    }

    @Test
    fun migratedDatabase_acceptsNewInserts() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        createV1Schema(driver)
        AppDatabase.Schema.migrate(driver, 1, 2)
        val database = AppDatabase(driver)

        database.sentence_word_linksQueries.insertLink(
            sentenceCardId = 1,
            wordCardId = null,
            positionInSentence = 0,
            surfaceForm = "unbekannt",
            resolution = "Unresolved",
        )

        val links = database.sentence_word_linksQueries.getLinksBySentenceCardId(1).executeAsList()
        assertTrue(links.single().word_card_id == null)
    }

    /** The `sentence_word_links` shape as it was before migration 1. */
    private fun createV1Schema(driver: JdbcSqliteDriver) {
        driver.execute(
            null,
            """
            CREATE TABLE sentence_word_links (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                sentence_card_id INTEGER NOT NULL,
                word_card_id INTEGER NOT NULL,
                position_in_sentence INTEGER NOT NULL DEFAULT 0,
                surface_form TEXT NOT NULL
            )
            """.trimIndent(),
            0,
        )
    }
}
