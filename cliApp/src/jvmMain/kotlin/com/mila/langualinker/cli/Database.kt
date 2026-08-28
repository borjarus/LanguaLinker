package com.mila.langualinker.cli

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

private val SCHEMA = """
    CREATE TABLE IF NOT EXISTS decks (
        id      INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
        name    TEXT    NOT NULL,
        language TEXT   NOT NULL,
        type    TEXT    NOT NULL DEFAULT 'Linguistic'
    );
    CREATE TABLE IF NOT EXISTS cards (
        id               INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
        deck_id          INTEGER NOT NULL,
        front            TEXT    NOT NULL,
        back             TEXT    NOT NULL,
        tags             TEXT    NOT NULL DEFAULT '',
        card_type        TEXT    NOT NULL DEFAULT 'Sentence',
        position         INTEGER NOT NULL DEFAULT 0,
        due              INTEGER NOT NULL DEFAULT 0,
        stability        REAL    NOT NULL DEFAULT 0.0,
        difficulty       REAL    NOT NULL DEFAULT 0.0,
        retrievability   REAL    NOT NULL DEFAULT 0.0,
        ease_factor      REAL    NOT NULL DEFAULT 2.5,
        average_interval INTEGER NOT NULL DEFAULT 0,
        reps             INTEGER NOT NULL DEFAULT 0,
        lapses           INTEGER NOT NULL DEFAULT 0,
        last_review_date TEXT,
        next_review_date TEXT,
        scheduled_days   INTEGER NOT NULL DEFAULT 0,
        elapsed_days     INTEGER NOT NULL DEFAULT 0,
        card_state       TEXT    NOT NULL DEFAULT 'New',
        created_at       INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS grammar_tips (
        id         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
        card_id    INTEGER NOT NULL,
        content    TEXT    NOT NULL,
        sort_order INTEGER NOT NULL DEFAULT 0,
        source     TEXT    NOT NULL DEFAULT 'Bundled',
        created_at INTEGER NOT NULL
    );
    CREATE TABLE IF NOT EXISTS sentence_word_links (
        id                  INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
        sentence_card_id    INTEGER NOT NULL,
        word_card_id        INTEGER NOT NULL,
        position_in_sentence INTEGER NOT NULL DEFAULT 0,
        surface_form        TEXT    NOT NULL
    );
""".trimIndent()

fun defaultDbPath(): String =
    "${System.getProperty("user.home")}/.langualinker/langualinker.db"

fun openDb(path: String): Connection {
    File(path).parentFile?.mkdirs()
    Class.forName("org.sqlite.JDBC")
    val conn = DriverManager.getConnection("jdbc:sqlite:$path")
    conn.autoCommit = false
    val stmt = conn.createStatement()
    stmt.execute("PRAGMA foreign_keys = ON")
    // Apply schema (all statements are IF NOT EXISTS — idempotent)
    SCHEMA.split(";").map { it.trim() }.filter { it.isNotBlank() }.forEach { stmt.execute(it) }
    conn.commit()
    return conn
}

// ─── Insert helpers ───────────────────────────────────────────────────────────

fun Connection.insertDeck(name: String, language: String, type: String): Long {
    prepareStatement("INSERT INTO decks (name, language, type) VALUES (?, ?, ?)").use { ps ->
        ps.setString(1, name)
        ps.setString(2, language)
        ps.setString(3, type)
        ps.executeUpdate()
    }
    return lastInsertRowId()
}

data class CardRow(
    val front: String,
    val back: String,
    val tags: String = "",
    val cardType: String = "Sentence",
    val position: Int = 0,
    val due: Long = 0L,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val retrievability: Double = 0.0,
    val easeFactor: Double = 2.5,
    val averageInterval: Int = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastReviewDate: String? = null,
    val nextReviewDate: String? = null,
    val scheduledDays: Int = 0,
    val elapsedDays: Int = 0,
    val cardState: String = "New",
)

