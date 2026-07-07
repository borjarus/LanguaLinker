# FSRS-6 Algorithm

LanguaLinker uses **FSRS-6** (Free Spaced Repetition Scheduler v6) — a modern memory model backed by neurological research. This doc explains the math, state machine, and usage.

**References:**
- https://github.com/open-spaced-repetition/awesome-fsrs/wiki/The-Algorithm
- https://expertium.github.io/Algorithm.html

---

## Key Concepts

### Memory State (S, D, R)

| Symbol | Name | Meaning | Range |
|--------|------|---------|-------|
| **S** | Stability | Days until recall drops to 90% | 0.1–∞ |
| **D** | Difficulty | How hard card is to retain | 1–10 |
| **R** | Retrievability | Current recall probability | 0–1 (0%–100%) |

### How They Interact

1. **Higher S** = longer you can wait before review (spacing effect)
2. **Higher D** = harder to grow S (gains diminish on tough cards)
3. **Lower R at review time** = larger S gain (review when almost forgotten = max benefit)

### User Rating

When you review a card, you press one of 4 buttons:

| Rating | Meaning | Effect on S | Effect on D |
|--------|---------|------------|------------|
| **Again** | Forgot | Reset S (post-lapse) | Increase D |
| **Hard** | Struggled | Grow S slower | Slight D increase |
| **Good** | Correct | Grow S normally | D unchanged |
| **Easy** | Obvious | Grow S faster | Decrease D |

---

## State Machine

Card lifecycle: **New → Learning → Review ⇄ Relearning**

```
New (unreviewed)
├─ Again/Hard/Good  →  Learning (sched 0/1/1 day)
└─ Easy             →  Review   (sched = interval)

Learning (ramping up S, sched ≤ 1 day)
├─ Again            →  Learning (sched 0)
├─ Hard             →  Learning (sched 1)
├─ Good/Easy        →  Review   (sched = interval, min 1 day)

Review (stable, sched ≥ 1 day)
├─ Again            →  Relearning (sched 0, S reset, lapses++)
└─ Hard/Good/Easy   →  Review     (sched = new interval)

Relearning (ramping back up after lapse, sched ≤ 1 day)
├─ Again            →  Relearning (sched 0)
├─ Hard             →  Relearning (sched 1)
└─ Good/Easy        →  Review     (sched = interval, min 1 day)
```

**Key rules:**
- **Learning/Relearning** use short-term stability formula (evolves same-day)
- **Review** uses full recall-stability formula (grows over weeks/months)
- **Lapse** (Again in Review) = card forgotten; reset S, bump D, increment `lapses` counter

---

## Math (FSRS-6)

### Forgetting Curve

How R decays over time:

$$R(t, S) = (1 + \text{FACTOR} \cdot \frac{t}{S})^{-w_{20}}$$

where $t$ = days elapsed, $S$ = stability, $w_{20}$ ≈ 0.154 (trainable shape).

**Constant:** FACTOR = $0.9^{-1/w_{20}} - 1$ ≈ 0.00865

**Key fact:** $R(S, S) = 90\%$ always (by definition of S).

### Next Interval

Given desired retention $r$ (e.g., 0.9 = 90%), solve for interval:

$$I(r, S) = \frac{S}{\text{FACTOR}} \cdot (r^{-1/w_{20}} - 1)$$

When $r = 0.9$, then $I = S$ (e.g., if S=20, next review in 20 days).

### Initial State (First Review)

**Initial stability** — picked from 4 defaults based on first rating:
$$S_0(G) = w_{G-1}$$
(w₀ for Again, w₁ for Hard, w₂ for Good, w₃ for Easy)

Default weights: [0.212, 1.293, 2.307, 8.296]

**Initial difficulty** — computed from first rating:
$$D_0(G) = w_4 - \exp(w_5 \cdot (G-1)) + 1, \quad \text{clamped } [1, 10]$$

### Difficulty Update

After any review:

$$\Delta D = -w_6 \cdot (G - 3)$$

Linear damping (diminish change as D→10):
$$D' = D + \Delta D \cdot \frac{10 - D}{9}$$

Mean reversion (nudge toward default Easy difficulty):
$$D'' = w_7 \cdot D_0(\text{Easy}) + (1 - w_7) \cdot D'$$

Clamp to [1, 10].

**Effect:** Again/Hard increase D, Good leaves it, Easy decreases it. Converges over time.

### Recall Stability (Successful Review)

After Hard/Good/Easy in Review state:

$$S'_r = S \cdot \left(\exp(w_8) \cdot (11 - D) \cdot S^{-w_9} \cdot (\exp(w_{10} \cdot (1-R)) - 1) \cdot hf \cdot ef + 1\right)$$

where:
- $hf = w_{15}$ if Hard, else 1
- $ef = w_{16}$ if Easy, else 1
- Result always ≥ S (stability never decreases on success)

**Intuition:**
- Larger D → smaller gain (hard cards grow slower)
- Larger S → smaller gain (diminishing returns as S saturates)
- Smaller R → larger gain (spacing effect: review when almost forgotten)

### Forget Stability (Lapse)

After Again in Review state:

$$S'_f = w_{11} \cdot D^{-w_{12}} \cdot ((S+1)^{w_{13}} - 1) \cdot \exp(w_{14} \cdot (1-R))$$

Clamped to $(0, S]$ — post-lapse S never exceeds pre-lapse S.

**Intuition:** Harder cards recover better from lapse. Low R at lapse time = steeper reset.

### Short-Term Stability (Learning/Relearning)

