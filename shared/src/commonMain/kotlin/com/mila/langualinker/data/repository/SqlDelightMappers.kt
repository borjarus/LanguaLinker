package com.mila.langualinker.data.repository

import com.mila.langualinker.association.Association
import com.mila.langualinker.association.AssociationType
import com.mila.langualinker.database.Associations
import com.mila.langualinker.database.Cards
import com.mila.langualinker.database.Decks
import com.mila.langualinker.database.Grammar_tips
import com.mila.langualinker.database.Review_logs
import com.mila.langualinker.database.Sentence_word_links
import com.mila.langualinker.domain.model.Card
import com.mila.langualinker.domain.model.CardType
import com.mila.langualinker.domain.model.Deck
import com.mila.langualinker.domain.model.DeckType
import com.mila.langualinker.domain.model.GrammarTip
import com.mila.langualinker.domain.model.GrammarTipSource
import com.mila.langualinker.domain.model.LinkResolution
import com.mila.langualinker.domain.model.ReviewLog
import com.mila.langualinker.domain.model.SentenceWordLink
import com.mila.langualinker.fsrs.CardFsrsState
import com.mila.langualinker.fsrs.CardState
import com.mila.langualinker.fsrs.Rating
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/**
 * Row <-> domain mapping for the SQLDelight-backed repositories.
 *
 * SQLite has no dedicated types for the shapes the domain uses, so three conversions are
 * concentrated here rather than repeated per repository:
 *  - `REAL` maps to `Double`, while FSRS values are `Float`;
 *  - tag lists are stored comma-joined in a single `TEXT` column;
 *  - dates are stored as ISO-8601 strings, which sort lexicographically the same way they
 *    sort chronologically — that is what makes the `next_review_date <= :today` due query
 *    correct.
 */

internal const val TAG_SEPARATOR = ","

internal fun String.toTagList(): List<String> =
    split(TAG_SEPARATOR).map(String::trim).filter { it.isNotBlank() }

internal fun List<String>.toTagString(): String = joinToString(TAG_SEPARATOR)

internal fun LocalDate.toIsoString(): String = toString()

internal fun String?.toLocalDateOrNull(): LocalDate? =
    this?.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private inline fun <reified T : Enum<T>> String.toEnumOr(fallback: T): T =
    runCatching { enumValueOf<T>(this) }.getOrDefault(fallback)

internal fun Decks.toDomain(): Deck = Deck(
    id = id,
    name = name,
    language = language,
    type = type.toEnumOr(DeckType.Linguistic),
)

internal fun Cards.toDomain(): Card {
    // Rows written before a card's first review have no dates; the epoch is a neutral
    // placeholder that keeps such a card out of the "due today" window until scheduled.
    val fallbackDate = LocalDate.fromEpochDays(0)
    return Card(
        id = id,
        deckId = deck_id,
        front = front,
        back = back,
        tags = tags.toTagList(),
        cardType = card_type.toEnumOr(CardType.Sentence),
        position = position.toInt(),
        fsrsState = CardFsrsState(
            due = due,
            stability = stability.toFloat(),
            difficulty = difficulty.toFloat(),
            retrievability = retrievability.toFloat(),
            easeFactor = ease_factor.toFloat(),
            averageInterval = average_interval.toInt(),
            lastReviewDate = last_review_date.toLocalDateOrNull() ?: fallbackDate,
            nextReviewDate = next_review_date.toLocalDateOrNull() ?: fallbackDate,
            scheduledDays = scheduled_days.toInt(),
            elapsedDays = elapsed_days.toInt(),
            reps = reps.toInt(),
            lapses = lapses.toInt(),
            state = card_state.toEnumOr(CardState.New),
        ),
        createdAt = Instant.fromEpochMilliseconds(created_at),
    )
}

internal fun Grammar_tips.toDomain(): GrammarTip = GrammarTip(
    id = id,
    cardId = card_id,
    content = content,
    order = sort_order.toInt(),
    source = source.toEnumOr(GrammarTipSource.Bundled),
    createdAt = Instant.fromEpochMilliseconds(created_at),
)

internal fun Sentence_word_links.toDomain(): SentenceWordLink = SentenceWordLink(
    id = id,
    sentenceCardId = sentence_card_id,
    wordCardId = word_card_id,
    positionInSentence = position_in_sentence.toInt(),
    surfaceForm = surface_form,
    resolution = resolution.toEnumOr(LinkResolution.Unresolved),
)

internal fun Associations.toDomain(): Association = Association(
    id = id,
    cardId = card_id,
    content = content,
    type = type.toEnumOr(AssociationType.Generated),
    isFavorite = is_favorite == 1L,
    createdAt = Instant.fromEpochMilliseconds(created_at),
)

internal fun Review_logs.toDomain(): ReviewLog = ReviewLog(
    id = id,
    cardId = card_id,
    rating = rating.toEnumOr(Rating.Good),
    reviewedAt = Instant.fromEpochMilliseconds(reviewed_at),
    scheduledDays = scheduled_days.toInt(),
    elapsedDays = elapsed_days.toInt(),
    stability = stability.toFloat(),
    difficulty = difficulty.toFloat(),
)
