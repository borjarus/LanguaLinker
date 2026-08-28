package com.mila.langualinker.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import kotlinx.serialization.json.*
import java.io.File
import java.sql.Connection

class ImportCommand : CliktCommand(name = "import") {
    override fun help(context: Context) = """
        Import flashcard data into the app database or convert to another format.
        
        Supported input formats: apkg (Anki), ndjson, json, csv.
        Default output target: app SQLite database.
        
        Examples:
        
          Import Anki .apkg file:
            langualinker import --input sample.apkg --from-format apkg --deck-name "German A1" --language de --db-path ./langualinker.db
        
          Import NDJSON and convert to JSON (no DB write):
            langualinker import --input cards.ndjson --from-format ndjson --to-format json --output cards.json --db-path /tmp/scratch.db
    """.trimIndent()

    private val input by option("-i", "--input", metavar = "FILE", help = "Input file path")
        .file(mustExist = true, canBeDir = false)
        .required()

    private val fromFormat by option("-f", "--from-format", metavar = "FORMAT",
        help = "Input format (default: apkg)")
        .choice("apkg", "ndjson", "json", "csv")
        .default("apkg")

    private val toFormat by option("-t", "--to-format", metavar = "FORMAT",
        help = "Output format — db writes to SQLite, others write to --output (default: db)")
        .choice("db", "json", "ndjson", "csv")
        .default("db")

    private val output by option("-o", "--output", metavar = "PATH",
        help = "Output file path (required when --to-format is not db)")
        .file()

    private val dbPath by option("--db-path", metavar = "PATH",
        help = "App SQLite database path (default: ~/.langualinker/langualinker.db)")
        .default(defaultDbPath())

    private val deckName by option("--deck-name", metavar = "NAME",
        help = "Deck name — used for apkg/ndjson/csv imports (default: \"Imported Deck\")")
        .default("Imported Deck")

    private val language by option("--language", metavar = "CODE",
        help = "Language code, e.g. de, en (default: unknown)")
        .default("unknown")

    private val deckType by option("--deck-type", metavar = "TYPE",
        help = "Deck type (default: Linguistic)")
        .choice("Linguistic", "TextWithAssociations", "Simple")
        .default("Linguistic")

    override fun run() {
        if (toFormat != "db" && output == null) {
            echo("Error: --output is required when --to-format is not 'db'", err = true)
            throw com.github.ajalt.clikt.core.ProgramResult(1)
        }

        val appDb = openDb(dbPath)
        try {
            val deckId = when (fromFormat) {
                "apkg" -> importApkg(input.absolutePath, appDb, deckName, language, deckType)
                "ndjson" -> importNdjson(input.readText(), appDb, deckName, language, deckType)
                "json" -> importJson(input.readText(), appDb)
                "csv" -> importCsv(input.readText(), appDb, deckName, language, deckType)
                else -> error("unreachable")
            }

            if (toFormat != "db") {
                echo("Converting deck $deckId → $toFormat")
                val content = exportDeckToString(appDb, deckId, toFormat)
                output!!.writeText(content)
                echo("  Written → ${output!!.absolutePath}")
            }
        } finally {
            appDb.close()
        }
    }

    // ─── Format-specific importers ────────────────────────────────────────────