fun Connection.insertCard(deckId: Long, card: CardRow): Long {
    val nowMs = System.currentTimeMillis()
    prepareStatement(
        """INSERT INTO cards (deck_id, front, back, tags, card_type, position,
            due, stability, difficulty, retrievability, ease_factor, average_interval,
            reps, lapses, last_review_date, next_review_date,
            scheduled_days, elapsed_days, card_state, created_at)
           VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"""
    ).use { ps ->
        ps.setLong(1, deckId)
        ps.setString(2, card.front)
        ps.setString(3, card.back)
        ps.setString(4, card.tags)
        ps.setString(5, card.cardType)
        ps.setInt(6, card.position)
        ps.setLong(7, card.due)
        ps.setDouble(8, card.stability)
        ps.setDouble(9, card.difficulty)
        ps.setDouble(10, card.retrievability)
        ps.setDouble(11, card.easeFactor)
        ps.setInt(12, card.averageInterval)
        ps.setInt(13, card.reps)
        ps.setInt(14, card.lapses)
        ps.setString(15, card.lastReviewDate)
        ps.setString(16, card.nextReviewDate)
        ps.setInt(17, card.scheduledDays)
        ps.setInt(18, card.elapsedDays)
        ps.setString(19, card.cardState)
        ps.setLong(20, nowMs)
        ps.executeUpdate()
    }
    return lastInsertRowId()
}

fun Connection.insertGrammarTip(cardId: Long, content: String, sortOrder: Int) {
    val nowMs = System.currentTimeMillis()
    prepareStatement(
        "INSERT INTO grammar_tips (card_id, content, sort_order, source, created_at) VALUES (?,?,?,'Bundled',?)"
    ).use { ps ->
        ps.setLong(1, cardId)
        ps.setString(2, content)
        ps.setInt(3, sortOrder)
        ps.setLong(4, nowMs)
        ps.executeUpdate()
    }
}

fun Connection.lastInsertRowId(): Long =
    createStatement().executeQuery("SELECT last_insert_rowid()").use { it.getLong(1) }

// ─── Query helpers ────────────────────────────────────────────────────────────

fun Connection.getCardsAsMaps(deckId: Long): List<Map<String, Any?>> {
    val rows = mutableListOf<Map<String, Any?>>()
    prepareStatement("SELECT * FROM cards WHERE deck_id = ? ORDER BY position").use { ps ->
        ps.setLong(1, deckId)
        val rs = ps.executeQuery()
        while (rs.next()) {
            rows.add(
                mapOf(
                    "id" to rs.getLong("id"),
                    "front" to rs.getString("front"),
                    "back" to rs.getString("back"),
                    "tags" to rs.getString("tags"),
                    "card_type" to rs.getString("card_type"),
                    "due" to rs.getLong("due"),
                    "stability" to rs.getDouble("stability"),
                    "difficulty" to rs.getDouble("difficulty"),
                    "retrievability" to rs.getDouble("retrievability"),
                    "ease_factor" to rs.getDouble("ease_factor"),
                    "average_interval" to rs.getInt("average_interval"),
                    "reps" to rs.getInt("reps"),
                    "lapses" to rs.getInt("lapses"),
                    "last_review_date" to rs.getString("last_review_date"),
                    "next_review_date" to rs.getString("next_review_date"),
                    "scheduled_days" to rs.getInt("scheduled_days"),
                    "elapsed_days" to rs.getInt("elapsed_days"),
                    "card_state" to rs.getString("card_state"),
                )
            )
        }
    }
    return rows
}

fun Connection.getGrammarTips(cardId: Long): List<String> {
    val tips = mutableListOf<String>()
    prepareStatement("SELECT content FROM grammar_tips WHERE card_id = ? ORDER BY sort_order").use { ps ->
        ps.setLong(1, cardId)
        val rs = ps.executeQuery()
        while (rs.next()) tips.add(rs.getString("content"))
    }
    return tips
}

