package com.mila.langualinker.fsrs

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * Pure FSRS-6 math — no scheduling state, no side effects.
 *
 * All formulas follow the FSRS-6 spec:
 *   https://github.com/open-spaced-repetition/awesome-fsrs/wiki/The-Algorithm
 *
 * Forgetting curve  : R(t,S) = (1 + FACTOR·t/S)^(−w20)
 *                     FACTOR = 0.9^(−1/w20) − 1
 * Interval          : I(r,S) = S/FACTOR · (r^(−1/w20) − 1)
 * Initial stability : S₀(G) = w[G−1]
 * Initial difficulty: D₀(G) = w4 − exp(w5·(G−1)) + 1,  clamped [1,10]
 * Difficulty update : linear damping then mean reversion towards D₀(Easy)
 * Recall stability  : S·(exp(w8)·(11−D)·S^(−w9)·(exp(w10·(1−R))−1)·hf·ef + 1)
 * Forget stability  : w11·D^(−w12)·((S+1)^w13−1)·exp(w14·(1−R)), min(…,S)
 * Short-term S      : S·exp(w17·(G−3+w18))·S^(−w19), Good/Easy ≥ S
 */
class FsrsAlgorithm(val params: FsrsParameters = FsrsParameters()) {

    // ── Forgetting curve ─────────────────────────────────────────────────────

    /** Pre-computed FACTOR for the current w20.  factor = 0.9^(−1/w20) − 1 */
    private val factor: Float
        get() = 0.9f.pow(-1f / params.w[20]) - 1f

    /**
     * Retrievability: probability of recall after [elapsedDays] since a
     * review when stability is [stability].
     *
     * R(t,S) = (1 + FACTOR·t/S)^(−w20)
     */
    fun retrievability(elapsedDays: Int, stability: Float): Float {
        if (stability <= 0f) return 0f
        return (1f + factor * elapsedDays / stability)
            .pow(-params.w[20])
            .coerceIn(0f, 1f)
    }

    // ── Intervals ────────────────────────────────────────────────────────────

    /**
     * Next interval in days targeting [desiredRetention].
     *
     * I(r,S) = S/FACTOR · (r^(−1/w20) − 1)
     */
    fun nextInterval(
        stability: Float,
        desiredRetention: Float = params.desiredRetention,
    ): Int {
        val f = factor
        val interval = stability / f * (desiredRetention.pow(-1f / params.w[20]) - 1f)
        return interval.toInt().coerceIn(1, params.maximumInterval)
    }

    // ── Initial state ────────────────────────────────────────────────────────

    /**
     * Initial stability after the very first rating of a new card.
     *
     * S₀(G) = w[G−1]  (w0=Again, w1=Hard, w2=Good, w3=Easy)
     */
    fun initialStability(rating: Rating): Float =
        params.w[rating.ordinal].coerceAtLeast(0.1f)

    /**
     * Initial difficulty after the first rating.
     *
     * D₀(G) = w4 − exp(w5·(G−1)) + 1,  clamped [1,10]
     */
    fun initialDifficulty(rating: Rating): Float {
        val g = rating.grade.toFloat()
        return (params.w[4] - exp(params.w[5] * (g - 1f)) + 1f)
            .coerceIn(1f, 10f)
    }

    // ── Difficulty update ────────────────────────────────────────────────────

    /**
     * Next difficulty after a review.
     *
     * 1. ΔD = −w6·(G−3)
     * 2. Linear damping  : D' = D + ΔD·(10−D)/9
     * 3. Mean reversion  : D'' = w7·D₀(Easy) + (1−w7)·D'
     * 4. Clamp [1,10]
     *
     * Note: mean-reversion target D₀(Easy) is used unclamped so that
     * the very small w7 value barely nudges D.
     */
    fun nextDifficulty(difficulty: Float, rating: Rating): Float {
        val g = rating.grade.toFloat()
        val delta = -params.w[6] * (g - 3f)
        val dampened = difficulty + delta * (10f - difficulty) / 9f
        // D₀(4) = w4 − exp(w5·3) + 1  (unclamped as mean-reversion target)
        val d0Easy = params.w[4] - exp(params.w[5] * 3f) + 1f
        val reverted = params.w[7] * d0Easy + (1f - params.w[7]) * dampened
        return reverted.coerceIn(1f, 10f)
    }

    // ── Stability updates ────────────────────────────────────────────────────

    /**
     * Stability after a successful review (Hard / Good / Easy).
     *
     * S'r = S · (exp(w8)·(11−D)·S^(−w9)·(exp(w10·(1−R))−1)·hf·ef + 1)
     *
     * hf = w15 if Hard, else 1
     * ef = w16 if Easy, else 1
     */
    fun stabilityAfterRecall(
        difficulty: Float,
        stability: Float,
        retrievability: Float,
        rating: Rating,
    ): Float {
        val hardFactor = if (rating == Rating.Hard) params.w[15] else 1f
        val easyFactor = if (rating == Rating.Easy) params.w[16] else 1f
        val sInc = exp(params.w[8]) *
            (11f - difficulty) *
            stability.pow(-params.w[9]) *
            (exp(params.w[10] * (1f - retrievability)) - 1f) *
            hardFactor * easyFactor
        return (stability * (sInc + 1f)).coerceAtLeast(stability)
    }

    /**
     * Stability after forgetting (Again press on a Review/Relearning card).
     *
     * S'f = w11·D^(−w12)·((S+1)^w13−1)·exp(w14·(1−R))
     *
     * Clamped to (0, S] — post-lapse S cannot exceed pre-lapse S.
     */
    fun stabilityAfterForgetting(
        difficulty: Float,
        stability: Float,
        retrievability: Float,
    ): Float {
        val sf = params.w[11] *
            difficulty.pow(-params.w[12]) *
            ((stability + 1f).pow(params.w[13]) - 1f) *
            exp(params.w[14] * (1f - retrievability))
        return sf.coerceIn(0.1f, stability)
    }

    /**
     * Short-term stability for same-day (Learning / Relearning) reviews.
     *
     * S' = S · exp(w17·(G−3+w18)) · S^(−w19)
     *
     * Constraint: Good/Easy cannot decrease S.
     */
    fun shortTermStability(stability: Float, rating: Rating): Float {
        val g = rating.grade.toFloat()
        val newS = stability *
            exp(params.w[17] * (g - 3f + params.w[18])) *
            stability.pow(-params.w[19])
        return if (rating == Rating.Good || rating == Rating.Easy) {
            newS.coerceAtLeast(stability)
        } else {
            newS.coerceAtLeast(0.1f)
        }
    }
}

// ── Internal helpers ──────────────────────────────────────────────────────────

/** Numeric grade: Again=1, Hard=2, Good=3, Easy=4 */
internal val Rating.grade: Int
    get() = ordinal + 1  // Again=0 → 1, Hard=1 → 2, Good=2 → 3, Easy=3 → 4