    private fun importNdjson(
        content: String,
        appDb: Connection,
        deckName: String,
        language: String,
        deckType: String,
    ): Long {
        echo("Importing NDJSON")
        val deckId = appDb.insertDeck(deckName, language, deckType)
        val json = Json { ignoreUnknownKeys = true }
        var position = 0
        content.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val obj = json.parseToJsonElement(line).jsonObject
            val cardId = appDb.insertCard(deckId, cardRowFromJson(obj, position++))
            obj["grammarTips"]?.jsonArray?.forEachIndexed { idx, tip ->
                appDb.insertGrammarTip(cardId, tip.jsonPrimitive.content, idx)
            }
        }
        appDb.commit()
        echo("  Imported $position cards into deck id=$deckId")
        return deckId
    }

    private fun importJson(content: String, appDb: Connection): Long {
        echo("Importing JSON")
        val json = Json { ignoreUnknownKeys = true }
        val root = json.parseToJsonElement(content).jsonObject
        val name = root["name"]?.jsonPrimitive?.content ?: "Imported Deck"
        val lang = root["language"]?.jsonPrimitive?.content ?: "unknown"
        val type = root["type"]?.jsonPrimitive?.content ?: "Linguistic"
        val deckId = appDb.insertDeck(name, lang, type)
        var position = 0
        root["cards"]?.jsonArray?.forEach { elem ->
            val obj = elem.jsonObject
            val cardId = appDb.insertCard(deckId, cardRowFromJson(obj, position++))
            obj["grammarTips"]?.jsonArray?.forEachIndexed { idx, tip ->
                appDb.insertGrammarTip(cardId, tip.jsonPrimitive.content, idx)
            }
        }
        appDb.commit()
        echo("  Imported $position cards into deck '$name' (id=$deckId)")
        return deckId
    }

    private fun importCsv(
        content: String,
        appDb: Connection,
        deckName: String,
        language: String,
        deckType: String,
    ): Long {
        echo("Importing CSV")
        val deckId = appDb.insertDeck(deckName, language, deckType)
        val lines = content.lines()
        if (lines.size < 2) { appDb.commit(); return deckId }
        val header = parseCsvRow(lines[0])
        var position = 0
        lines.drop(1).filter { it.isNotBlank() }.forEach { line ->
            val cols = parseCsvRow(line)
            val row = header.zip(cols).toMap()
            val card = CardRow(
                front = row["front"] ?: return@forEach,
                back = row["back"] ?: "",
                tags = row["tags"] ?: "",
                cardType = row["cardType"] ?: "Sentence",
                position = position++,
                due = row["due"]?.toLongOrNull() ?: 0L,
                stability = row["stability"]?.toDoubleOrNull() ?: 0.0,
                difficulty = row["difficulty"]?.toDoubleOrNull() ?: 0.0,
                retrievability = 0.0,
                easeFactor = row["easeFactor"]?.toDoubleOrNull() ?: 2.5,
                averageInterval = row["averageInterval"]?.toIntOrNull() ?: 0,
                reps = row["reps"]?.toIntOrNull() ?: 0,
                lapses = row["lapses"]?.toIntOrNull() ?: 0,
                lastReviewDate = row["lastReviewDate"]?.takeIf { it.isNotBlank() },
                nextReviewDate = row["nextReviewDate"]?.takeIf { it.isNotBlank() },
                scheduledDays = row["scheduledDays"]?.toIntOrNull() ?: 0,
                elapsedDays = row["elapsedDays"]?.toIntOrNull() ?: 0,
                cardState = row["cardState"] ?: "New",
            )
            appDb.insertCard(deckId, card)
        }
        appDb.commit()
        echo("  Imported $position cards into deck id=$deckId")
        return deckId
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun cardRowFromJson(obj: JsonObject, position: Int): CardRow {
        fun str(key: String) = obj[key]?.jsonPrimitive?.content ?: ""
        fun dbl(key: String, default: Double = 0.0) = obj[key]?.jsonPrimitive?.doubleOrNull ?: default
        fun int(key: String, default: Int = 0) = obj[key]?.jsonPrimitive?.intOrNull ?: default
        fun long(key: String, default: Long = 0L) = obj[key]?.jsonPrimitive?.longOrNull ?: default
        return CardRow(
            front = str("front"),
            back = str("back"),
            tags = str("tags"),
            cardType = obj["cardType"]?.jsonPrimitive?.content
                ?.replaceFirstChar { it.uppercase() } ?: "Sentence",
            position = position,
            due = long("due"),
            stability = dbl("stability"),
            difficulty = dbl("difficulty"),
            retrievability = dbl("retrievability"),
            easeFactor = dbl("easeFactor", 2.5),
            averageInterval = int("averageInterval"),
            reps = int("reps"),
            lapses = int("lapses"),
            lastReviewDate = obj["lastReviewDate"]?.jsonPrimitive?.contentOrNull,
            nextReviewDate = obj["nextReviewDate"]?.jsonPrimitive?.contentOrNull,
            scheduledDays = int("scheduledDays"),
            elapsedDays = int("elapsedDays"),
            cardState = obj["cardState"]?.jsonPrimitive?.content ?: "New",
        )
    }

    private fun parseCsvRow(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            when {
                line[i] == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"'); i++
                }
                line[i] == '"' -> inQuotes = !inQuotes
                line[i] == ',' && !inQuotes -> { result.add(current.toString()); current.clear() }
                else -> current.append(line[i])
            }
            i++
        }
        result.add(current.toString())
        return result
    }
}

