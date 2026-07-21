package com.mila.langualinker.fsrs

import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.test.*

/**
 * FSRS-6 algorithm unit tests.
 *
 * Reference values cross-checked against:
 *   https://github.com/open-spaced-repetition/awesome-fsrs/wiki/The-Algorithm
 *   https://expertium.github.io/Algorithm.html
 */
class FsrsAlgorithmTest {

    private val algo = FsrsAlgorithm()
    private val p = algo.params

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun assertApprox(expected: Float, actual: Float, tolerance: Float = 0.0001f, msg: String = "") {
        assertTrue(
            abs(expected - actual) <= tolerance,
            "Expected ~$expected but was $actual (tol=$tolerance). $msg",
        )
    }

    // ── Retrievability ────────────────────────────────────────────────────────

    @Test
    fun retrievability_atStability_is90pct() {
        // R(S, S) = 90% for all valid S, by definition
        for (s in listOf(1f, 5f, 10f, 50f, 100f)) {
            assertApprox(0.9f, algo.retrievability(s.toInt(), s), 0.001f, "S=$s")
        }
    }

    @Test
    fun retrievability_atZeroElapsed_is100pct() {
        // R(0, S) should be very close to 1
        assertApprox(1.0f, algo.retrievability(0, 10f), 0.001f)
    }

    @Test
    fun retrievability_decreasesWithTime() {
        val s = 10f
        val r1 = algo.retrievability(5, s)
        val r2 = algo.retrievability(10, s)
        val r3 = algo.retrievability(20, s)
        assertTrue(r1 > r2, "R should decrease over time: r1=$r1, r2=$r2")
        assertTrue(r2 > r3, "R should decrease over time: r2=$r2, r3=$r3")
    }

    @Test
    fun retrievability_zeroStability_returns0() {
        assertEquals(0f, algo.retrievability(5, 0f))
    }

    @Test
    fun retrievability_negativeStability_returns0() {
        assertEquals(0f, algo.retrievability(5, -1f))
    }

    @Test
    fun retrievability_clamped_between_0_and_1() {
        val r = algo.retrievability(1000, 1f)
        assertTrue(r in 0f..1f, "R out of bounds: $r")
    }

    // ── Next interval ─────────────────────────────────────────────────────────

    @Test
    fun nextInterval_atDesiredRetention09_equalsStability() {
        // I(0.9, S) ≈ S
        for (s in listOf(1f, 5f, 10f, 50f)) {
            val interval = algo.nextInterval(s, desiredRetention = 0.9f)
            // rounding may shift by 1
            assertTrue(abs(interval - s) <= 1, "I(0.9, $s) = $interval, expected ~$s")
        }
    }

    @Test
    fun nextInterval_higherRetention_givesShortInterval() {
        val s = 20f
        val i90 = algo.nextInterval(s, 0.90f)
        val i95 = algo.nextInterval(s, 0.95f)
        assertTrue(i95 < i90, "Higher retention → shorter interval: i95=$i95, i90=$i90")
    }

    @Test
    fun nextInterval_atLeastOne() {
        assertTrue(algo.nextInterval(0.001f) >= 1)
    }

    @Test
    fun nextInterval_capped_at_maximumInterval() {
        val hugeSt = 1_000_000f
        assertEquals(p.maximumInterval, algo.nextInterval(hugeSt))
    }

    // ── Initial stability ─────────────────────────────────────────────────────

    @Test
    fun initialStability_matchesDefaultWeights() {
        assertApprox(p.w[0], algo.initialStability(Rating.Again))
        assertApprox(p.w[1], algo.initialStability(Rating.Hard))
        assertApprox(p.w[2], algo.initialStability(Rating.Good))
        assertApprox(p.w[3], algo.initialStability(Rating.Easy))
    }

    @Test
    fun initialStability_easyGreaterThanGoodGreaterThanHardGreaterThanAgain() {
        val sA = algo.initialStability(Rating.Again)
        val sH = algo.initialStability(Rating.Hard)
        val sG = algo.initialStability(Rating.Good)
        val sE = algo.initialStability(Rating.Easy)
        assertTrue(sA < sH, "$sA < $sH")
        assertTrue(sH < sG, "$sH < $sG")
        assertTrue(sG < sE, "$sG < $sE")
    }

    @Test
    fun initialStability_neverBelowMinimum() {
        Rating.values().forEach { r ->
            assertTrue(algo.initialStability(r) >= 0.1f, "S0 < 0.1 for $r")
        }
    }

    // ── Initial difficulty ────────────────────────────────────────────────────

    @Test
    fun initialDifficulty_inRange() {
        Rating.values().forEach { r ->
            val d = algo.initialDifficulty(r)
            assertTrue(d in 1f..10f, "D0 out of [1,10] for $r: $d")
        }
    }

