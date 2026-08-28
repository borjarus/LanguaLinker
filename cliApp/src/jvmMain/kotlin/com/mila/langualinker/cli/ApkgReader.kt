package com.mila.langualinker.cli

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.zip.ZipFile

private val ANKI_STATE = mapOf(0 to "New", 1 to "Learning", 2 to "Review", 3 to "Relearning")

/** Extract collection.anki2 from an .apkg ZIP to a temp file and return its path. */
fun extractAnkiDb(apkgPath: String): String {
    val tmp = java.io.File.createTempFile("langualinker_anki_", ".db")
    tmp.deleteOnExit()
    ZipFile(apkgPath).use { zip ->
        val entry = zip.getEntry("collection.anki2")
            ?: error("No collection.anki2 found in $apkgPath. " +
                    "This file may use the newer Anki 2.1.50+ backend format (collection.anki21b) " +
                    "which requires the full Anki application to export in legacy format first.")
        zip.getInputStream(entry).use { input ->
            tmp.outputStream().use { output -> input.copyTo(output) }
        }
    }
    return tmp.absolutePath
}

fun importApkg(
    apkgPath: String,
    appDb: Connection,
    deckName: String,
    language: String,
    deckType: String,
): Long {
    println("Importing APKG: $apkgPath")
    val ankiDbPath = extractAnkiDb(apkgPath)

    Class.forName("org.sqlite.JDBC")
    val ankiConn = DriverManager.getConnection("jdbc:sqlite:$ankiDbPath")
    try {
        val colCrt = ankiConn.createStatement()
            .executeQuery("SELECT crt FROM col LIMIT 1")
            .let { rs -> if (rs.next()) rs.getLong("crt") else System.currentTimeMillis() / 1000L }

        // Load all notes: id → fields
        val notes = mutableMapOf<Long, Pair<String, String>>() // noteId → (front, back)
        val noteTags = mutableMapOf<Long, String>()
        ankiConn.createStatement().executeQuery("SELECT id, tags, flds FROM notes").use { rs ->
            while (rs.next()) {
                val fields = rs.getString("flds").split("\u001f")
                notes[rs.getLong("id")] = Pair(
                    fields.getOrElse(0) { "" },
                    fields.getOrElse(1) { "" },
                )
                noteTags[rs.getLong("id")] = rs.getString("tags").trim()
            }
        }

        val deckId = appDb.insertDeck(deckName, language, deckType)
        println("  Created deck id=$deckId name='$deckName'")

        var imported = 0
        var position = 0
        ankiConn.createStatement().executeQuery("SELECT * FROM cards").use { rs ->
            while (rs.next()) {
                val nid = rs.getLong("nid")
                val (front, back) = notes[nid] ?: continue
                if (front.isBlank()) continue

                val cardTypeInt = rs.getInt("type")
                val cardState = ANKI_STATE[cardTypeInt] ?: "New"
                val ankiDue = rs.getInt("due")
                val ivl = rs.getInt("ivl")
                val factor = rs.getInt("factor")
                val reps = rs.getInt("reps")
                val lapses = rs.getInt("lapses")
                val tags = noteTags[nid] ?: ""

                // For review cards, due = days since collection creation date
                val todayIso = java.time.LocalDate.now().toString()
                val nextReviewDate = if (cardTypeInt == 2 && ankiDue > 0) {
                    val colDate = java.time.Instant.ofEpochSecond(colCrt)
                        .atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    colDate.plusDays(ankiDue.toLong()).toString()
                } else {
                    todayIso
                }
                val stability = if (ivl > 0) ivl.toDouble() else 0.0
                val easeFactor = if (factor > 0) factor / 1000.0 else 2.5

                val card = CardRow(
                    front = front,
                    back = back,
                    tags = tags,
                    cardType = "Sentence",
                    position = position++,
                    due = 0L,
                    stability = stability,
                    difficulty = 0.0,
                    retrievability = 0.0,
                    easeFactor = easeFactor,
                    averageInterval = maxOf(ivl, 0),
                    reps = reps,
                    lapses = lapses,
                    lastReviewDate = todayIso,
                    nextReviewDate = nextReviewDate,
                    scheduledDays = maxOf(ivl, 0),
                    elapsedDays = 0,
                    cardState = cardState,
                )
                appDb.insertCard(deckId, card)
                imported++
            }
        }
        appDb.commit()
        println("  Imported $imported cards")
        return deckId
    } finally {
        ankiConn.close()
        File(ankiDbPath).delete()
    }
}
