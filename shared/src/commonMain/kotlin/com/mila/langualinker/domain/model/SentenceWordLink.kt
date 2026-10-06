package com.mila.langualinker.domain.model

data class SentenceWordLink(
    val id: Long,
    val sentenceCardId: Long,
    /**
     * The word-deck card this link points at, or `null` when the reference could not be
     * resolved (unknown lemma) or the target word card was deleted.
     */
    val wordCardId: Long?,
    val positionInSentence: Int,
    val surfaceForm: String,
    val resolution: LinkResolution = LinkResolution.Unresolved,
)

/**
 * How [SentenceWordLink.wordCardId] was arrived at.
 *
 * `Ambiguous` and `UserOverridden` are part of the model already so that the
 * disambiguation flow (homographs such as German "sie") does not need a migration later.
 */
enum class LinkResolution { Resolved, Unresolved, Ambiguous, UserOverridden }