Same-day reviews (Learning, Relearning):

$$S'_{st} = S \cdot \exp(w_{17} \cdot (G - 3 + w_{18})) \cdot S^{-w_{19}}$$

**Constraint:** Good/Easy cannot decrease S.

**Effect:** Small S grows quickly; larger S grows slowly. Allows rapid graduation from Learning.

---

## Implementation

### Architecture

```
shared/src/commonMain/kotlin/com/mila/langualinker/fsrs/

├── FsrsParameters.kt       # 21 weights + config
├── FsrsAlgorithm.kt        # Pure math (no state mutation)
├── FsrsScheduler.kt        # State machine + transitions
└── CardFsrsState.kt        # Data class (already exists)
```

### Core Classes

**`FsrsParameters`**
```kotlin
data class FsrsParameters(
    val w: FloatArray,            // 21 weights
    val desiredRetention: Float,  // Target R (default 0.9)
    val maximumInterval: Int,     // Cap interval days
)
```

**`FsrsAlgorithm`**
```kotlin
class FsrsAlgorithm(val params: FsrsParameters) {
    fun retrievability(elapsedDays: Int, stability: Float): Float
    fun nextInterval(stability: Float): Int
    fun initialStability(rating: Rating): Float
    fun initialDifficulty(rating: Rating): Float
    fun nextDifficulty(difficulty: Float, rating: Rating): Float
    fun stabilityAfterRecall(d: Float, s: Float, r: Float, rating: Rating): Float
    fun stabilityAfterForgetting(d: Float, s: Float, r: Float): Float
    fun shortTermStability(stability: Float, rating: Rating): Float
}
```

**`FsrsScheduler`**
```kotlin
class FsrsScheduler(params: FsrsParameters = FsrsParameters()) {
    fun review(
        state: CardFsrsState,
        rating: Rating,
        reviewDate: LocalDate
    ): CardFsrsState
}
```

---

## Usage Example

```kotlin
import kotlinx.datetime.LocalDate
import com.mila.langualinker.fsrs.*

// Create scheduler with default FSRS-6 params
val scheduler = FsrsScheduler()
val today = LocalDate(2024, 7, 3)

// New unreviewed card
var card = newCardFsrsState(today)

// First review: user rates "Good"
card = scheduler.review(card, Rating.Good, today)
// → state = Learning, scheduledDays = 1, stability ≈ 2.3

// Tomorrow: user rates "Easy"
val tomorrow = today + 1
card = scheduler.review(card, Rating.Easy, tomorrow)
// → state = Review, scheduledDays ≈ 6, stability ≈ 13.5

// One week later: user rates "Good"
val nextReview = tomorrow + 6
card = scheduler.review(card, Rating.Good, nextReview)
// → state = Review, scheduledDays ≈ 36, stability ≈ 21.0

// Inspect card
println("Stability: ${card.stability}")        // 21.0
println("Difficulty: ${card.difficulty}")      // 2.9
println("Retrievability: ${card.retrievability}") // ~0.9
println("Next review: ${card.nextReviewDate}") // 36 days out
println("Total reviews: ${card.reps}")        // 3
println("Lapses: ${card.lapses}")             // 0
```

### Custom Parameters

```kotlin
val customParams = FsrsParameters(
    w = floatArrayOf(/* custom 21 weights */),
    desiredRetention = 0.95f,  // Stricter: aim for 95% recall
    maximumInterval = 36500    // Cap at 100 years
)
val strictScheduler = FsrsScheduler(customParams)
```

---

## Testing

54 unit tests in `FsrsTest.kt`:

- **Algorithm tests (31):** R bounds, interval growth, initial state, stability updates, edge cases
- **Scheduler tests (23):** State transitions, date tracking, lapse counting, parameter validation

All pass ✓

### Key Test Scenarios

```kotlin
// Retrievability at stability is 90%
algo.retrievability(10, 10f) == 0.9f  ✓

// Interval grows with repeated success
interval1 < interval2 < interval3  ✓

// Lapses increment and drop S
preS > postS_after_lapse  ✓

// Difficulty converges
repeated_good_ratings → D → D0(Easy)  ✓

// Easy rating is stronger than Good
easy_gain > good_gain  ✓
```

---

## Key Insights

1. **Spacing effect is optimal:** Reviewing a card just before you'd forget it yields max S gain
2. **Difficulty adapts:** Hard cards grow slower; Easy cards decrease difficulty over time
3. **Lapses are recorded:** Forgetting is data; post-lapse S resets conservatively
4. **No session-based limits:** Intervals are data-driven, not arbitrary "easy/normal/hard" factors
5. **Personalizable:** Train 21 weights on your own review history for better predictions

---

## References

- **Official FSRS wiki:** https://github.com/open-spaced-repetition/awesome-fsrs/wiki
- **Expertium's explanation:** https://expertium.github.io/Algorithm.html
- **Research:** Chuanren Liu et al., "MaiMemo: DHP-enhanced Memory Model"

---

## Glossary

| Term | Meaning |
|------|---------|
| **Review** | Act of testing yourself on a card and rating (Again/Hard/Good/Easy) |
| **Lapses** | Count of times you pressed "Again" on a Review/Relearning card |
| **Gradient descent** | Optimization method used to train weights from past review history |
| **Ease hell** | SM-2 problem where easy factor keeps decreasing; FSRS solves with mean reversion |
| **Fuzz** | Random jitter applied to intervals to prevent review clumping |
| **Post-lapse S** | Stability after forgetting; conservative reset to avoid scheduling too soon |
