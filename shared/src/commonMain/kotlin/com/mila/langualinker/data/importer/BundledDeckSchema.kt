package com.mila.langualinker.data.importer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire format of the decks shipped in `composeResources/files/decks/`.
 *
 * This is deliberately a different contract from [CardImportData], which serves user-facing
 * import/export. Bundled content is authored by us and versioned with the app, so it carries
 * provenance the user's own CSV never will: a schema version, a content version, and the
 * licence of the source material.
 */

/** Bundled schema this build understands. A file declaring anything else is rejected. */
const val BUNDLED_SCHEMA_VERSION: Int = 1

@Serializable
data class BundledManifest(
    val schemaVersion: Int,
    /**
     * Deck keys in import order. Word decks must precede the sentence decks that reference
     * them — otherwise every `wordCardRef` would resolve against a deck that does not exist
     * yet, and the failure would be silent.
     */
    val decks: List<String>,
    val comment: String? = null,
)

@Serializable
data class BundledDeckHeader(
    val schemaVersion: Int,
    val contentVersion: Int,
    val name: String,
    /** L2 — the language being learned. BCP-47. */
    val language: String,
    /** L1 — the learner's own language, used for the `back` side and for mnemonics. */
    val sourceLanguage: String,
    val deckType: String,
    val cardType: String,
    val cardsFile: String,
    /** Deck key of the word deck this deck's `wordCardRef` values point into. */
    val wordDeckRef: String? = null,
    val license: BundledLicense = BundledLicense(),
)

@Serializable
data class BundledLicense(
    val source: String = "original",
    val attribution: String? = null,
)

@Serializable
data class BundledCard(
    val front: String,
    val back: String,
    val tags: List<String> = emptyList(),
    @SerialName("cardType") val cardTypeOverride: String? = null,
    val grammarTips: List<String> = emptyList(),
    val wordLinks: List<BundledWordLink> = emptyList(),
)

@Serializable
data class BundledWordLink(
    /** The word exactly as it appears in the sentence, so the UI can highlight it. */
    val surfaceForm: String,
    /** Dictionary form of the word — the actual join key into the word deck. */
    val wordCardRef: String,
)
