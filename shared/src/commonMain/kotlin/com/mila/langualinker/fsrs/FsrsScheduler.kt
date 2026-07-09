package com.mila.langualinker.fsrs

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * FSRS-6 review scheduler.
 *
 * Given a [CardFsrsState] and a [Rating], produces the next [CardFsrsState]
 * with updated S, D, R, dates, and card state.
 *
 * State-machine transitions:
 *
 *   New  ─────────────────────────────────────────────────────────────────┐
 *         Again/Hard/Good → Learning (sched 0/1/1 day)                   │
 *         Easy            → Review   (sched = interval(S, DR))           │
 *                                                                         │
 *   Learning ────────────────────────────────────────────────────────────┤
 *         Again  → Learning  (sched = 0, short-term S)                   │
 *         Hard   → Learning  (sched = 1, short-term S)                   │
 *         Good   → Review    (sched = max(1, interval(S, DR)), s-t S)    │
 *         Easy   → Review    (sched = max(1, interval(S, DR)), s-t S)    │
 *                                                                         │
 *   Review ──────────────────────────────────────────────────────────────┤
 *         Again  → Relearning (sched = 0, forget-S, D updated)           │
 *         Hard/Good/Easy → Review (sched = interval(S',DR), recall-S)   │
 *                                                                         │
 *   Relearning ──────────────────────────────────────────────────────────┘
 *         Again  → Relearning (sched = 0, short-term S)
 *         Hard   → Relearning (sched = 1, short-term S)
 *         Good   → Review     (sched = max(1, interval(S, DR)), s-t S)
 *         Easy   → Review     (sched = max(1, interval(S, DR)), s-t S)
 *
 * Short-term stability is used whenever the card is in Learning/Relearning,
 * regardless of elapsed days (these cards graduate quickly).
 */
class FsrsScheduler(params: FsrsParameters = FsrsParameters()) {

    val algorithm = FsrsAlgorithm(params)

    /**
     * Schedule the next review.
     *
     * @param state  Current FSRS state. For brand-new cards use [CardFsrsState.new].
     * @param rating User's rating for this review.
     * @param reviewDate Today's date (date of the review being recorded).
     */
    fun review(
        state: CardFsrsState,
        rating: Rating,
        reviewDate: LocalDate,
    ): CardFsrsState {
        val elapsedDays = when (state.state) {
            CardState.New -> 0
            else -> state.lastReviewDate.daysUntil(reviewDate).coerceAtLeast(0)
        }

        return when (state.state) {
            CardState.New -> reviewNew(state, rating, reviewDate)
            CardState.Learning -> reviewLearning(state, rating, reviewDate, elapsedDays)
            CardState.Review -> reviewReview(state, rating, reviewDate, elapsedDays)
            CardState.Relearning -> reviewRelearning(state, rating, reviewDate, elapsedDays)
        }
    }

    // ── State handlers ────────────────────────────────────────────────────────

    private fun reviewNew(
        state: CardFsrsState,
        rating: Rating,
        today: LocalDate,
    ): CardFsrsState {
        val s = algorithm.initialStability(rating)
        val d = algorithm.initialDifficulty(rating)
        val (nextState, schedDays) = when (rating) {
            Rating.Again -> CardState.Learning to 0
            Rating.Hard  -> CardState.Learning to 1
            Rating.Good  -> CardState.Learning to 1
            Rating.Easy  -> CardState.Review   to algorithm.nextInterval(s)
        }
        val r = algorithm.retrievability(schedDays, s)
        val nextReviewDate = today + schedDays
        return state.copy(
            due             = nextReviewDate.toEpochDays().toLong(),
            stability      = s,
            difficulty     = d,
            retrievability = r,
            easeFactor     = state.easeFactor,
            averageInterval = schedDays,
            lastReviewDate = today,
            nextReviewDate = nextReviewDate,
            scheduledDays  = schedDays,
            elapsedDays    = 0,
            reps           = state.reps + 1,
            lapses         = state.lapses,
            state          = nextState,
        )
    }

    private fun reviewLearning(
        state: CardFsrsState,
        rating: Rating,
        today: LocalDate,
        elapsedDays: Int,
    ): CardFsrsState {
        val s = algorithm.shortTermStability(state.stability, rating)
        val d = state.difficulty  // D doesn't change in Learning
        val (nextState, schedDays) = when (rating) {
            Rating.Again -> CardState.Learning  to 0
            Rating.Hard  -> CardState.Learning  to 1
            Rating.Good  -> CardState.Review    to algorithm.nextInterval(s).coerceAtLeast(1)
            Rating.Easy  -> CardState.Review    to algorithm.nextInterval(s).coerceAtLeast(1)
        }
        val r = algorithm.retrievability(schedDays, s)
        val nextReviewDate = today + schedDays
        return state.copy(
            due             = nextReviewDate.toEpochDays().toLong(),
            stability      = s,
            difficulty     = d,
            retrievability = r,
            easeFactor     = state.easeFactor,
            averageInterval = schedDays,
            lastReviewDate = today,
            nextReviewDate = nextReviewDate,
            scheduledDays  = schedDays,
            elapsedDays    = elapsedDays,
            reps           = state.reps + 1,
            lapses         = state.lapses,
            state          = nextState,
        )
    }

    private fun reviewReview(
        state: CardFsrsState,
        rating: Rating,
        today: LocalDate,
        elapsedDays: Int,
    ): CardFsrsState {
        val r = algorithm.retrievability(elapsedDays, state.stability)

        val (newS, newD, nextState, lapsesDelta) = when (rating) {
            Rating.Again -> {
                val sf = algorithm.stabilityAfterForgetting(state.difficulty, state.stability, r)
                val df = algorithm.nextDifficulty(state.difficulty, rating)
                Quad(sf, df, CardState.Relearning, 1)
            }
            else -> {
                val sr = algorithm.stabilityAfterRecall(state.difficulty, state.stability, r, rating)
                val dr = algorithm.nextDifficulty(state.difficulty, rating)
                Quad(sr, dr, CardState.Review, 0)
            }
        }

        val schedDays = when (nextState) {
            CardState.Relearning -> 0
            else -> algorithm.nextInterval(newS)
        }
        val newR = algorithm.retrievability(schedDays, newS)
        val nextReviewDate = today + schedDays

        return state.copy(
            due             = nextReviewDate.toEpochDays().toLong(),
            stability      = newS,
            difficulty     = newD,
            retrievability = newR,
            easeFactor     = state.easeFactor,
            averageInterval = schedDays,
            lastReviewDate = today,
            nextReviewDate = nextReviewDate,
            scheduledDays  = schedDays,
            elapsedDays    = elapsedDays,
            reps           = state.reps + 1,
            lapses         = state.lapses + lapsesDelta,
            state          = nextState,
        )
    }

    private fun reviewRelearning(
        state: CardFsrsState,
        rating: Rating,
        today: LocalDate,
        elapsedDays: Int,
    ): CardFsrsState {
        val s = algorithm.shortTermStability(state.stability, rating)
        val d = state.difficulty  // D doesn't change in Relearning
        val (nextState, schedDays) = when (rating) {
            Rating.Again -> CardState.Relearning to 0
            Rating.Hard  -> CardState.Relearning to 1
            Rating.Good  -> CardState.Review     to algorithm.nextInterval(s).coerceAtLeast(1)
            Rating.Easy  -> CardState.Review     to algorithm.nextInterval(s).coerceAtLeast(1)
        }
        val r = algorithm.retrievability(schedDays, s)
        val nextReviewDate = today + schedDays
        return state.copy(
            due             = nextReviewDate.toEpochDays().toLong(),
            stability      = s,
            difficulty     = d,
            retrievability = r,
            easeFactor     = state.easeFactor,
            averageInterval = schedDays,
            lastReviewDate = today,
            nextReviewDate = nextReviewDate,
            scheduledDays  = schedDays,
            elapsedDays    = elapsedDays,
            reps           = state.reps + 1,
            lapses         = state.lapses,
            state          = nextState,
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private data class Quad(
        val stability: Float,
        val difficulty: Float,
        val state: CardState,
        val lapsesDelta: Int,
    )
}

// ── Extensions ────────────────────────────────────────────────────────────────

/** Convenience: new unreviewed card state, ready to schedule the first review. */
fun newCardFsrsState(today: LocalDate): CardFsrsState = CardFsrsState(
    due             = 0L,
    stability       = 0f,
    difficulty      = 5f,
    retrievability  = 0f,
    easeFactor      = 2.5f,
    averageInterval = 0,
    lastReviewDate  = today,
    nextReviewDate  = today,
    scheduledDays   = 0,
    elapsedDays     = 0,
    reps            = 0,
    lapses          = 0,
    state           = CardState.New,
)

/** Advance a [LocalDate] by [days]. */
internal operator fun LocalDate.plus(days: Int): LocalDate =
    LocalDate.fromEpochDays(toEpochDays() + days)