    @Test
    fun initialDifficulty_againHardest_easyEasiest() {
        val dA = algo.initialDifficulty(Rating.Again)
        val dH = algo.initialDifficulty(Rating.Hard)
        val dG = algo.initialDifficulty(Rating.Good)
        val dE = algo.initialDifficulty(Rating.Easy)
        assertTrue(dA > dH, "Again should be harder than Hard: $dA vs $dH")
        assertTrue(dH > dG, "Hard should be harder than Good: $dH vs $dG")
        assertTrue(dG > dE, "Good should be harder than Easy: $dG vs $dE")
    }

    @Test
    fun initialDifficulty_againMatchesW4() {
        // D0(Again) = w4 - exp(w5 * 0) + 1 = w4 - 1 + 1 = w4
        assertApprox(p.w[4], algo.initialDifficulty(Rating.Again), 0.001f)
    }

    // ── Next difficulty ───────────────────────────────────────────────────────

    @Test
    fun nextDifficulty_good_nearlyUnchanged() {
        // ΔD for Good = -w6*(3-3) = 0, so D barely changes (only mean reversion)
        val d = 5f
        val nextD = algo.nextDifficulty(d, Rating.Good)
        assertTrue(abs(nextD - d) < 0.5f, "Good rating should barely change D: was=$d, next=$nextD")
    }

    @Test
    fun nextDifficulty_again_increases() {
        val d = 5f
        assertTrue(algo.nextDifficulty(d, Rating.Again) > d,
            "Again should increase D")
    }

    @Test
    fun nextDifficulty_easy_decreases() {
        val d = 5f
        assertTrue(algo.nextDifficulty(d, Rating.Easy) < d,
            "Easy should decrease D")
    }

    @Test
    fun nextDifficulty_clamped_1_to_10() {
        // Boundary: very easy card can't go below 1
        val dLow = algo.nextDifficulty(1f, Rating.Easy)
        assertTrue(dLow >= 1f, "D cannot go below 1: $dLow")

        // Boundary: very hard card can't go above 10
        val dHigh = algo.nextDifficulty(10f, Rating.Again)
        assertTrue(dHigh <= 10f, "D cannot exceed 10: $dHigh")
    }

    @Test
    fun nextDifficulty_linearDamping_reducesChangeNearMax() {
        // At D=9.9 the delta should be smaller than at D=5 for same rating
        val deltaAt5 = abs(algo.nextDifficulty(5f, Rating.Again) - 5f)
        val deltaAt99 = abs(algo.nextDifficulty(9.9f, Rating.Again) - 9.9f)
        assertTrue(deltaAt99 < deltaAt5, "Linear damping: change near 10 should be smaller")
    }

    // ── Recall stability ──────────────────────────────────────────────────────

    @Test
    fun stabilityAfterRecall_alwaysGrowsOrStays() {
        // For Hard/Good/Easy, S' >= S
        for (rating in listOf(Rating.Hard, Rating.Good, Rating.Easy)) {
            val s = 10f
            val newS = algo.stabilityAfterRecall(5f, s, 0.9f, rating)
            assertTrue(newS >= s, "$rating: S' ($newS) should be >= S ($s)")
        }
    }

    @Test
    fun stabilityAfterRecall_easyGrowsFasterThanGoodGrowsFasterThanHard() {
        val d = 5f; val s = 10f; val r = 0.9f
        val sH = algo.stabilityAfterRecall(d, s, r, Rating.Hard)
        val sG = algo.stabilityAfterRecall(d, s, r, Rating.Good)
        val sE = algo.stabilityAfterRecall(d, s, r, Rating.Easy)
        assertTrue(sH <= sG, "Hard($sH) <= Good($sG)")
        assertTrue(sG <= sE, "Good($sG) <= Easy($sE)")
    }

    @Test
    fun stabilityAfterRecall_lowerR_givesHigherGrowth() {
        // Spacing effect: lower R at review time → larger stability gain
        val d = 5f; val s = 20f
        val sHighR = algo.stabilityAfterRecall(d, s, 0.9f, Rating.Good)
        val sLowR  = algo.stabilityAfterRecall(d, s, 0.1f, Rating.Good)
        assertTrue(sLowR > sHighR, "Lower R → larger S gain: lowR=$sLowR highR=$sHighR")
    }

    @Test
    fun stabilityAfterRecall_harderCard_growsSlower() {
        val s = 10f; val r = 0.9f
        val sEasy = algo.stabilityAfterRecall(2f, s, r, Rating.Good)
        val sHard = algo.stabilityAfterRecall(8f, s, r, Rating.Good)
        assertTrue(sHard < sEasy, "Harder card ($sHard) grows slower than easier ($sEasy)")
    }

    // ── Forget stability ──────────────────────────────────────────────────────

