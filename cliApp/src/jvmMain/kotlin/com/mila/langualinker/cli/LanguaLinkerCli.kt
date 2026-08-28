package com.mila.langualinker.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context

class LanguaLinkerCli : CliktCommand(name = "langualinker") {
    override fun help(context: Context) = """
        LanguaLinker CLI — import/export flashcard data.
        
        Supports APKG (Anki packages), NDJSON, JSON, and CSV formats.
        The app database is a standard SQLite file (langualinker.db).
    """.trimIndent()

    override fun run() = Unit
}
