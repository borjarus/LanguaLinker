package com.mila.langualinker.fsrs

/**
 * FSRS-6 parameters: 21 optimizable weights (w0..w20) plus scheduling config.
 *
 * w0-w3   : initial stability per first rating (Again/Hard/Good/Easy)
 * w4      : initial difficulty anchor (D0(Again) = w4)
 * w5      : difficulty exponential factor
 * w6      : difficulty change magnitude per grade
 * w7      : mean-reversion weight towards D0(Easy)
 * w8      : recall-stability log-scale
 * w9      : stability power in recall formula
 * w10     : retrievability factor in recall formula
 * w11     : post-lapse stability scale
 * w12     : difficulty power in post-lapse formula
 * w13     : stability power in post-lapse formula
 * w14     : retrievability factor in post-lapse formula
 * w15     : hard-rating penalty factor (applied to SInc)
 * w16     : easy-rating bonus factor (applied to SInc)
 * w17     : short-term stability scale
 * w18     : short-term stability offset
 * w19     : short-term stability decay (FSRS-6)
 * w20     : forgetting-curve shape / decay (FSRS-6, typically 0.1..0.8)
 */
data class FsrsParameters(
    val w: FloatArray = DEFAULT_WEIGHTS.clone(),
    val desiredRetention: Float = 0.9f,
    val maximumInterval: Int = 36500,
) {
    init {
        require(w.size == WEIGHT_COUNT) {
            "FSRS-6 requires exactly $WEIGHT_COUNT weights, got ${w.size}"
        }
        require(desiredRetention in 0.7f..0.99f) {
            "desiredRetention must be in [0.7, 0.99], got $desiredRetention"
        }
        require(maximumInterval > 0) { "maximumInterval must be positive" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FsrsParameters) return false
        return w.contentEquals(other.w) &&
            desiredRetention == other.desiredRetention &&
            maximumInterval == other.maximumInterval
    }

    override fun hashCode(): Int =
        31 * (31 * w.contentHashCode() + desiredRetention.hashCode()) + maximumInterval

    override fun toString(): String =
        "FsrsParameters(w=${w.contentToString()}, " +
            "desiredRetention=$desiredRetention, maximumInterval=$maximumInterval)"

    companion object {
        const val WEIGHT_COUNT = 21

        /** FSRS-6 default weights as published in open-spaced-repetition/awesome-fsrs wiki. */
        val DEFAULT_WEIGHTS = floatArrayOf(
            0.2120f,  // w0  – S0(Again)
            1.2931f,  // w1  – S0(Hard)
            2.3065f,  // w2  – S0(Good)
            8.2956f,  // w3  – S0(Easy)
            6.4133f,  // w4  – initial difficulty anchor
            0.8334f,  // w5  – difficulty exponential factor
            3.0194f,  // w6  – difficulty change per grade
            0.0010f,  // w7  – mean-reversion weight
            1.8722f,  // w8  – recall stability log-scale
            0.1666f,  // w9  – stability power in recall
            0.7960f,  // w10 – retrievability factor in recall
            1.4835f,  // w11 – post-lapse stability scale
            0.0614f,  // w12 – difficulty power in post-lapse
            0.2629f,  // w13 – stability power in post-lapse
            1.6483f,  // w14 – retrievability factor in post-lapse
            0.6014f,  // w15 – hard penalty
            1.8729f,  // w16 – easy bonus
            0.5425f,  // w17 – short-term scale
            0.0912f,  // w18 – short-term offset
            0.0658f,  // w19 – short-term stability decay
            0.1542f,  // w20 – forgetting-curve shape
        )
    }
}