fun exportDeckToString(appDb: Connection, deckId: Long, format: String): String {
    val deckRow = run {
        val rs = appDb.prepareStatement("SELECT * FROM decks WHERE id = ?")
            .apply { setLong(1, deckId) }.executeQuery()
        check(rs.next()) { "Deck id=$deckId not found" }
        mapOf(
            "name" to rs.getString("name"),
            "language" to rs.getString("language"),
            "type" to rs.getString("type"),
        )
    }

    val cards = appDb.getCardsAsMaps(deckId)
    println("  Deck '${deckRow["name"]}' — ${cards.size} cards")

    val cardJsonObjects = cards.map { card ->
        val id = card["id"] as Long
        val tips = appDb.getGrammarTips(id)
        buildJsonObject {
            put("front", card["front"] as? String ?: "")
            put("back", card["back"] as? String ?: "")
            put("tags", card["tags"] as? String ?: "")
            put("cardType", card["card_type"] as? String ?: "Sentence")
            put("due", card["due"] as? Long ?: 0L)
            put("stability", card["stability"] as? Double ?: 0.0)
            put("difficulty", card["difficulty"] as? Double ?: 0.0)
            put("retrievability", card["retrievability"] as? Double ?: 0.0)
            put("easeFactor", card["ease_factor"] as? Double ?: 2.5)
            put("averageInterval", card["average_interval"] as? Int ?: 0)
            put("reps", card["reps"] as? Int ?: 0)
            put("lapses", card["lapses"] as? Int ?: 0)
            put("scheduledDays", card["scheduled_days"] as? Int ?: 0)
            put("elapsedDays", card["elapsed_days"] as? Int ?: 0)
            put("cardState", card["card_state"] as? String ?: "New")
            put("lastReviewDate", card["last_review_date"] as? String)
            put("nextReviewDate", card["next_review_date"] as? String)
            putJsonArray("grammarTips") { tips.forEach { add(it) } }
        }
    }

    return when (format) {
        "json" -> Json { prettyPrint = true }.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("name", deckRow["name"]!!)
                put("language", deckRow["language"]!!)
                put("type", deckRow["type"]!!)
                putJsonArray("cards") { cardJsonObjects.forEach { add(it) } }
            }
        )
        "ndjson" -> cardJsonObjects.joinToString("\n") {
            Json.encodeToString(JsonObject.serializer(), it)
        }
        "csv" -> buildCsv(cardJsonObjects)
        else -> error("Unknown export format: $format")
    }
}

private fun buildCsv(cards: List<JsonObject>): String {
    val header = listOf(
        "front", "back", "tags", "cardType", "due", "stability", "difficulty",
        "reps", "lapses", "easeFactor", "averageInterval", "scheduledDays",
        "elapsedDays", "cardState", "lastReviewDate", "nextReviewDate"
    )
    val rows = cards.map { c ->
        header.map { key ->
            val raw = when (val v = c[key]) {
                is JsonPrimitive -> v.contentOrNull ?: v.content
                null -> ""
                else -> v.toString()
            }
            raw.csvEscape()
        }.joinToString(",")
    }
    return (listOf(header.joinToString(",")) + rows).joinToString("\n")
}

private fun String.csvEscape(): String =
    if (contains(',') || contains('"') || contains('\n')) "\"${replace("\"", "\"\"")}\"" else this
