package com.mila.langualinker.domain.usecase

import com.mila.langualinker.data.importer.BundledDeckImporter
import com.mila.langualinker.data.importer.BundledImportReport
import com.mila.langualinker.data.settings.AppSettingsRepository
import kotlinx.coroutines.flow.first

/**
 * Runs the bundled import once, on first launch.
 *
 * The `bundledDecksImported` flag is only a fast path: the importer itself skips decks that
 * are already present, so a flag lost to a cleared DataStore or an interrupted first launch
 * cannot produce duplicated decks. That belt-and-braces arrangement is deliberate — a
 * duplicated bundled deck is not something the user can plausibly clean up by hand.
 *
 * The per-deck `contentVersion` in the bundled headers is what future content updates will
 * key off (adding cards to an existing deck without resetting review progress). That path
 * needs stable card GUIDs, which arrive with the Phase 3.5 schema retrofit; until then this
 * use case only handles the first-import case.
 */
class EnsureBundledDecksUseCase(
    private val importer: BundledDeckImporter,
    private val settingsRepository: AppSettingsRepository,
) {
    suspend operator fun invoke(force: Boolean = false): BundledImportReport? {
        if (!force && settingsRepository.getSettings().first().bundledDecksImported) {
            return null
        }
        val report = importer.importAll()
        settingsRepository.setBundledDecksImported(true)
        return report
    }
}