    @Test
    fun stabilityAfterForgetting_belowPrelapseStability() {
        val newS = algo.stabilityAfterForgetting(5f, 20f, 0.9f)
        assertTrue(newS <= 20f, "Post-lapse S ($newS) should be <= pre-lapse S (20)")
    }

    @Test
    fun stabilityAfterForgetting_neverBelowMinimum() {
        val newS = algo.stabilityAfterForgetting(10f, 0.1f, 0.9f)
        assertTrue(newS >= 0.1f, "Post-lapse S cannot be below minimum: $newS")
    }

    @Test
    fun stabilityAfterForgetting_largerPrelapseGivesLargerPostlapse() {
        // A card with more stability before forgetting recovers somewhat better
        val s1 = algo.stabilityAfterForgetting(5f, 10f, 0.5f)
        val s2 = algo.stabilityAfterForgetting(5f, 100f, 0.5f)
        assertTrue(s2 > s1, "Larger pre-lapse S → larger post-lapse S: s1=$s1, s2=$s2")
    }

    // ── Short-term stability ──────────────────────────────────────────────────

    @Test
    fun shortTermStability_goodAndEasy_neverDecreaseS() {
        val s = 5f
        for (rating in listOf(Rating.Good, Rating.Easy)) {
            val newS = algo.shortTermStability(s, rating)
            assertTrue(newS >= s, "$rating should not decrease S: was=$s, new=$newS")
        }
    }

    @Test
    fun shortTermStability_easyGrowsFasterThanGood() {
        val s = 2f
        val sG = algo.shortTermStability(s, Rating.Good)
        val sE = algo.shortTermStability(s, Rating.Easy)
        assertTrue(sE >= sG, "Easy($sE) should grow >= Good($sG)")
    }

