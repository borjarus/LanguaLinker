package com.mila.langualinker.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.long
import kotlinx.serialization.json.*
import java.sql.Connection

class ExportCommand : CliktCommand(name = "export") {
    override fun help(context: Context) = """
        Export a deck from the app database to JSON, NDJSON, or CSV.
        
        All FSRS review fields (due, stability, difficulty, reps, lapses) are always included.
        Front/back content is exported as-is (Markdown strings preserved).
        
        Examples:
        
          Export deck #1 as JSON:
            langualinker export --deck-id 1 --to-format json --output de_a1.json --db-path ./langualinker.db
        
          Export deck #1 as NDJSON:
            langualinker export --deck-id 1 --to-format ndjson --output de_a1.ndjson --db-path ./langualinker.db
    """.trimIndent()

    private val deckId by option("-d", "--deck-id", metavar = "INT",
        help = "ID of the deck to export")
        .long()
        .required()

    private val toFormat by option("-t", "--to-format", metavar = "FORMAT",
        help = "Output format (default: json)")
        .choice("json", "ndjson", "csv")
        .default("json")

    private val output by option("-o", "--output", metavar = "PATH",
        help = "Output file path")
        .file()
        .required()

    private val dbPath by option("--db-path", metavar = "PATH",
        help = "App SQLite database path (default: ~/.langualinker/langualinker.db)")
        .default(defaultDbPath())

    override fun run() {
        val appDb = openDb(dbPath)
        try {
            val content = exportDeckToString(appDb, deckId, toFormat)
            output.writeText(content)
            echo("Exported deck $deckId → $toFormat → ${output.absolutePath}")
        } finally {
            appDb.close()
        }
    }
}
