package com.mila.langualinker.data.importer

import langualinker.shared.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

/**
 * Where the bundled deck files are read from.
 *
 * Kept behind an interface so the importer can be tested against fixture strings without
 * pulling in the Compose resource loader, which needs a running resource environment.
 */
interface BundledDeckSource {
    suspend fun readText(fileName: String): String
}

class ComposeResourcesBundledDeckSource : BundledDeckSource {
    @OptIn(ExperimentalResourceApi::class)
    override suspend fun readText(fileName: String): String =
        Res.readBytes("files/decks/$fileName").decodeToString()
}
