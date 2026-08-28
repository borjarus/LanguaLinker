package com.mila.langualinker.cli

import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands

fun main(args: Array<String>) {
    LanguaLinkerCli()
        .subcommands(ImportCommand(), ExportCommand())
        .main(args)
}