    @Test
    fun shortTermStability_neverBelowMinimum() {
        Rating.values().forEach { r ->
            val newS = algo.shortTermStability(0.1f, r)
            assertTrue(newS >= 0.1f, "$r short-term S below minimum: $newS")
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

/**
 * FSRS-6 scheduler integration tests.
 */
class FsrsSchedulerTest {

    private val scheduler = FsrsScheduler()
    private val today = LocalDate(2024, 1, 1)

    private fun newCard() = newCardFsrsState(today)

    // ── State transitions ─────────────────────────────────────────────────────

    @Test
    fun newCard_anyRating_incrementsReps() {
        Rating.values().forEach { r ->
            val next = scheduler.review(newCard(), r, today)
            assertEquals(1, next.reps, "reps should be 1 after first review with $r")
        }
    }

    @Test
    fun newCard_again_goesToLearning() {
        val next = scheduler.review(newCard(), Rating.Again, today)
        assertEquals(CardState.Learning, next.state)
    }

    @Test
    fun newCard_hard_goesToLearning() {
        val next = scheduler.review(newCard(), Rating.Hard, today)
        assertEquals(CardState.Learning, next.state)
    }

    @Test
    fun newCard_good_goesToLearning() {
        val next = scheduler.review(newCard(), Rating.Good, today)
        assertEquals(CardState.Learning, next.state)
    }

    @Test
    fun newCard_easy_goesToReview() {
        val next = scheduler.review(newCard(), Rating.Easy, today)
        assertEquals(CardState.Review, next.state)
    }

    @Test
    fun newCard_easy_hasPositiveInterval() {
        val next = scheduler.review(newCard(), Rating.Easy, today)
        assertTrue(next.scheduledDays >= 1, "Easy new card should schedule >= 1 day")
    }

    @Test
    fun learning_good_graduatesToReview() {
        val learning = scheduler.review(newCard(), Rating.Good, today)
        assertEquals(CardState.Learning, learning.state)
        val next = scheduler.review(learning, Rating.Good, today)
        assertEquals(CardState.Review, next.state)
    }

    @Test
    fun learning_again_staysInLearning() {
        val learning = scheduler.review(newCard(), Rating.Good, today)
        val again = scheduler.review(learning, Rating.Again, today)
        assertEquals(CardState.Learning, again.state)
    }

    @Test
    fun review_again_goesToRelearning() {
        val review = simulateToReview(Rating.Good)
        val lapsed = scheduler.review(review, Rating.Again, today + review.scheduledDays)
        assertEquals(CardState.Relearning, lapsed.state)
    }

    @Test
    fun review_again_incrementsLapses() {
        val review = simulateToReview(Rating.Good)
        val lapsed = scheduler.review(review, Rating.Again, today + review.scheduledDays)
        assertEquals(review.lapses + 1, lapsed.lapses)
    }

    @Test
    fun review_good_staysInReview() {
        val review = simulateToReview(Rating.Good)
        val next = scheduler.review(review, Rating.Good, today + review.scheduledDays)
        assertEquals(CardState.Review, next.state)
    }

    @Test
    fun relearning_good_returnToReview() {
        val review = simulateToReview(Rating.Good)
        val lapsed = scheduler.review(review, Rating.Again, today + review.scheduledDays)
        val recovered = scheduler.review(lapsed, Rating.Good, today + review.scheduledDays)
        assertEquals(CardState.Review, recovered.state)
    }

    @Test
    fun relearning_again_staysRelearning() {
        val review = simulateToReview(Rating.Good)
        val lapsed = scheduler.review(review, Rating.Again, today + review.scheduledDays)
        val again = scheduler.review(lapsed, Rating.Again, today + review.scheduledDays)
        assertEquals(CardState.Relearning, again.state)
    }

    // ── Date / interval correctness ───────────────────────────────────────────

    @Test
    fun nextReviewDate_matchesScheduledDays() {
        val next = scheduler.review(newCard(), Rating.Good, today)
        val expectedDate = today + next.scheduledDays
        assertEquals(expectedDate, next.nextReviewDate)
    }

    @Test
    fun review_intervalGrowsWithSuccessfulReviews() {
        var card = scheduler.review(newCard(), Rating.Good, today)  // → Learning
        card = scheduler.review(card, Rating.Good, today)           // → Review
        val interval1 = card.scheduledDays
        val reviewDay1 = today + interval1
        card = scheduler.review(card, Rating.Good, reviewDay1)       // → Review again
        val interval2 = card.scheduledDays
        assertTrue(interval2 >= interval1, "Interval should grow: i1=$interval1, i2=$interval2")
    }

    // ── Stability / difficulty consistency ───────────────────────────────────

    @Test
    fun review_again_reducesStability() {
        val review = simulateToReview(Rating.Good)
        val preS = review.stability
        val lapsed = scheduler.review(review, Rating.Again, today + review.scheduledDays)
        assertTrue(lapsed.stability < preS, "Lapse should reduce S: $preS → ${lapsed.stability}")
    }

    @Test
    fun review_easy_increasesStabilityMoreThanGood() {
        val reviewG = simulateToReview(Rating.Good)
        val reviewE = simulateToReview(Rating.Easy)
        val nextG = scheduler.review(reviewG, Rating.Good, today + reviewG.scheduledDays)
        val nextE = scheduler.review(reviewE, Rating.Easy, today + reviewE.scheduledDays)
        // Easy new card starts with higher S, and grows more aggressively
        assertTrue(nextE.stability > nextG.stability,
            "Easy path stability (${nextE.stability}) > Good path (${nextG.stability})")
    }

    @Test
    fun newCard_repsZero_lapsesZero() {
        val card = newCard()
        assertEquals(0, card.reps)
        assertEquals(0, card.lapses)
    }

    @Test
    fun retrievability_setAfterReview() {
        val next = scheduler.review(newCard(), Rating.Good, today)
        assertTrue(next.retrievability in 0f..1f)
    }

    // ── Boundary: extreme parameters ─────────────────────────────────────────

    @Test
    fun scheduler_highDesiredRetention_shortIntervals() {
        val paramsHigh = FsrsParameters(desiredRetention = 0.99f)
        val paramsLow  = FsrsParameters(desiredRetention = 0.70f)
        val schedHigh = FsrsScheduler(paramsHigh)
        val schedLow  = FsrsScheduler(paramsLow)
        val c = newCard()
        val h = schedHigh.review(schedHigh.review(c, Rating.Good, today), Rating.Good, today)
        val l = schedLow.review(schedLow.review(c, Rating.Good, today), Rating.Good, today)
        assertTrue(h.scheduledDays <= l.scheduledDays,
            "High retention → shorter interval: ${h.scheduledDays} vs ${l.scheduledDays}")
    }

    @Test
    fun parameterValidation_wrongWeightCount_throws() {
        assertFailsWith<IllegalArgumentException> {
            FsrsParameters(w = floatArrayOf(1f, 2f))
        }
    }

    @Test
    fun parameterValidation_retentionOutOfRange_throws() {
        assertFailsWith<IllegalArgumentException> {
            FsrsParameters(desiredRetention = 0.5f)
        }
    }

    @Test
    fun parameterValidation_retentionAboveMax_throws() {
        assertFailsWith<IllegalArgumentException> {
            FsrsParameters(desiredRetention = 1.01f)
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Bring a card through New → Learning → Review with the given rating. */
    private fun simulateToReview(firstRating: Rating): CardFsrsState {
        var card = scheduler.review(newCard(), firstRating, today)     // New → Learning/Review
        if (card.state == CardState.Learning) {
            card = scheduler.review(card, Rating.Good, today)          // Learning → Review
        }
        return card
    }

    private operator fun LocalDate.plus(days: Int): LocalDate =
        LocalDate.fromEpochDays(toEpochDays() + days)
}
