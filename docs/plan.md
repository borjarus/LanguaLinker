# Implementation Plan for a Flashcards App (Anki-like) in Kotlin Multiplatform

**Version:** 3 (supersedes v2)
**Date:** 2026-08-28
**Status:** Phases 1–3 complete; Phase 4 onward re-planned

---

## How to read this document

Everything marked **`[v3]`** is new or materially changed compared to *Plan ENG v2*. Content without a marker is carried over unchanged and is still binding. Nothing from v2 was deleted; a few items were **moved** to a different phase — those moves are listed explicitly in *Mapping v2 → v3*.

Items marked **`[DECIDE]`** are open questions that need an answer from the product owner before the phase that depends on them starts. They are collected in one table in *Open Decisions*.

---

## Project Goal

A mobile app (Android + iOS) for learning through spaced repetition using the **FSRS** algorithm and a built-in mnemonic association system based on methods (Substitution Word Technique + Chain Association Method). The app will come with default sentence/phrase decks for each language (e.g. Deck: German).

### `[v3]` Target user and framing

The plan is written for one primary persona; every scope decision below is judged against it.

- **Primary persona:** a Polish-speaking adult self-learner of German or English at A1–B1, who has heard of Anki but finds it too complex to configure, and who learns better with vivid mnemonic imagery than with bare word lists.
- **Native language (L1):** Polish for MVP — this matters because the mnemonic technique ("find a Polish word that sounds similar") is L1-dependent and is currently hardcoded in the prompt.
- **Target languages (L2):** German, English for MVP.
- **Device profile:** mid-range Android (API 26+) and iPhone (iOS 16+), often used offline (commute).

### `[v3]` Success metrics (v1)

These drive what must be instrumented in Phase 12, not just what gets built.

| Metric | Target for v1 | Why it matters |
|---|---|---|
| D7 retention (users who study on day 7) | ≥ 25 % | Core habit signal for an SRS app |
| Median session start time (cold start → first card) | < 2.0 s | Study sessions are short; friction kills the habit |
| Association generation p90 latency | < 4 s | Above this the user stops asking for associations |
| Association "save as favorite" rate | ≥ 20 % of generations | Proxy for association quality |
| True retention (Good+Easy / all reviews, mature cards) | 85–92 % | Confirms FSRS scheduling is actually working |
| Crash-free session rate | ≥ 99.5 % | Store ranking + trust |

---

## `[v3]` Scope, Non-Goals and Open Decisions

### In scope for v1

Bundled DE/EN sentence + word decks, FSRS scheduling, study session, association generation, grammar tips, word links, card browser, editor, statistics, notifications, CSV import/export, local backup.

### Explicit non-goals for v1

Stated so they do not creep in mid-phase:

- Cloud sync and multi-device (schema will be sync-*ready*, see C1 — but no sync server).
- Shared/community decks, deck marketplace, user accounts.
- Filtered/custom-study decks (Anki's "Custom Study").
- Handwriting, image occlusion, cloze deletion.
- Web and Desktop builds — see `[DECIDE] D-01`.

### `[v3]` Open Decisions

| ID   | Decision                                                                                                                                      | Blocks                  | Default if unanswered                                                                        |
| ---- | --------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------- | -------------------------------------------------------------------------------------------- |
| D-01 | Do Desktop/Web targets ship in v1? The stack section claims Compose MP for "Android + iOS + Desktop + Web" but no phase builds or tests them. | Phase 4, CI             | **No.** Android + iOS only; keep `shared/` platform-agnostic so Desktop can be added in v1.1 |
| D-02 | LLM access model: user brings own API key (BYOK), or we run a proxy backend and absorb cost?                                                  | Phase 7, legal, pricing | **Hybrid:** free quota via our proxy, BYOK to lift the cap                                   |
| D-03 | Monetization: free, one-off purchase, subscription? Determines whether a proxy is affordable.                                                 | D-02, Phase 14          | Free v1, decide before v1.1                                                                  |
| D-04 | Source of bundled sentences: self-authored, licensed corpus, or LLM-generated + human review?                                                 | Phase 5                 | LLM-generated + native-speaker review (see *Content sourcing*)                               |
| D-05 | Is `.apkg` **import** in v1 scope, or import-only-CSV in v1 and `.apkg` in v1.1?                                                              | Phase 11                | `.apkg` **export** in v1, **import** in v1.1                                                 |
| D-06 | UI language(s) at launch: Polish only, or Polish + English?                                                                                   | Phase 4                 | Polish + English                                                                             |
| D-07 | Minimum age rating / is the app directed at children? Affects store data-safety forms and LLM content policy.                                 | Release                 | 12+/PEGI 3, **not** directed at children                                                     |
| D-08 | Analytics vendor and whether telemetry is opt-in or opt-out under GDPR.                                                                       | Phase 12                | Opt-in, self-hosted or EU-hosted                                                             |

---

## Architecture and Technology Stack

- **KMP (Kotlin Multiplatform)** — shared logic: domain, data, FSRS engine, association engine.
- **Compose Multiplatform** — UI (Android + iOS + Desktop + Web) — *scope narrowed by `[DECIDE] D-01`*.
- **SQLDelight** — local database (shared).
- **Koin** — DI (shared).
- **Kotlinx.serialization** — data serialization.
- **Kotlinx.datetime** — date/time handling (FSRS requires precise timestamps).
- **DataStore** — user settings.
- **Ktor** — requests to the association-generation model (LLM API).
- **Bundled content** — built-in language decks in the app assets.

### `[v3]` Additions to the stack

| Concern | Choice | Rationale |
|---|---|---|
| Navigation | Compose Multiplatform Navigation (or Voyager/Decompose) | 9 screens with deep links (`/deck/{id}`, `/study/{deckId}`) need a real nav graph, not manual state |
| Secret storage | **Android Keystore / iOS Keychain** via `expect/actual` — **not DataStore** | v2 puts the LLM API key in DataStore, which is plaintext on disk. See C6 |
| Crash reporting | Sentry KMP (or Firebase Crashlytics) | Crash-free rate is a v1 success metric |
| Logging | Kermit | Structured logs shared across targets |
| String resources / i18n | Compose Multiplatform resources or Lyricist/moko-resources | UI language ≠ learning language; see C3 |
| Background work | WorkManager (Android) / BGTaskScheduler + UNUserNotificationCenter (iOS) via `expect/actual` | Notification scheduling differs fundamentally per platform (Phase 12) |
| Text-to-speech | Platform TTS via `expect/actual` (`android.speech.tts` / `AVSpeechSynthesizer`) | Free pronunciation for a language app; large value for near-zero cost |
| Testing | kotlin.test + Turbine (Flows) + Paparazzi/Roborazzi (screenshots) + kotlinx-benchmark | v2 lists "unit tests" with no tooling decision |
| Static analysis | ktlint + detekt + Gradle version catalog | Enforced in CI from Phase 4 |
| Serialization of bundled content | **NDJSON** streaming parse | Matches the note in *Bundled Content*; avoids loading a 10 k-card deck fully into memory on first launch |

---

## `[v3]` Cross-Cutting Design Decisions

These apply to every phase and are the most common source of expensive late rework. Each has an owner phase where it is implemented and a test that proves it.

### C1 — Identity and sync-readiness

v2 uses `Long` autoincrement IDs and declares sync a "Won't Have (v1)". That combination is the single most expensive thing to retrofit later, because every card, deck and association would need re-keying and every existing user's local data migrated.

**Decision:** keep `Long` as the local primary key for SQLite efficiency, but add to every user-owned entity:

```kotlin
interface Syncable {
    val guid: String        // UUID v4 or Anki-compatible base91 GUID, stable forever
    val updatedAt: Instant  // last local modification
    val deletedAt: Instant? // tombstone; rows are soft-deleted, never hard-deleted
    val usn: Int            // update sequence number, -1 = pending push (Anki convention)
}
```

- Applies to: `decks`, `notes`, `cards`, `associations`, `grammar_tips`, `sentence_word_links`, `deck_settings`.
- `guid` is also what makes `.apkg` **re-import** idempotent: Anki matches notes by `guid`, so without it a re-import duplicates every card instead of updating it.
- Cost now: ~1 day + one migration. Cost later: a multi-week migration touching every table.

### C2 — Time, timezones and the day cutoff

v2 mixes `LocalDate` (`lastReviewDate`, `nextReviewDate`), `Long` (`due`) and `Instant` (`createdAt`). FSRS needs precise instants; "cards due today" needs a *local* day boundary. These are different concepts and must not share a type.

**Decisions:**

- All scheduling math uses `Instant` (UTC). `LocalDate` appears only in the presentation layer.
- A **day cutoff hour** setting (default 04:00 local, as in Anki) defines when "today" rolls over, so a 1 a.m. study session counts as the previous day.
- Store the user's `ZoneId` and the `rolloverHour` in settings; recompute due counts on timezone change rather than assuming the device zone is stable (travel, DST).
- Persist the **collection creation timestamp** (`collectionCreatedAt`, Anki's `crt`, set to the local day-cutoff on first launch). It is required to convert Anki's review-card `due` (days since collection creation) to and from an absolute instant. Without it, `.apkg` import/export silently shifts every review card's due date.
- Test: a card reviewed at 23:59 and a card reviewed at 00:01 land in the correct study days under a 04:00 cutoff, in `Europe/Warsaw` across a DST change.

### C3 — Localization vs learning languages

v2 never mentions app localization, yet the prompt is hardcoded to Polish output and the plan targets Polish learners.

**Decisions:**

- Three distinct language concepts, never conflated: **UI language** (app chrome), **L1 / native language** (user's own, used for translations and mnemonics), **L2 / target language** (what is being learned, a property of the deck).
- `Deck` gains `sourceLanguage` and `targetLanguage`; the association prompt takes L1 as a parameter instead of hardcoding "Polish".
- No user-visible string is hardcoded in a composable from Phase 4 onward; a CI check fails the build on literal strings in UI modules.
- Plurals matter: "2 karty" vs "5 kart" — use proper plural resources, not string concatenation.

### C4 — Accessibility (cross-cutting, not a Phase-9 afterthought)

v2 lists "Accessibility" as one checkbox at the end. Retro-fitting it across 9 screens costs more than building it in.

**Decisions:**

- Every interactive element has a content description; the study session's Again/Hard/Good/Easy buttons are announced with their next-interval value.
- **Replace emoji used as UI icons** (⭐ 📘 🔗 💡) with real vector icons plus text labels. Emoji render inconsistently across Android OEMs and iOS versions, are read aloud by screen readers as their Unicode name ("open book"), and carry no meaning for a colorblind or low-vision user.
- Dynamic type: study-session text must survive 200 % font scale without clipping.
- Contrast ≥ 4.5:1 in both themes; never encode state in colour alone (card state badges get a shape or label too).
- Test: TalkBack and VoiceOver walkthrough of the study session recorded once per phase from Phase 6 on.

### C5 — Offline-first

Association generation is the only network-dependent feature, and it is a *Must Have*. The app must be fully usable on a plane.

**Decisions:**

- Bundled decks ship with **pre-generated associations and grammar tips baked in** (see *Bundled Content*), so a brand-new user with no network gets the full experience on day one and we pay the generation cost once at build time instead of once per user per card.
- Generated associations are cached locally and keyed by `(text, L1, L2, promptVersion)`; a repeat request never hits the network.
- Requests made offline are queued and retried; the UI shows a clear "will generate when online" state, not an error toast.
- Every screen defines its **loading / empty / error / offline** state explicitly in Phase 4's design system. "Empty deck", "no cards due today", "generation failed" are designed states, not blank screens.

### C6 — Secrets, privacy and data protection

- The LLM API key moves from DataStore to Keystore/Keychain. Never logged, never included in crash reports, never in exported backups.
- Card content sent to a third-party LLM is **personal data processing** under GDPR when the user typed it. Required before launch: privacy policy naming the LLM sub-processor, an explicit in-app consent the first time generation is used, a DPA with the LLM vendor, and a documented retention/no-training stance (most vendors offer a no-training-on-API-data commitment — get it in writing).
- Data minimisation: send only the card text, L1 and L2. Never the user ID, device ID, deck name or review history.
- The app must work end-to-end with generation disabled, so consent can genuinely be declined.
- Under the EU AI Act, LLM-generated associations are a transparency case: label them in the UI as AI-generated (a small badge on generated vs. user-saved associations — the data model already distinguishes them via `AssociationType`).

### C7 — Content safety

The prompt asks for "absurd, colorful, emotional" scenes. That is exactly the instruction most likely to produce sexual or violent imagery, and mnemonic techniques amplify it.

**Decisions:**

- Explicit negative constraints in the system prompt (no sexual content, no graphic violence, no slurs, no real named people).
- Server-side or SDK moderation pass on every generated association before it is shown; blocked results trigger one silent retry, then a neutral fallback message.
- User-facing "report this association" action that logs the prompt + output for review.
- This is a store-review issue as much as a safety one — both Apple and Google now ask about user-generated / AI-generated content in the review questionnaire (`[DECIDE] D-07`).

---

## Card Model & FSRS Data Model

Each card gets a set of Anki-equivalent fields so that progress and scheduling metadata survive round-trips through import/export, including `.apkg`.

| Anki field (PL) | Anki field (EN) | App field | Notes |
|---|---|---|---|
| Pole sortowania | Sort Field | `sortField` | Text used for sorting/search in Card Browser |
| Tagi | Tags | `tags` | List of strings used for filtering |
| Oczekuje | Due | `due` | Critical for import/export, preserves scheduling progress |
| Śr. łatwość (nowa) | Ease | `easeFactor` | Legacy SM-2 compatibility field, populated on Anki import |
| Trudność | Difficulty | `difficulty` | FSRS difficulty |
| Stabilność | Stability | `stability` | FSRS stability |
| Śr. przerwa | Average interval | `averageInterval` | Mean interval across reviews |
| Pomyłki | Lapses | `lapses` | FSRS lapses |
| Powtórki | Reps | `reps` | FSRS repetitions |
| Przywoływalność | Retrievability | `retrievability` | FSRS retrievability |
| Położenie | Position | `position` | New-card creation order / queue order |

### `[v3]` Correction: what `due` actually means in Anki

v2 declares `due: Long` as "Anki-compatible", which is not sufficient — in Anki the column is **polymorphic** and its unit depends on the card's type:

| Card type | Meaning of `due` | Conversion needed |
|---|---|---|
| New (`type = 0`) | Queue **position**, starting at 1 — not a date at all | Maps to our `position` |
| Learning / Relearning (`type = 1` / `3`) | **Epoch seconds** | Direct instant |
| Review (`type = 2`) | **Days since collection creation** (`col.crt`) | Requires `collectionCreatedAt` (see C2) |

Storing one untyped `Long` and calling it "Anki-compatible" will corrupt scheduling on import for either learning or review cards. **Action:** persist an unambiguous `dueAt: Instant` internally, and convert to/from Anki's polymorphic `due` **only** at the import/export boundary, in a dedicated, unit-tested `AnkiDueCodec`. The round-trip test in Phase 11 must cover all four card types, not just review cards.

Sources: [AnkiDroid Database Structure](https://github.com/ankidroid/Anki-Android/wiki/Database-Structure)

### `[v3]` Missing concept: Note vs Card

Anki's model is **Note → (template) → 1..n Cards**. A single note ("der Abend / evening") generates a forward card and a reverse card that schedule independently but share their content. v2 collapses this into `Card(front, back)` while still declaring a `card_templates` table and `.apkg` import as goals — those are mutually incompatible: a note with two cards cannot round-trip through a model that has no notes.

**Decision:** introduce `Note` now. It is cheap while there is no production data and it unlocks, for free: reverse cards (recognition **and** production practice — a major learning feature for a language app), sibling burying, and faithful `.apkg` import.

```kotlin
data class Note(
    val id: Long,
    val guid: String,                    // C1 — Anki-compatible, stable across export/import
    val deckId: Long,
    val noteTypeId: Long,                // -> NoteType / card templates
    val fields: List<String>,            // ordered; Anki stores these 0x1F-separated
    val sortField: String,
    val tags: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class NoteType(
    val id: Long,
    val name: String,                    // "Basic", "Basic (and reversed)", "Sentence"
    val fieldNames: List<String>,
    val templates: List<CardTemplate>
)

data class CardTemplate(
    val ordinal: Int,                    // Anki `ord`
    val name: String,                    // "Recognition", "Production"
    val questionFormat: String,          // "{{Front}}"
    val answerFormat: String             // "{{FrontSide}}<hr>{{Back}}"
)
```

### `[v3]` Missing concept: suspended, buried, leech and flags

`CardState { New, Learning, Review, Relearning }` describes the *memory* state but leaves no room for the *queue* state. Anki separates `type` (memory) from `queue` (schedulability), and the app needs the same split or these behaviours are impossible: suspend a card you never want to see, bury a sibling until tomorrow, auto-tag a card you keep failing.

```kotlin
enum class CardState { New, Learning, Review, Relearning }   // memory state (Anki `type`)

enum class QueueState {                                       // schedulability (Anki `queue`)
    UserBuried, SiblingBuried, Suspended, New, Learning, Review, DayLearn, Preview
}

data class LeechConfig(
    val lapseThreshold: Int = 8,        // lapses before a card is flagged
    val action: LeechAction = LeechAction.TagOnly
)
enum class LeechAction { TagOnly, Suspend }
```

Also add `flags: Int` (Anki's colour flags, 0–7) so browser flags survive a round-trip.

### `[v3]` Correction: derived fields must not be persisted as truth

Two fields in v2's `CardFsrsState` are **functions of other fields plus the current time**, not independent state:

- `retrievability` — computed as `f(elapsedDays, stability, decay)`. Persisting it means it is stale the moment the clock moves; a card loaded from the database would report yesterday's retrievability. **Store nothing; compute on read.** Optionally persist `retrievabilityAtLastReview` as an immutable historical snapshot for statistics.
- `averageInterval` — derivable from `review_logs`. Persist it only as a denormalised cache with a clear comment, or compute it in the stats query.

Keeping derived values in the same struct as source-of-truth values is how scheduling bugs become unreproducible.

### `[v3]` Revised card model

```kotlin
data class Card(
    val id: Long,
    val guid: String,                    // C1
    val noteId: Long,                    // [v3] cards belong to notes
    val deckId: Long,
    val templateOrdinal: Int,            // [v3] which template produced this card
    val cardType: CardType,              // Sentence | Word | Text
    val position: Int,                   // new-card queue order
    val flags: Int = 0,                  // [v3]
    val fsrsState: CardFsrsState,
    val queueState: QueueState,          // [v3]
    val updatedAt: Instant,              // C1
    val deletedAt: Instant? = null       // C1
)

data class CardFsrsState(
    val dueAt: Instant,                  // [v3] unambiguous; Anki `due` is a codec concern
    val stability: Float,
    val difficulty: Float,
    val easeFactor: Float,               // SM-2 legacy, import/export only
    val lastReviewedAt: Instant?,        // [v3] Instant, not LocalDate
    val scheduledDays: Int,
    val elapsedDays: Int,
    val reps: Int,
    val lapses: Int,
    val state: CardState
) {
    // [v3] derived, never persisted
    fun retrievability(now: Instant, decay: Float): Float = TODO()
}

enum class Rating { Again, Hard, Good, Easy }
enum class CardState { New, Learning, Review, Relearning }
enum class CardType { Sentence, Word, Text }   // [v3] Text added for TextWithAssociations decks
```

**Import/export requirement:** the `due` field must always be read from and written to `.apkg` / CSV / JSON payloads. This is what lets the user export a collection and later re-import it without losing the review progress already accumulated on each card.

### `[v3]` Missing model: ReviewLog

v2 creates a `review_logs` table and calls review logs a key decision, but never defines the record. Its shape determines whether FSRS parameter optimisation (Phase 15) is possible at all — the optimiser needs the full review history with the state *before* each review.

```kotlin
data class ReviewLog(
    val id: Long,
    val cardId: Long,
    val reviewedAt: Instant,
    val rating: Rating,
    val stateBefore: CardState,
    val stabilityBefore: Float,
    val difficultyBefore: Float,
    val elapsedDays: Int,                 // days since previous review
    val scheduledDays: Int,               // interval that had been assigned
    val durationMs: Int,                  // answer time; needed for "study time" stats
    val reviewKind: ReviewKind,           // [v3]
    val fsrsVersion: String,              // [v3] which engine version produced this
    val parametersHash: String            // [v3] which weight vector was in effect
)

enum class ReviewKind { Learning, Review, Relearning, Filtered, Manual, Rescheduled }
```

`fsrsVersion` + `parametersHash` matter because a review logged under one parameter set is not directly comparable to one logged under another — without them, an optimiser trained on mixed history produces garbage.

### `[v3]` Missing model: Deck

v2 defines `DeckSettings` but never the `Deck` itself, although `decks` is the first table in the schema.

```kotlin
data class Deck(
    val id: Long,
    val guid: String,
    val name: String,
    val parentDeckId: Long?,             // [v3] subdecks — "German::A1::Verbs"
    val description: String,
    val sourceLanguage: String,          // [v3] L1, BCP-47 — C3
    val targetLanguage: String,          // [v3] L2, BCP-47
    val configId: Long,                  // [v3] shared options preset, see below
    val contentVersion: Int,             // [v3] for bundled-deck updates
    val isBundled: Boolean,              // [v3]
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null
)
```

### `[v3]` Deck options as a shared preset

v2's `DeckSettings` is one row per deck. Anki users expect **presets** shared by many decks ("Default", "Intensive"), so changing `newCardsPerDay` once applies everywhere. Splitting this later means migrating every deck.

```kotlin
data class DeckConfig(
    val id: Long,
    val name: String,
    // FSRS
    val requestRetention: Float = 0.9f,
    val maximumInterval: Int = 36_500,
    val fsrsParameters: List<Float>,         // 21 weights for FSRS-6 (w0..w20)
    val learningSteps: List<Duration>,       // [v3] e.g. [1m, 10m]
    val relearningSteps: List<Duration>,     // [v3] e.g. [10m]
    val enableFuzz: Boolean = true,          // [v3] interval jitter to avoid review pile-ups
    // Daily limits and ordering
    val newCardsPerDay: Int = 20,
    val reviewsPerDay: Int = 200,            // [v3]
    val newCardOrder: NewCardOrder,          // [v3] Position | Random
    val newCardGatherPriority: GatherOrder,  // [v3]
    val reviewOrder: ReviewOrder,            // [v3] DueDate | Random | AscendingRetrievability
    val burySiblings: Boolean = true,        // [v3]
    val leech: LeechConfig = LeechConfig()   // [v3]
)

data class DeckSettings(          // per-deck, content-shaped settings only
    val deckId: Long,
    val type: DeckType,
    val configId: Long            // [v3] -> DeckConfig
)

enum class NewCardOrder { Position, Random }
enum class GatherOrder { DeckThenPosition, AscendingPosition, RandomNotes }
enum class ReviewOrder { DueDateThenRandom, Random, AscendingRetrievability, RelativeOverdueness }
```

### `[v3]` Reserved: media

Images and audio are *Could Have*, but reserving the table now costs one migration and avoids a painful one later — and TTS audio caching needs it anyway.

```kotlin
data class Media(
    val id: Long,
    val noteId: Long,
    val fileName: String,          // Anki media-map compatible
    val mimeType: String,
    val checksum: String,          // dedup + integrity
    val kind: MediaKind
)
enum class MediaKind { Image, Audio, TtsCache }
```

---

## Association Data Model

```kotlin
data class Association(
    val id: Long,
    val guid: String,                    // C1
    val cardId: Long,
    val content: String,
    val type: AssociationType,           // Generated | UserSaved
    val isFavorite: Boolean,
    val createdAt: Instant,
    // [v3] provenance and cacheability
    val sourceLanguage: String,          // L1 used when generating
    val targetLanguage: String,
    val promptVersion: String,           // which AssociationPromptBuilder template
    val modelId: String?,                // e.g. "gpt-4o-mini" — null for UserSaved
    val moderationVerdict: ModerationVerdict,
    val cacheKey: String                 // hash(text, L1, L2, promptVersion) — C5
)

enum class AssociationType { Generated, UserSaved, Bundled }   // [v3] Bundled added
enum class ModerationVerdict { Approved, Flagged, NotChecked } // [v3]
```

`[v3]` **Enforce "one favorite per card" in the schema**, not just in the use case: a partial unique index on `(cardId) WHERE isFavorite = 1`. v2 states this as a design principle but a boolean column alone permits two favorites, and this *will* happen through import or a race in the UI.

## Grammar Tip Data Model

A card can have zero, one, or multiple grammar tips attached. Grammar tips are primarily meant for sentence-type cards, since a single word rarely needs grammatical context on its own.

```kotlin
data class GrammarTip(
    val id: Long,
    val guid: String,                    // C1
    val cardId: Long,
    val content: String,
    val order: Int,
    val source: GrammarTipSource,        // Bundled | Generated | UserAdded
    val createdAt: Instant,
    val updatedAt: Instant               // C1
)

enum class GrammarTipSource { Bundled, Generated, UserAdded }
```

`[v3]` **Attach grammar tips to the note, not the card.** If a note produces both a recognition and a production card, the grammar tip ("`ins` = `in das`") is true of the content, not of one direction of practice. Duplicating it per card means editing it twice. Change `cardId` → `noteId`, with the same change for `Association` where the deck type is `Linguistic`.

`[v3]` **Reordering needs a stable strategy.** `order: Int` with a `ReorderGrammarTipsUseCase` implies rewriting every row's index on each drag. Either accept that (lists are short — fine here) or use fractional indexing. Decide and document, and make the reorder operation transactional so a crash mid-drag cannot leave duplicate order values.

## Word-Sentence Link Model

Every word inside a sentence card can be linked to its corresponding entry in a **word deck**, so the user can jump from a sentence to a specific word to see its own associations and grammar tips.

```kotlin
data class SentenceWordLink(
    val id: Long,
    val guid: String,                        // C1
    val sentenceCardId: Long,
    val wordCardId: Long?,                   // [v3] nullable — see unresolved links below
    val positionInSentence: Int,
    val surfaceForm: String,
    val lemma: String,                       // [v3] dictionary form, the actual join key
    val resolution: LinkResolution           // [v3]
)

enum class LinkResolution { Resolved, Unresolved, Ambiguous, UserOverridden }  // [v3]
```

- `surfaceForm` lets the UI highlight the exact word as written in the sentence.
- A sentence card can have multiple `SentenceWordLink` rows.
- In newly created sentence cards, each individual word should be linkable to a word-deck card.
- Tapping a linked word in Study Session or Card Detail opens a quick preview: translation, associations, and grammar tips for that specific word.

### `[v3]` Rules the link resolver needs (these are not obvious and will otherwise be invented ad hoc)

v2 says `wordCardRef` "is resolved at import time to the actual `cardId`", but the example itself already contains three hard cases. Specify them now:

1. **Inflection.** `gehe` → `gehen`, `ging` → `gehen`. The join must be on `lemma`, and the JSON must therefore carry the lemma explicitly (`wordCardRef: "gehen"`), which the current example does — good. But user-created sentences have no lemma. **Rule:** for user-created cards, match `surfaceForm` exactly first, then a normalised form (lowercase, strip articles), then leave `Unresolved` and offer the user a picker. Never guess silently.
2. **Contractions and multi-word units.** `ins` = `in das` maps one surface token to a two-word entry, and `heute Abend` is one unit spanning two tokens. **Rule:** `positionInSentence` becomes a **range** (`startIndex`, `endIndex`), not a point — otherwise "heute Abend" can never be linked as one concept.
3. **Homographs.** German `sie` (she/they/you-formal), `der` (article/relative pronoun). **Rule:** `Ambiguous` resolution shows a disambiguation sheet on first tap and records the choice as `UserOverridden`.
4. **Missing target.** A `wordCardRef` with no matching word-deck card must **fail the build** for bundled content (see the content linter) and degrade gracefully to a non-tappable word for user content — never a crash, never an empty sheet.
5. **Deletion.** Deleting a word card must not orphan links; `wordCardId` is nullable and the link falls back to `Unresolved`.

## Deck Type Model

Decks now carry a **type**, configurable in Deck Settings. At the start, three types are supported:

```kotlin
enum class DeckType {
    Linguistic,
    TextWithAssociations,
    Simple
}
```

- **Linguistic** — standard language deck: front/back, FSRS, associations, grammar tips, and word links.
- **TextWithAssociations** — free text (paragraph, quote, definition, note) with generated mnemonic associations for the whole text.
- **Simple** — plain flashcard: front/back only, no associations, no grammar tips, no word links.

Deck type is selected in deck settings and determines which UI sections and fields are available.

### `[v3]` Deck type is a UI capability set, not a data restriction

Define the mapping once, in one place, so nine screens do not each re-derive it — and so changing a deck's type never destroys data.

```kotlin
data class DeckCapabilities(
    val hasAssociations: Boolean,
    val hasGrammarTips: Boolean,
    val hasWordLinks: Boolean,
    val associationScope: AssociationScope   // PerWord | PerSentence | WholeText | None
)

enum class AssociationScope { PerWord, PerSentence, WholeText, None }

fun DeckType.capabilities(): DeckCapabilities = when (this) {
    DeckType.Linguistic           -> DeckCapabilities(true, true, true, AssociationScope.PerSentence)
    DeckType.TextWithAssociations -> DeckCapabilities(true, false, false, AssociationScope.WholeText)
    DeckType.Simple               -> DeckCapabilities(false, false, false, AssociationScope.None)
}
```

**Rule:** switching a deck from `Linguistic` to `Simple` **hides** associations, grammar tips and links; it never deletes them. Switching back restores them. The UI must warn ("3 associations and 5 grammar tips will be hidden") and the data must survive. Without this rule the first user who explores the type picker loses work.

---

## Built-in Language Decks (Bundled Content)

**IMPORTANT**
Check the alternative solution in `NDJSON + SQLDelight + Kotlin Importer for Android`.

- JSON/CSV files in `assets/decks/` bundled with the app.
- On first launch, they are imported into SQLDelight.
- **Default bundled decks contain sentences/phrases, not single words.**
- Single words are shipped in separate, dedicated word decks.

Example structure of a `de_deck_sentences.json` deck:

```json
{
  "language": "de",
  "name": "German - Sentences",
  "deckType": "Linguistic",
  "cardType": "sentence",
  "cards": [
    {
      "front": "Ich gehe heute Abend ins Kino.",
      "back": "I'm going to the cinema tonight.",
      "tags": ["phrase", "A1"],
      "grammarTips": [
        "'ins' = 'in das' (article contraction in the accusative after the preposition 'in')",
        "'heute Abend' means 'tonight', not a literal word-for-word translation"
      ],
      "wordLinks": [
        { "surfaceForm": "Ich", "wordCardRef": "ich" },
        { "surfaceForm": "gehe", "wordCardRef": "gehen" },
        { "surfaceForm": "heute", "wordCardRef": "heute" },
        { "surfaceForm": "Abend", "wordCardRef": "der Abend" },
        { "surfaceForm": "ins", "wordCardRef": "in das" },
        { "surfaceForm": "Kino", "wordCardRef": "das Kino" }
      ]
    }
  ]
}
```

Corresponding word deck example:

```json
{
  "language": "de",
  "name": "German - Words",
  "deckType": "Linguistic",
  "cardType": "word",
  "cards": [
    { "front": "ich", "back": "I", "tags": ["pronoun", "A1"] },
    { "front": "gehen", "back": "to go", "tags": ["verb", "A1"] },
    { "front": "heute", "back": "today", "tags": ["adverb", "A1"] },
    { "front": "der Abend", "back": "evening", "tags": ["noun", "A1"] },
    { "front": "in das", "back": "into the", "tags": ["phrase", "A1"] },
    { "front": "das Kino", "back": "cinema", "tags": ["noun", "A1"] }
  ]
}
```

- `wordCardRef` is resolved at import time to the actual `cardId` in the matching word deck.
- MVP: German, English.
- Additional languages as app updates.

### `[v3]` Revised bundled-deck schema

Four fields are missing and each one causes a concrete problem later:

```json
{
  "schemaVersion": 1,
  "deckGuid": "b6b1e0e2-...",
  "contentVersion": 3,
  "language": "de",
  "sourceLanguage": "pl",
  "name": "German - Sentences",
  "deckType": "Linguistic",
  "cardType": "sentence",
  "license": { "source": "original", "attribution": null },
  "cards": [
    {
      "guid": "n_de_0001",
      "front": "Ich gehe heute Abend ins Kino.",
      "back": "Idę dziś wieczorem do kina.",
      "tags": ["phrase", "A1"],
      "grammarTips": ["..."],
      "associations": [
        { "content": "...", "type": "Bundled" }
      ],
      "wordLinks": [
        { "surfaceForm": "heute Abend", "startIndex": 2, "endIndex": 3, "wordCardRef": "heute Abend" }
      ]
    }
  ]
}
```

| New field | Why |
|---|---|
| `schemaVersion` | The importer must reject a file it does not understand instead of half-importing it |
| `deckGuid` + card `guid` | Makes bundled-deck **updates** possible: v1.1 can add 200 cards to the German deck and fix a typo in card 42 **without** resetting the user's review progress. Without stable GUIDs, the only update path is delete-and-reimport, which destroys progress |
| `contentVersion` | Drives the update check: import if `bundled.contentVersion > local.contentVersion` |
| `sourceLanguage` | `back` is currently English while the target user is Polish (C3) — the example itself shows this inconsistency |
| `license` | See content sourcing below |
| `associations` | Pre-generated, so day-one offline works and generation cost is paid once (C5) |
| `startIndex`/`endIndex` | Multi-token links like "heute Abend" |

### `[v3]` Content sourcing and licensing — resolve before writing content

This is the one item in the whole plan with a **legal** failure mode, and v2 does not mention it at all. Options:

| Source | License reality | Verdict |
|---|---|---|
| Tatoeba corpus | **CC-BY 2.0 FR** — attribution required, per-sentence; audio may carry non-commercial restrictions set by individual contributors | Usable but requires an in-app attribution screen and per-sentence license verification. Not "free" |
| Scraping other apps' or Anki shared decks | Almost always copyrighted or unlicensed | **No** |
| Copying Anki source code for `.apkg` handling | Anki is **AGPL-3.0**, AnkiDroid **GPL-3.0** — copyleft | Implement the *format* from the documented spec; do **not** copy code into a closed-source app |
| Existing textbook/course sentences | Copyrighted | **No** |
| LLM-generated + native-speaker review | We own the output (subject to vendor terms); costs review time | **Recommended default (D-04)** |

**Action for Phase 5:** decide D-04, and if any third-party corpus is used, add an "Attributions" screen and ship the license text in assets. Also avoid using the word "Anki" in the store title or subtitle — it is a trademark; "compatible with Anki decks" in the description is the safe phrasing.

Sources: [Tatoeba Terms of Use](https://tatoeba.org/en/terms_of_use)

### `[v3]` Build-time content linter (small effort, prevents a whole class of shipped bugs)

A Gradle task + CI check that runs against `assets/decks/` on every commit and **fails the build** on:

- a `wordCardRef` with no matching card in the paired word deck (rule 4 above),
- a duplicate `guid` within or across decks,
- a `front` duplicated inside a deck,
- a card whose `deckType` requires grammar tips or associations but has none,
- malformed JSON/NDJSON, unknown `schemaVersion`,
- an association longer than the UI's designed maximum (associations are shown in a fixed card area — a 400-character mnemonic breaks the layout),
- missing `sourceLanguage`/`targetLanguage`.

Reference counts per language deck are also asserted here, so "German - Sentences has 500 cards" is a test, not a hope.

### `[v3]` Import contract

- **Idempotent.** Running the importer twice must not duplicate anything (match on `guid`).
- **Ordered.** Word decks import before sentence decks, otherwise every `wordCardRef` is unresolved. v2 does not state this and the failure is silent.
- **Transactional.** One transaction per deck; a crash mid-import leaves either the whole deck or nothing. A half-imported deck with unresolved links is the worst outcome and is what a naive per-card loop produces.
- **Resumable and non-blocking.** First launch must not show a frozen splash screen for 20 s. Import runs with visible progress, or the app opens on an "preparing your decks" state.
- **Benchmarked.** A 5 000-card deck must import in < 5 s on a mid-range Android device. If not, switch to NDJSON streaming + batched inserts inside a single transaction (this is what the `NDJSON + SQLDelight` note refers to).
- `bundledDecksImported: Boolean` from v2 is **replaced** by a per-deck `contentVersion` map, so future content updates work.

---

## Association Generation System

### Approach: External LLM API (MVP)

- Ktor sends a prompt to an LLM (OpenAI / Gemini / custom endpoint).
- The prompt includes:
  - the current word / sentence / text from the flashcard,
  - source and target language,
  - an instruction to use mnemonic techniques.

For **TextWithAssociations** decks, the same engine is used but targets the whole text, chaining associations across key terms and ideas.

### Custom Model Option (v2 / optional)

- Fine-tuning a small model (e.g. Mistral 7B / Phi-3) on pairs: word → mnemonic association.
- Run locally via ONNX Runtime / llama.cpp.
- Requires preparing a training dataset.
- Decision: **start with the API, use a custom model as an option in v2 once user data is available**.

### Prompt Engineering

```text
You are an expert in mnemonics. You use the Substitution Word Technique:
1. Find a Polish word that sounds similar to the foreign word
2. Create an absurd, colorful, emotional scene connecting the substitute word with the translation
3. The scene must include IMAGE + ACTION + EMOTION
4. Max 2–3 sentences. Polish only.

Word: [FOREIGN_WORD]
Translation: [TRANSLATION]
```

### `[v3]` Problems with the current prompt and how to fix them

| Problem | Fix |
|---|---|
| "Polish" is hardcoded in two places, but L1 is a user property (C3) | Parameterise: `[NATIVE_LANGUAGE]`. The technique is L1-dependent by nature; the prompt must be too |
| One template for three very different jobs (word / sentence / whole text), yet `GenerateAssociationUseCase` is supposed to "branch behavior by DeckType" | Three templates, versioned. Substitution Word for single words; **Chain Association / story method** for sentences (the plan names this method in the goal but never uses it); key-term chaining for `TextWithAssociations` |
| No output format — the response is free text, so the app cannot reliably split a mnemonic from a preamble ("Sure! Here's a mnemonic:") | Request structured JSON output (`{"association": "...", "substituteWord": "..."}`) and use the vendor's JSON mode. Parse strictly, retry once on parse failure |
| No safety constraints (C7) | Explicit negative constraints + moderation pass |
| No length bound in characters, only "2–3 sentences" | Hard character cap matching the UI's designed area (the content linter enforces the same cap on bundled content) |
| No prompt versioning | `promptVersion` on every stored association, so a prompt change can be A/B'd and old outputs can be identified |
| Deterministic-ish quality | Pin `temperature`, `model`, `max_tokens`; store `modelId`. A prompt is code — it belongs in version control with the same review discipline |

### `[v3]` Cost, latency and abuse model (`[DECIDE] D-02`)

The plan treats LLM access as a settings field. It is really a business decision with three viable shapes:

| Model | User friction | Our cost | Abuse risk | Notes |
|---|---|---|---|---|
| **BYOK** — user pastes their own key | High (most target users have no OpenAI account) | Zero | None | Fine for a power-user beta; will not survive contact with the mainstream persona |
| **Our proxy, free quota** | None | ~€0.0002–0.002 per association with a small model; dominated by abuse if unguarded | High — an exposed endpoint gets scraped | Needs per-install token, server-side rate limit, quota, and a kill switch |
| **Hybrid (recommended)** | None up to a quota, then BYOK | Bounded | Bounded | Free tier ≈ 50 generations/month covers the honest user; the bundled decks already ship pre-generated, so most users never hit the API |

Whatever is chosen: **cache aggressively** (C5). Two users asking for a mnemonic for "das Kino" in Polish should cost one API call, not two — a shared server-side cache keyed on `(text, L1, L2, promptVersion)` cuts cost by an order of magnitude for common vocabulary and is the main reason to prefer a proxy over BYOK.

Latency budget: p90 < 4 s end-to-end. Show a skeleton immediately, stream if the vendor supports it, and never block the study session — the user can rate the card while an association is still generating.

---

## `[v3]` Implementation Phases — Revised Roadmap

### Why the roadmap changed

Three structural problems with v2's phase order:

1. **No working app until week 11.** Phases 4–6 build content, engines and use cases with no UI at all; the first end-to-end study session happens in the middle of a 5-week UI block. That means the FSRS engine — the riskiest correctness item — is not exercised by a human until two-thirds of the schedule is spent. **Fix: a walking skeleton.** Get *one* deck, *one* study screen and *one* rating flow working end-to-end as early as possible, then thicken it.
2. **Phase 4 bundles the two heaviest, least-related jobs.** "Prepare bundled JSON decks" (content work) and "import `.apkg`" (reverse-engineering a compressed, protobuf-carrying, three-variant binary format) are both scheduled into week 4. `.apkg` import alone is realistically 2–3 weeks, is only *Should Have*, and blocks nothing. **Fix: split them; bundled import stays early, `.apkg` moves late.**
3. **No design/UI foundation phase.** Nine screens are specified in content terms with no theme, type scale, component library, navigation graph, or state conventions. Each screen would invent its own. **Fix: a foundation phase before the screens.**

### Mapping v2 → v3

| v2 | v3 | Change |
|---|---|---|
| Phase 1 — Project Setup | Phase 1 ✅ | Unchanged (done) |
| Phase 2 — FSRS-6 Engine | Phase 2 ✅ + **Phase 3.5** | Done; conformance tests and scheduler split added as retrofit |
| Phase 3 — Data Layer | Phase 3 ✅ + **Phase 3.5** | Done; C1/C2 schema retrofit added |
| — | **Phase 3.5 — Retrofit** `[NEW]` | Absorbs the model corrections above while there is no production data |
| — | **Phase 4 — Design System & App Shell** `[NEW]` | Theme, navigation, state conventions, i18n, a11y baseline |
| Phase 4 (bundled JSON + importer) | **Phase 5** | Kept early; schema extended; linter added |
| Phase 4 (CSV import/export) | **Phase 11** | Moved — not needed to prove the product |
| Phase 4 (`.apkg` import/export) | **Phase 11** | Moved — highest-effort, lowest-certainty item; `Should Have` |
| Phase 6 (use cases) | **Phase 6** | Moved earlier than the association engine; split into scheduler-critical and rest |
| Phase 7 (Screens 1, 3, 4) | **Phase 7 — Walking Skeleton** | Pulled forward: dashboard → study → summary, end to end |
| Phase 5 (association engine) | **Phase 8** | Now sits after there is a UI to show associations in |
| Phase 7 (Screens 5, 6, 7) | **Phase 9** | Card detail, editor, browser |
| Phase 7 (Screens 2, 8, 9) | **Phase 10** | Deck overview, statistics, settings |
| Phase 8 (notifications) | **Phase 12** | Unchanged in content; platform split made explicit |
| Phase 9 (polish & testing) | **Phase 13** | Unchanged in content; a11y moved to cross-cutting |
| — | **Phase 14 — Beta & Release** `[NEW]` | Store compliance, privacy policy, phased rollout |
| — | **Phase 15 — Post-launch** `[NEW]` | FSRS optimiser on collected review logs |

### `[v3]` Definition of Done — applies to every phase

A phase is not complete until all of these hold. This is the single highest-leverage addition to the plan: v2's checkboxes describe *activities*, not *outcomes*.

- [ ] All acceptance criteria for the phase pass, demonstrated on a **real device** (one Android, one iOS), not just an emulator.
- [ ] New code is covered by tests at the level stated in the phase; the suite is green in CI on both targets.
- [ ] No new ktlint/detekt violations; no new compiler warnings in `shared/`.
- [ ] Any schema change ships with a migration **and** a migration test that upgrades a fixture database from the previous released version.
- [ ] User-visible strings are externalised and translated for all D-06 languages.
- [ ] New screens have loading / empty / error / offline states implemented (C5).
- [ ] New screens pass a TalkBack **and** VoiceOver walkthrough (C4).
- [ ] Docs updated: this plan, the ADR log, and the README if setup changed.
- [ ] A 5-minute demo of the phase's outcome is recorded or shown.

---

### PHASE 1 — Project Setup (Week 1) ✅

- [x] Initialize the KMP project (Android + iOS targets).
- [x] Configure Gradle: KMP, Compose Multiplatform, dependency versions.
- [x] Add dependencies: SQLDelight, Koin, kotlinx.datetime, kotlinx.serialization, DataStore, Ktor.
- [x] Module structure:
  - `shared/` — domain, data, FSRS engine, association engine
  - `androidApp/`
  - `iosApp/`
- [x] CI/CD: GitHub Actions — build checks for Android + iOS.

**`[v3]` Backlog carried into Phase 3.5**

- [ ] Add ktlint + detekt to CI and fail the build on violations.
- [ ] Add a Gradle version catalog if not already present.
- [ ] Add the content-linter task stub (Phase 5 fills it in).
- [ ] Set up signing configs and a `debug`/`release`/`beta` build-type matrix now, not at release time.
- [ ] Add crash reporting and Kermit logging so every subsequent phase is debuggable on device.
- [ ] Decide `[DECIDE] D-01` and remove unused targets from the build to keep CI fast.

---

### PHASE 2 — FSRS-6 Engine (Week 2) ✅

- [x] Implement the FSRS memory model in `shared/domain/fsrs/`
- [x] Unit tests for the FSRS engine
- [x] Support states: `New → Learning → Review → Relearning`
- [x] Calculate: `stability`, `difficulty`, `retrievability`, `nextInterval`

**`[v3]` What "FSRS-6 engine" must additionally contain**

The four bullets above describe roughly half of a usable FSRS implementation. Missing pieces, all of which change scheduling behaviour visibly:

- [ ] **Pin the exact FSRS version and its default parameter vector.** FSRS-6 uses 21 weights (`w0`–`w20`); `w20` is the personalisable decay parameter introduced in FSRS-6, and `w17`–`w19` drive the same-day/short-term memory heuristic that FSRS-4.5 does not have at all. Copy the defaults from the reference implementation and add a test asserting the count and values, so a silent upstream change is caught.
- [ ] **Same-day review handling.** A card reviewed twice in one day must use the short-term component, not the long-term forgetting curve. This is the most common source of "why is this card due in 5 minutes / 5 years" bug reports.
- [ ] **Learning and relearning steps** (`[1m, 10m]`, `[10m]`) with graduation and easy-bonus behaviour — these live *above* the memory model and belong to the scheduler.
- [ ] **Interval fuzz.** Without jitter, 200 cards created on the same day come due on the same day forever. Fuzz must be deterministic per card (seeded by `guid`) so intervals do not change on every recomputation.
- [ ] **Maximum interval and minimum interval clamping**, and the "Hard" interval rule.
- [ ] **`nextIntervalPreview(rating)` for all four ratings**, because the study screen must show "1 min / 8 min / 4 d / 9 d" on the buttons before the user chooses. This is a UI-visible API that is easy to forget in the engine.
- [ ] **Conformance test against reference vectors.** Take the review sequences and expected outputs from the reference implementation (`rs-fsrs` / `py-fsrs` test suites) and assert our engine reproduces them within float tolerance. This is the difference between "we wrote an FSRS-shaped algorithm" and "we implemented FSRS". Non-negotiable — every downstream number in the app depends on it.
- [ ] **Property-based tests:** intervals are monotonic in rating (`Again ≤ Hard ≤ Good ≤ Easy`), stability never decreases on `Easy`, difficulty stays within `[1, 10]`, no `NaN`/`Infinity` for any input including a 10-year elapsed time.

**`[v3]` Split the scheduler from the algorithm**

FSRS answers "given this card and this rating, what is the new state?". It does **not** answer "which card do I show next?". The second question — queue building, daily limits, new/review interleaving, sibling burying, suspended/buried filtering, leech handling — is a separate component (`Scheduler`) with its own tests. Conflating them is why `GetDueCardsUseCase` in v2 looks deceptively small.

**DoD:** conformance vectors pass; `nextIntervalPreview` exposed; scheduler component exists with queue-order tests.

---

### PHASE 3 — Data Layer (Week 3) ✅

- [x] SQLDelight schema:
  - table `decks` (+ `type` column)
  - table `deck_settings`
  - table `cards` (+ full FSRS / Anki-equivalent fields: `due`, `stability`, `difficulty`, `retrievability`, `easeFactor`, `averageInterval`, `reps`, `lapses`, `sortField`, `position`, `cardType`)
  - table `review_logs`
  - table `card_templates`
  - table `associations`
  - table `grammar_tips`
  - table `sentence_word_links`
- [x] Repositories: `DeckRepository`, `CardRepository`, `ReviewLogRepository`, `AssociationRepository`, `GrammarTipRepository`, `SentenceWordLinkRepository`
- [x] Database migrations
- [x] DataStore — settings: `requestRetention`, `maximumInterval`, theme, LLM API key
- [x] Repository integration tests

---

### `[v3]` PHASE 3.5 — Schema & Model Retrofit (Week 4) `[NEW]`

**Why now:** every item here is a one-day change today and a multi-week migration after launch. This phase exists solely because Phases 1–3 are already done and the corrections above cannot be applied retroactively for free once real users have data.

**Tasks**

- [ ] Add `guid`, `updatedAt`, `deletedAt`, `usn` to all user-owned tables (C1); switch deletes to soft deletes.
- [ ] Introduce `notes` and `note_types`; make `cards.note_id` mandatory; migrate existing `front`/`back` into note fields.
- [ ] Replace polymorphic `due: Long` with `due_at: Instant` (epoch millis); add `collection_created_at` to settings (C2).
- [ ] **Drop** `retrievability` and `average_interval` as stored columns; compute on read (add `retrievability_at_last_review` if statistics need the snapshot).
- [ ] Add `queue_state` and `flags` to `cards`.
- [ ] Split `deck_settings` into `deck_config` (shared preset) + `deck_settings` (per-deck type/config link); add `learning_steps`, `relearning_steps`, `fsrs_parameters`, `review_order`, `bury_siblings`, `leech_threshold`.
- [ ] Extend `review_logs` with `state_before`, `stability_before`, `difficulty_before`, `duration_ms`, `review_kind`, `fsrs_version`, `parameters_hash`.
- [ ] Move `associations.card_id` and `grammar_tips.card_id` → `note_id`.
- [ ] Add `sentence_word_links.lemma`, `start_index`, `end_index`, `resolution`; make `word_card_id` nullable.
- [ ] Add partial unique index: one favorite association per note.
- [ ] Add `media` table (reserved).
- [ ] **Move the LLM API key out of DataStore into Keystore/Keychain** (C6) via `expect/actual` `SecureStorage`.
- [ ] Add indexes for the queries the study session will actually run: `(deck_id, queue_state, due_at)`, `(deck_id, queue_state, position)`, `(note_id)`, `(card_id, is_favorite)`.
- [ ] Seed a large synthetic collection (50 000 cards, 500 000 review logs) as a test fixture.

**DoD**

- Migration test upgrades a fixture DB from the current schema to the new one with zero data loss.
- Query benchmark: "cards due today" for a 50 000-card collection returns in < 50 ms on a mid-range device.
- No plaintext secret remains on disk (verified by inspecting the DataStore file).

**Risk:** this phase produces no user-visible progress and will feel like a detour. It is the cheapest insurance in the plan; skipping it is a decision to pay 5–10× later.

---

### `[v3]` PHASE 4 — Design System & App Shell (Week 5) `[NEW]`

**Why:** nine screens are specified as bullet lists of content. Without a shared foundation each will invent its own spacing, its own error state, its own loading spinner, and the app will look assembled rather than designed — and every later screen will be slower to build, not faster.

**Tasks**

- [ ] Theme: colour tokens for light **and** dark, type scale, spacing scale, elevation, shape. Dark mode is table stakes for an app used in bed.
- [ ] Core components: `DeckCard`, `StatTile`, `PrimaryButton`, `RatingButtonRow`, `SectionHeader`, `EmptyState`, `ErrorState`, `LoadingSkeleton`, `Chip`, `BottomSheet`.
- [ ] **Icon set** replacing the emoji placeholders (C4).
- [ ] Navigation graph for all 9 routes, with deep links and predictive back on Android.
- [ ] UI state convention: one sealed `UiState<T>` (`Loading | Empty | Content | Error`) used by every screen so states cannot be forgotten.
- [ ] ViewModel/`StateFlow` convention shared across platforms; one worked example.
- [ ] i18n scaffolding + Polish and English string files (C3, D-06).
- [ ] Accessibility baseline: minimum touch target, focus order, dynamic type sanity check (C4).
- [ ] Screenshot-test harness so future UI changes produce visible diffs.
- [ ] `[v3]` **Onboarding flow** — the plan has no first-run experience at all: choose native language, choose target language, choose daily goal, notification permission ask (iOS requires an explicit prompt; asking at the wrong moment loses ~half the opt-ins), optional consent for AI generation (C6).

**DoD:** every component renders in a gallery screen in both themes at 100 % and 200 % font scale; navigation between all 9 routes works with placeholder screens; onboarding runs end to end.

---

### `[v3]` PHASE 5 — Bundled Content & Importer (Weeks 6–7)

*(was v2 Phase 4, minus CSV/`.apkg` which moved to Phase 11)*

**Tasks**

- [ ] Decide `[DECIDE] D-04` (content source) and `[DECIDE] D-06` (UI languages).
- [ ] Author/generate + **native-speaker review** the content:
  - German: sentence deck + matching word deck
  - English: sentence deck + matching word deck
- [ ] Pre-generate associations and grammar tips for all bundled cards; review them for quality and safety (C7) before they ship.
- [ ] Define the bundled-deck schema `schemaVersion: 1` (see revised schema above); write the JSON-Schema file.
- [ ] `BundledDeckImporter`: parse `grammarTips`, `associations`, `wordLinks`; resolve `wordCardRef` via the lemma rules; set `deck.type`, languages, `contentVersion`.
- [ ] Word decks imported **before** sentence decks; whole operation transactional and idempotent.
- [ ] Content-update path: re-import on `contentVersion` bump without resetting review progress (match on `guid`).
- [ ] Build-time content linter wired into CI.
- [ ] NDJSON streaming path if the benchmark fails.

**Acceptance criteria**

- Fresh install → German sentence deck and word deck present, every `wordCardRef` resolved, no unresolved links.
- Re-running the importer changes nothing (idempotency test).
- Bumping `contentVersion` and adding 50 cards preserves `due`, `stability`, `reps` on all pre-existing cards (regression test — this is the test that protects every future content update).
- 5 000-card import < 5 s on a mid-range device, with progress shown.
- Content linter fails the build on a deliberately broken fixture deck.

**Scope target `[DECIDE]`:** how many cards per bundled deck? Recommend **300–500 sentences + 800–1 200 words per language** for A1–A2 — enough to be useful for a month, small enough to review by hand. Set the number now; it drives the content-authoring effort more than any code decision in this plan.

---

### `[v3]` PHASE 6 — Domain / Use Cases (Week 8)

*(was v2 Phase 6, moved ahead of the association engine because the study loop depends on it and the association engine does not)*

**Scheduler-critical (needed for the walking skeleton)**

- [ ] `GetDueCardsUseCase` — queue building: daily new/review limits, `newCardGatherPriority`, `reviewOrder`, sibling burying, suspended/buried exclusion, day cutoff (C2).
- [ ] `ScheduleReviewUseCase` — apply rating → FSRS → persist card + write `ReviewLog` **in one transaction**.
- [ ] `UndoLastReviewUseCase` `[v3]` — **missing from v2 entirely.** Users mis-tap constantly during a session; Anki users expect undo and its absence reads as a bug. Requires the review log to be authoritative enough to restore the previous card state, which is why `stability_before`/`difficulty_before` were added in Phase 3.5.
- [ ] `GetStudyStatsUseCase`.

**Content management**

- [ ] `CreateDeckUseCase` / `UpdateDeckUseCase` / `DeleteDeckUseCase`
- [ ] `CreateCardUseCase` / `UpdateCardUseCase` / `DeleteCardUseCase`
- [ ] `AddGrammarTipUseCase` / `RemoveGrammarTipUseCase` / `ReorderGrammarTipsUseCase`
- [ ] `LinkWordToSentenceUseCase` / `UnlinkWordFromSentenceUseCase` / `GetLinkedWordDetailsUseCase`
- [ ] `[v3]` `SuspendCardUseCase` / `BuryCardUseCase` / `SetFlagUseCase`
- [ ] `[v3]` `ChangeDeckTypeUseCase` — with the hide-don't-delete rule and its warning payload
- [ ] `[v3]` `SearchCardsUseCase` — the browser needs a defined query language. Recommend a small subset of Anki's: free text, `deck:`, `tag:`, `is:due`, `is:new`, `is:suspended`, `added:7`. Back it with an FTS5 virtual table over `sortField` — full-table `LIKE` scans will not survive a 50 000-card collection

**DoD:** every use case has unit tests with a fake repository; `ScheduleReviewUseCase` has a transactional-rollback test (simulated crash between card update and log write leaves the DB consistent).

---

### `[v3]` PHASE 7 — Walking Skeleton: Dashboard → Study → Summary (Weeks 9–10)

*(v2 Screens 1, 3, 4, pulled forward)*

**Goal:** by the end of this phase the app is genuinely usable for learning German, offline, on both platforms. Everything after this thickens a working product rather than assembling parts.

#### Screen 1: Dashboard — `/home`

- Deck list with summary: name, language icon, deck type badge, due today, new cards, overdue cards
- `+ New deck`
- Daily progress bar
- Day streak
- Quick `Study everything`
- `[v3]` Empty state (no decks), offline indicator, pull-to-refresh counts
- `[v3]` Streak definition must be specified: does it break at the day cutoff? Does a 1-card day count? Is there a freeze/grace day? Undefined streaks generate support mail

#### Screen 3: Study Session — `/study/{deckId}`

For sentence cards in linguistic decks:

```text
💡 Show association | 📘 Grammar tips (2)
🔗 Ich  🔗 gehe  🔗 heute  🔗 Abend  🔗 ins  🔗 Kino
```

*(`[v3]` emoji above are placeholders — ship real icons, see C4)*

- Grammar tips section is hidden by default
- Linked words open quick previews with translation, associations, grammar tips
- Visible sections depend on `DeckType`:
  - `Simple` → front/back only
  - `TextWithAssociations` → association section for full text
  - `Linguistic` → associations + grammar tips + linked words

`[v3]` Additions:

- [ ] **Next-interval preview on each rating button** ("1 min · 8 min · 4 d · 9 d"). This is the main feedback loop that makes an SRS feel trustworthy, and it requires the engine API added in Phase 2's backlog.
- [ ] **Undo** (toolbar + swipe), wired to `UndoLastReviewUseCase`.
- [ ] Card actions: suspend, bury, flag, edit, delete — reachable without leaving the session.
- [ ] Keyboard/hardware shortcuts (1–4 to rate, space to flip) — free on Desktop, useful with an iPad keyboard.
- [ ] **Answer timing** captured into `ReviewLog.durationMs`, with an idle cap (a card left open for 40 minutes must not record 40 minutes of study time).
- [ ] Session state survives process death: killing the app mid-session must not lose the reviews already rated.
- [ ] TTS playback button for the target-language side (`expect/actual`).
- [ ] "No cards due" state that offers ahead-of-schedule study or new-card-limit increase rather than a dead end.

#### Screen 4: Session Summary

- reviewed cards, rating distribution, retention, session duration
- `[v3]` "Again" cards listed for immediate re-study; share/streak affirmation; next-due summary ("next 12 cards in 3 h")

**Acceptance criteria for the phase**

- A user can install the app, complete onboarding, study 20 German cards, close the app, and see correct due counts the next day.
- Ratings persist across process death.
- The whole flow works with the device in airplane mode.
- Cold start → first card < 2 s on a mid-range device (success metric).

---

### `[v3]` PHASE 8 — Association Engine (Week 11)

*(was v2 Phase 5)*

- [ ] `AssociationPromptBuilder` with three versioned templates (word / sentence / whole text) and `[NATIVE_LANGUAGE]` parameterised.
- [ ] `GenerateAssociationUseCase` — branch by `DeckType` + `CardType`; strict JSON parsing with one retry.
- [ ] `SaveFavoriteAssociationUseCase` (enforcing one favorite per note).
- [ ] `AssociationViewModel`.
- [ ] `[v3]` Local cache keyed on `(text, L1, L2, promptVersion)`; cache hit = no network call.
- [ ] `[v3]` Offline queue with retry and a visible pending state.
- [ ] `[v3]` Moderation pass + fallback message (C7); "report association" action.
- [ ] `[v3]` Rate limiting and quota UI (D-02); clear messaging when the free quota is exhausted.
- [ ] `[v3]` Timeout (10 s) + cancellation when the user leaves the screen; a generation must never block rating a card.
- [ ] `[v3]` "Regenerate" and "generate 3 variants" — mnemonics are personal; the first output is often not the one that sticks.
- [ ] `[v3]` AI-generated badge on generated associations (C6).
- [ ] Error handling: no network, API error, malformed response, moderation block, quota exceeded — five distinct, tested states, not one generic toast.
- [ ] Configure API key in settings (now via `SecureStorage`).
- [ ] Unit tests with a fake LLM client; contract test against a recorded real response.

**DoD:** generating an association for a card with no network shows the queued state and completes on reconnect; a deliberately unsafe input is blocked and logged; p90 latency measured and recorded.

---

### `[v3]` PHASE 9 — Card Detail, Editor, Browser (Weeks 12–13)

*(v2 Screens 5, 6, 7)*

#### Screen 5: Card Detail / Association Manager

- card front/back, favorite association, generated association history, grammar tips list, linked words section, add / edit / remove / reorder
- `[v3]` FSRS state panel (stability, difficulty, current retrievability, due date, reps, lapses) — power users want it and it is invaluable for our own debugging
- `[v3]` Review history list from `review_logs`

#### Screen 6: Card Editor

- `Front`, `Back`, `Association`, `Grammar tips`, `Linked words`, Deck selector, Tags, Live preview, `Save` / `Save and add another`
- Field visibility depends on `DeckType`.
- `[v3]` **Duplicate detection** on `Front` within the deck — Anki does this and users rely on it
- `[v3]` Note-type selector (Basic / Basic-and-reversed / Sentence), now that notes exist
- `[v3]` Word-link editor: tap a word in the sentence to link it, with the disambiguation sheet for `Ambiguous` cases
- `[v3]` Unsaved-changes guard on back navigation
- `[v3]` "Generate association" inline, so card creation and mnemonic creation are one flow

#### Screen 7: Card Browser

- card list with preview, FSRS state, due / position preview
- icons: association / grammar tips / linked words *(real icons, C4)*
- `[v3]` Search bar backed by `SearchCardsUseCase` + FTS5, with the documented query subset
- `[v3]` Sort by any column; filter by tag, deck, state, flag
- `[v3]` **Multi-select + bulk actions**: change deck, add/remove tag, suspend, flag, delete, reposition new cards. Without bulk actions a browser is a viewer
- `[v3]` Paging (`PagingSource` or a windowed query) — a 50 000-row list cannot be a single `LazyColumn` over a full-table query

**DoD:** browser opens and scrolls a 50 000-card collection at 60 fps; search returns in < 200 ms; bulk-suspending 1 000 cards completes in one transaction.

---

### `[v3]` PHASE 10 — Deck Overview, Statistics, Settings (Week 14)

*(v2 Screens 2, 8, 9)*

#### Screen 2: Deck Overview — `/deck/{id}`

- Deck name, statistics, `Start review`, `Browse cards`, `Add card`, deck settings section including `DeckType`
- `[v3]` Subdeck tree with rolled-up counts; rename/move/delete with a clear warning about what is destroyed
- `[v3]` Deck options preset picker (`DeckConfig`), with "this preset is used by 4 decks" shown before an edit

#### Screen 8: Statistics

- review forecast, activity heatmap, retention, stability distribution, streak, review count, study time
- `[v3]` **Define "retention" precisely**: *true retention* = `Good+Easy / all reviews` on cards in the `Review` state, over a selectable window, excluding learning-state reviews. Reported per interval bucket (young < 21 d, mature ≥ 21 d). Every FSRS app defines this slightly differently and an ambiguous number is worse than none
- `[v3]` Forecast must respect daily limits and the day cutoff, otherwise it lies
- `[v3]` Card-count breakdown by state (new / learning / young / mature / suspended)
- `[v3]` "Difficulty vs stability" scatter for the power user; hidden behind an "advanced" toggle
- `[v3]` All charts need an empty state — a new user has no data and a blank chart area reads as a crash

#### Screen 9: Settings

- FSRS settings, association settings, theme, notifications, import/export, reset progress, about
- **Deck-level settings:** deck type picker, per-type sub-settings
- `[v3]` Native language (L1) and UI language pickers (C3)
- `[v3]` Day cutoff hour, timezone (C2)
- `[v3]` **Local backup / restore** — separate from Anki interop and far more important: a single-file export of the whole collection, plus an automatic weekly backup kept locally (Anki keeps rolling backups; users who lose a year of reviews do not come back). `Reset progress` without a backup path is a data-loss feature
- `[v3]` Privacy: AI-generation consent toggle, analytics opt-in (D-08), "delete all my data"
- `[v3]` Attributions screen if any licensed content is used (D-04)
- `[v3]` Diagnostics: app version, DB schema version, FSRS version, "export debug log"

---

### `[v3]` PHASE 11 — Interop: CSV & Anki Import/Export (Weeks 15–17)

*(moved out of v2 Phase 4 — this is the largest single risk item in the plan and it blocks nothing)*

**Why it moved:** `.apkg` is not one format but three (`collection.anki2` schema 11, `collection.anki21` schema 11, `collection.anki21b` schema 18 with zstd compression and protobuf-encoded configuration), plus a media mapping that is JSON in the legacy formats and a protobuf `MediaEntries` message in the current one. Add the polymorphic `due` semantics, note types, templates and deck configs, and a faithful importer is realistically 2–3 weeks — not the two checkboxes v2 allocates to it inside a one-week phase.

**CSV (do first — 2 days, covers most real user need)**

- [ ] Import `front; back; deck; tags; due` with delimiter/encoding detection (BOM, semicolon vs comma — Excel in Polish locale writes semicolons), a preview-and-map step, and a dry run showing "412 new, 8 duplicates, 2 errors" before committing.
- [ ] Export to CSV, always including `due` (as ISO-8601, not an opaque integer — a CSV a human can read is worth more than one only we can parse).

**`.apkg`**

- [ ] `ExportDeckUseCase` → `.apkg`. **Export first**: it is far easier than import, it gives users an escape hatch (which materially reduces the risk of adopting a new app), and it produces the fixtures needed to test the importer.
- [ ] Import: support **legacy 1 and 2 first** (uncompressed SQLite, schema 11); add `anki21b`/zstd/schema 18 second, behind a clear "unsupported export version — please export as an older format" message until it lands.
- [ ] Map `due`, `ease` (`factor`, permille), `reps`, `lapses`, `ivl`, `queue`, `type`, `flags`, `odue`/`odid`, and note `guid`s through the `AnkiDueCodec` (C2, `collectionCreatedAt`).
- [ ] Map note types and templates; a note with 2 cards must import as 2 cards.
- [ ] Media: import files, rewrite references, or explicitly drop with a warning — silently losing images is worse than refusing.
- [ ] Conflict policy on re-import: match on `guid`, then `update | skip | duplicate`, chosen by the user.
- [ ] Implement from the documented format only — **do not copy AGPL/GPL Anki or AnkiDroid code** into the app (see *Content sourcing*).

**Round-trip test (v2's requirement, extended)**

- export deck → re-import deck → verify `due`, `stability`, `difficulty`, `reps`, `lapses` unchanged
- `[v3]` extended to cover **all four card states** (new/learning/review/relearning), suspended and buried cards, notes with 2 cards, tags with spaces, unicode content, an empty deck, and a 20 000-card deck (performance)
- `[v3]` **third-party fixture corpus**: 5–10 real `.apkg` files exported from different Anki versions, committed as test fixtures. Testing only against our own exports proves nothing about interop

**DoD:** a deck exported from current Anki desktop imports with correct due dates for every card state, verified by re-exporting and diffing against Anki's own values.

---

### `[v3]` PHASE 12 — Notifications & Instrumentation (Week 18)

*(was v2 Phase 8)*

- [ ] Push notification scheduling
- [ ] Daily reminder with the number of cards to study
- [ ] No cards → do not send
- [ ] `[v3]` **Platform split made explicit**: Android uses WorkManager + a notification channel and must handle exact-alarm restrictions on API 31+ and OEM battery killers (Xiaomi/Huawei/Samsung silently kill background work — this is the single most common "notifications don't work" complaint for Android SRS apps). iOS has **no background scheduler for this**: notifications must be pre-scheduled as local notifications with `UNUserNotificationCenter`, which caps at **64 pending notifications**, so the app schedules a rolling window (e.g. the next 14 days) and refreshes it on every app open. The card count in an iOS notification is therefore a *prediction* made at scheduling time, not a live value — either accept staleness or use a generic wording.
- [ ] `[v3]` Permission request at the right moment (after the first completed session, not at first launch — the difference in opt-in rate is large).
- [ ] `[v3]` Quiet hours, per-day reminder time, snooze; deep link straight into the study session.
- [ ] `[v3]` Instrumentation for the success metrics: session start, cards reviewed, association generated/saved, generation latency and errors, crash-free rate. Opt-in, privacy-reviewed (D-08). Never log card content.

---

### `[v3]` PHASE 13 — Polish, Performance & Test Hardening (Weeks 19–20)

*(was v2 Phase 9)*

- [ ] Card flip animation, association reveal, grammar tips reveal, screen transitions
- [ ] Unit tests: FSRS engine, scheduler, association engine, `GrammarTipRepository`, `SentenceWordLinkRepository`, `AnkiDueCodec`, importers
- [ ] UI tests: add card → session → rate → save association; add sentence card with grammar tips and word links; verify linked word preview
- [ ] `[v3]` UI tests also for: undo, suspend/bury, deck-type switch (data preserved), offline association, onboarding, import
- [ ] `[v3]` **Performance profiling with concrete budgets**, not "profiling" as an activity: cold start < 2 s; 50 000-card browser scroll at 60 fps; due-query < 50 ms; import 5 000 cards < 5 s; APK/IPA size budget; memory ceiling during import
- [ ] `[v3]` Baseline Profile for Android (measurable cold-start win for a Compose app, low effort)
- [ ] `[v3]` Battery/thermal check on a 30-minute study session
- [ ] `[v3]` **Localisation QA pass**: every screen at 200 % font scale in both languages, German compound words in the UI, RTL sanity even if no RTL language ships (cheap now, expensive later)
- [ ] `[v3]` Monkey/fuzz testing on the study session and importer
- [ ] `[v3]` **Data-loss drill**: force-kill during import, during a session, during a migration; verify recovery in every case. Data loss is the only unrecoverable reputational failure for an SRS app
- [ ] Accessibility final audit (C4 has been enforced per-phase; this is verification, not first contact)
- [ ] API error handling review

---

### `[v3]` PHASE 14 — Beta & Release (Weeks 21–22) `[NEW]`

v2 ends at "polish and testing", with no path to users. This phase is what stands between a finished build and a shipped app, and it routinely takes longer than teams expect — Apple review alone can consume a week if the AI-content questionnaire is answered carelessly.

**Compliance and store**

- [ ] Privacy policy + terms, hosted, linked in-app and in both store listings (required — an app that talks to an LLM API cannot ship without one).
- [ ] Google Play **Data Safety** form and Apple **App Privacy** nutrition labels, both consistent with what the app actually sends (C6).
- [ ] Apple: AI-generated content questions in App Review; declare the moderation and reporting mechanism (C7). Age rating (D-07).
- [ ] Google Play: target-API compliance, Play Integrity if the proxy needs it, declaration for `POST_NOTIFICATIONS` and any exact-alarm use.
- [ ] Account/data deletion path (both stores require it if any account exists; for a local-only app, document that no account exists).
- [ ] DPA with the LLM vendor; GDPR record of processing; sub-processor list.
- [ ] Trademark check on the app name; avoid "Anki" in title/subtitle.
- [ ] Open-source license attribution screen (SQLDelight, Koin, Ktor, etc.).

**Release engineering**

- [ ] Signing keys in CI with secure storage; reproducible release builds.
- [ ] Versioning scheme and a changelog users can read.
- [ ] Crash reporting verified in a release build with symbol/dSYM upload.
- [ ] Store assets: icon, screenshots (both platforms, several device sizes), feature graphic, descriptions in PL and EN, ASO keywords.
- [ ] **Closed beta**: TestFlight + Play internal testing, 20–50 real learners, minimum 2 weeks. This is the only phase where FSRS behaviour meets real memories.
- [ ] Feedback channel + a triage rhythm during beta.
- [ ] Staged rollout (Play 5 % → 20 % → 50 % → 100 %) with a crash-rate gate at each step, and a documented rollback plan.
- [ ] Kill switch for association generation (remote config) in case of cost or safety incidents.

---

### `[v3]` PHASE 15 — Post-launch: FSRS Optimiser & v1.1 `[NEW]`

v2 says review logs are collected "for future FSRS optimization" but never plans the optimisation. This is the feature that makes the app measurably better than a generic SRS, and the data to do it only exists after launch.

- [ ] **Local FSRS parameter optimisation** from the user's own `review_logs` — offered once a user has ~1 000 reviews. Run on-device (it is a small optimisation problem) so no review history leaves the phone: a genuine privacy advantage worth stating in the store listing.
- [ ] Show before/after predicted workload so the user understands what changed.
- [ ] Aggregate (opt-in, anonymised) retention analysis to validate that our implementation schedules as well as the reference.
- [ ] v1.1 candidates in priority order: `.apkg` import if deferred (D-05), Desktop target (D-01), TTS everywhere, image/audio cards, additional languages, sync.

---

## `[v3]` Risk Register

| # | Risk | Likelihood | Impact | Mitigation | Owner phase |
|---|---|---|---|---|---|
| R1 | FSRS implementation deviates subtly from the reference; scheduling is wrong in ways nobody notices for months | Medium | **Critical** — every number in the app is downstream | Conformance tests against reference vectors; property tests; log `fsrsVersion` | 2 |
| R2 | `.apkg` import proves far larger than estimated and consumes the schedule | **High** | High | Moved to Phase 11; export-first; legacy formats first; `Should Have` so it can be cut | 11 |
| R3 | LLM cost or abuse on an open proxy | Medium | High | Aggressive caching, pre-generated bundled associations, per-install quota, kill switch, D-02 decision early | 8 |
| R4 | App Store rejection over AI-generated content or missing privacy policy | Medium | High (weeks of delay) | Moderation + reporting shipped in Phase 8; privacy work started in Phase 14, not finished there | 8, 14 |
| R5 | Compose Multiplatform iOS gaps (text input, scroll feel, accessibility) surface late | Medium | High | Build the walking skeleton on **both** platforms in Phase 7, not Android-first; test on device every phase | 7 |
| R6 | Content authoring (1 000+ reviewed cards × 2 languages) is underestimated and becomes the critical path | **High** | Medium | Decide deck size early; start authoring in parallel from Phase 5; LLM-assisted with human review | 5 |
| R7 | Data loss on migration or import; users lose review history | Low | **Critical** — unrecoverable trust loss | Migration tests, transactional imports, automatic local backups, data-loss drill | 3.5, 5, 13 |
| R8 | Sync retro-fit demanded after launch onto non-sync-ready schema | Medium | High | C1 applied in Phase 3.5 | 3.5 |
| R9 | Mnemonic generation produces offensive content in a store review or a screenshot | Medium | High | C7: negative constraints + moderation + report action | 8 |
| R10 | Association quality is mediocre and the differentiating feature falls flat | Medium | **High** — it is the product's reason to exist | Prompt A/B via `promptVersion`; measure favorite-save rate; multi-variant generation; beta feedback | 8, 14 |
| R11 | Android OEM battery management silently kills reminders | High | Medium | Documented workaround in-app; do not rely on notifications as the only retention mechanism | 12 |
| R12 | Third-party corpus licensing challenged | Low | High | Resolve D-04 before authoring; prefer self-generated content | 5 |

---

## `[v3]` Estimates and Critical Path

v2 plans 14 weeks. The revised plan is **22 weeks** for the same scope plus the additions above, assuming **one full-time developer** plus part-time content and design help. The increase is not scope creep in the pejorative sense — roughly half of it is work that v2 implies but does not schedule (`.apkg` realism, release, design foundation, retrofit), and the rest is risk reduction.

| Block | Weeks | Notes |
|---|---|---|
| Phases 1–3 (done) | 3 | ✅ |
| Phase 3.5 retrofit | 1 | Insurance; cheapest week in the plan |
| Phase 4 design system | 1 | Pays for itself across phases 7, 9, 10 |
| Phase 5 bundled content | 2 | **Content authoring may exceed this — see R6** |
| Phase 6 use cases | 1 | |
| Phase 7 walking skeleton | 2 | **First demo-able product at week 10** |
| Phase 8 associations | 1 | |
| Phases 9–10 remaining screens | 3 | |
| Phase 11 interop | 3 | Cuttable to 0.5 (CSV only) if schedule pressure hits |
| Phase 12 notifications | 1 | |
| Phase 13 hardening | 2 | |
| Phase 14 beta & release | 2 | Includes a 2-week beta running in parallel with fixes |
| **Total** | **22** | |

**Critical path:** Phase 3.5 → 4 → 6 → 7 (walking skeleton). Everything else can slip without stopping the demo.

**Parallelisable from week 5:** content authoring (Phase 5), design system (Phase 4), store assets and privacy policy (Phase 14). If a second person is available, content is the highest-value thing to hand off.

**If the schedule must fit 16 weeks**, cut in this order: `.apkg` import (→ v1.1), statistics beyond basic counts, card-browser bulk actions, TTS, one of the two bundled languages. Do **not** cut Phase 3.5, the conformance tests, backups, or the beta.

---

## `[v3]` Release & Compliance Checklist

Kept as one list so nothing is discovered in week 22:

- [ ] Privacy policy live and linked (in-app + both stores)
- [ ] Play Data Safety + Apple App Privacy completed and accurate
- [ ] DPA with LLM vendor signed; no-training commitment documented
- [ ] In-app consent for AI generation; app fully functional without it
- [ ] AI-generated content labelled in the UI
- [ ] Moderation + user reporting live
- [ ] Age rating set; not directed at children (D-07)
- [ ] Third-party content attributions screen (if D-04 uses a licensed corpus)
- [ ] Open-source license attributions
- [ ] No AGPL/GPL code in the closed-source app
- [ ] App name trademark-checked; "Anki" only in the description
- [ ] Account/data deletion path documented
- [ ] Crash reporting live with symbol upload
- [ ] Automatic local backups enabled by default
- [ ] Staged rollout configured with crash-rate gates
- [ ] Remote kill switch for LLM generation
- [ ] Rollback plan written down

---

## Implementation Priorities (MoSCoW)

`[v3]` revisions marked in the Change column.

| Feature | Priority | `[v3]` Change |
|---|---|---|
| FSRS engine (Again/Hard/Good/Easy) | Must Have | — |
| **FSRS conformance tests vs reference vectors** | **Must Have** | `[v3]` new |
| **Scheduler: daily limits, queue order, sibling burying** | **Must Have** | `[v3]` new — was implicit in `GetDueCardsUseCase` |
| **Undo last review** | **Must Have** | `[v3]` new — missing from v2 |
| **Local backup & restore** | **Must Have** | `[v3]` new — data loss is unrecoverable |
| **Onboarding (L1/L2, daily goal, permissions)** | **Must Have** | `[v3]` new |
| **App localisation (PL/EN UI)** | **Must Have** | `[v3]` new |
| **Sync-ready schema (guid/updatedAt/tombstones)** | **Must Have** | `[v3]` new — enables sync later without migration pain |
| **Note→Cards model** | **Must Have** | `[v3]` new — required for reverse cards and `.apkg` fidelity |
| Bundled language decks (DE, EN) — sentences + word decks | Must Have | — |
| **Pre-generated associations in bundled decks** | **Must Have** | `[v3]` new — makes offline day-one work and cuts LLM cost |
| Study session with FSRS intervals | Must Have | — |
| **Next-interval preview on rating buttons** | **Must Have** | `[v3]` new |
| Association generation (LLM API) | Must Have | — |
| **Association moderation + reporting** | **Must Have** | `[v3]` new — store requirement |
| Save favorite association | Must Have | — |
| Association section hidden by default | Must Have | — |
| Grammar tips per card (0..n) | Must Have | Now per **note** |
| Deck type selection: Linguistic / TextWithAssociations / Simple | Must Have | + hide-don't-delete rule |
| Full Anki-equivalent card fields | Must Have | + correct `due` semantics |
| `due` field preserved on import/export | Must Have | — |
| **Suspend / bury / flags / leech handling** | **Should Have** | `[v3]` new |
| Word links for sentence cards | Should Have | + lemma/ambiguity rules |
| Card browser + Association Manager | Should Have | + search (FTS5) and bulk actions |
| Statistics | Should Have | + precise retention definition |
| CSV import | Should Have | Split from `.apkg` |
| `.apkg` **export** | Should Have | `[v3]` split — easier, high user value as an escape hatch |
| `.apkg` **import** | Could Have (v1.1) | `[v3]` downgraded — largest risk, blocks nothing (D-05) |
| Notifications | Should Have | + platform-specific limits |
| Manual association entry | Should Have | — |
| **TTS pronunciation** | **Should Have** | `[v3]` new — near-free, high value for a language app |
| **Subdecks** | **Should Have** | `[v3]` new |
| Generated association history | Could Have | — |
| **Reverse cards (recognition + production)** | **Could Have** | `[v3]` new — free once notes exist |
| **On-device FSRS parameter optimisation** | **Could Have (v1.1)** | `[v3]` new |
| Custom local model | Could Have (v2) | — |
| Cards with image/audio | Could Have | Schema reserved in Phase 3.5 |
| **Desktop target** | **Could Have (v1.1)** | `[v3]` new (D-01) |
| Synchronization | Won't Have (v1) | Schema made sync-ready |
| Filtered / custom-study decks | Won't Have (v1) | `[v3]` explicit non-goal |
| Community deck sharing | Won't Have (v1) | `[v3]` explicit non-goal |

---

## Key Technical Decisions

- **LLM via API instead of a local model (v1)** — faster delivery, no model overhead on device
- **Association hidden by default** — user should first try to recall independently
- **Grammar tips hidden by default** — tips support recall, not replace it
- **Favorite association as a single record per card** — simpler design
- **Grammar tips as a 1..n relation per card** — one sentence may require multiple notes
- **Bundled sentence decks by default, word decks separate** — better context for grammar and reuse
- **Each word in a sentence can be linked to a word-deck card** — enables in-context access to word-specific associations and grammar
- **Deck type as a first-class setting** — unified data model, adaptive UI
- **Anki-equivalent card fields are first-class fields** — `sortField`, `tags`, `due`, `easeFactor`, `difficulty`, `stability`, `averageInterval`, `lapses`, `reps`, `retrievability`, `position`
- **`due` is always preserved on import/export** — necessary to keep learning progress after re-import
- **AssociationPromptBuilder as a separate class** — easier testing and prompt replacement
- **SQLDelight instead of Room** — native KMP support, type-safe queries
- **Bundled content in assets** — no network required on first launch
- **Review logs from the start** — needed for future FSRS optimization and effectiveness analysis

### `[v3]` Added decisions

- **Note → Cards model from the start** — reverse cards, sibling burying and faithful `.apkg` import are impossible without it, and adding notes after launch means migrating every user's collection.
- **Sync-ready schema without sync** — `guid`, `updatedAt`, tombstones and `usn` cost one day now and remove the largest foreseeable migration.
- **`dueAt: Instant` internally; Anki's polymorphic `due` only at the codec boundary** — one untyped `Long` cannot represent a queue position, an epoch second and a day offset at the same time.
- **Derived values are computed, not stored** — `retrievability` is a function of time and is stale the instant it is written.
- **Scheduler separated from the FSRS algorithm** — "what is the new state" and "what do I show next" are different problems with different tests.
- **Conformance testing against the reference implementation** — the only way to know we implemented FSRS rather than something FSRS-shaped.
- **Deck options as a shared preset** — matches user expectation from Anki and avoids editing the same limit ten times.
- **Deck type switching hides, never deletes** — the type picker must not be a data-loss trap.
- **Pre-generated associations for bundled content** — day-one offline value, cost paid once at build time instead of per user.
- **Secrets in Keystore/Keychain, never DataStore** — DataStore is plaintext on disk.
- **Export before import for `.apkg`** — easier, gives users an escape hatch that lowers the cost of trying the app, and generates the fixtures the importer needs.
- **Walking skeleton before feature completeness** — the riskiest component (FSRS) must be exercised by a human in week 10, not week 20.
- **Local backups on by default** — the only unrecoverable failure mode for an SRS app is losing review history.

---

## Directory Structure

```text
project/
├── shared/
│   ├── src/commonMain/kotlin/
│   │   ├── domain/
│   │   │   ├── fsrs/
│   │   │   │   ├── FsrsEngine.kt
│   │   │   │   ├── FsrsParameters.kt        # [v3] w0..w20, pinned defaults
│   │   │   │   └── ForgettingCurve.kt       # [v3]
│   │   │   ├── scheduler/                   # [v3] queue building, limits, burying
│   │   │   ├── association/
│   │   │   ├── grammar/
│   │   │   ├── wordlink/
│   │   │   ├── model/
│   │   │   │   ├── Deck.kt
│   │   │   │   ├── DeckConfig.kt            # [v3] shared preset
│   │   │   │   ├── DeckSettings.kt
│   │   │   │   ├── DeckType.kt
│   │   │   │   ├── Note.kt                  # [v3]
│   │   │   │   ├── NoteType.kt              # [v3] templates
│   │   │   │   ├── Card.kt
│   │   │   │   ├── Association.kt
│   │   │   │   ├── GrammarTip.kt
│   │   │   │   ├── SentenceWordLink.kt
│   │   │   │   ├── Media.kt                 # [v3] reserved
│   │   │   │   └── ReviewLog.kt
│   │   │   └── usecase/
│   │   ├── data/
│   │   │   ├── database/
│   │   │   ├── repository/
│   │   │   ├── api/
│   │   │   ├── bundled/
│   │   │   ├── interop/                     # [v3] AnkiDueCodec, ApkgReader/Writer, CsvCodec
│   │   │   ├── backup/                      # [v3] local backup/restore
│   │   │   └── settings/
│   │   ├── ui/                              # [v3] Compose MP shared UI
│   │   │   ├── theme/                       # [v3] design system
│   │   │   ├── components/                  # [v3]
│   │   │   ├── navigation/                  # [v3]
│   │   │   └── screens/                     # [v3]
│   │   ├── platform/                        # [v3] expect/actual
│   │   │   ├── SecureStorage.kt             # [v3] Keystore / Keychain
│   │   │   ├── Notifications.kt             # [v3] WorkManager / UNUserNotificationCenter
│   │   │   └── TextToSpeech.kt              # [v3]
│   │   ├── i18n/                            # [v3]
│   │   └── di/
│   ├── src/commonTest/kotlin/
│   │   └── fsrs/vectors/                    # [v3] reference conformance fixtures
│   ├── src/androidMain/kotlin/
│   └── src/iosMain/kotlin/
├── content/                                  # [v3] source of bundled decks + linter
│   ├── decks/
│   ├── schema/deck.schema.json
│   └── tools/                                # linter, association pre-generator
├── androidApp/
│   └── src/main/
│       ├── assets/decks/
│       └── kotlin/ui/
├── iosApp/
│   └── iosApp/
│       └── Resources/decks/
└── docs/                                     # [v3]
    ├── adr/                                  # architecture decision records
    └── plan/                                 # this document
```

---

## `[v3]` Appendix A — FSRS Conformance Test Plan

The single most important test suite in the project.

1. **Reference vectors.** Import the review sequences and expected `(stability, difficulty, interval)` outputs from the FSRS reference implementation's own test suite. Store as JSON fixtures in `commonTest/fsrs/vectors/`.
2. **Tolerance.** Assert to 1e-4 relative on floats; document that Kotlin `Float` vs Python `float64` differences are why the tolerance exists, and consider `Double` throughout the engine to reduce drift.
3. **Sequences to cover:** all-Good, all-Again, alternating, a lapse after a mature interval, a same-day double review, a 2-year gap, a card reviewed 200 times.
4. **Parameter sets:** the pinned defaults, plus one optimised set, plus deliberately extreme values, to confirm no `NaN`/overflow.
5. **Invariants (property-based):** monotonicity in rating; difficulty ∈ [1, 10]; stability > 0; interval ≤ `maximumInterval`; `R(t=S) ≈ 0.9`.
6. **Regression lock.** Once green, snapshot our own outputs too, so an unintended change to the engine fails a test even if the reference is unavailable.

## `[v3]` Appendix B — Anki Field Mapping (import/export contract)

| Anki column | Our field | Conversion |
|---|---|---|
| `cards.id` | `card.guid` (via notes' guid + ord) | Anki card ids are creation-time ms; keep original in a side column for round-trip |
| `cards.nid` | `card.noteId` | Resolved via `notes.guid` |
| `cards.ord` | `card.templateOrdinal` | Direct |
| `cards.type` | `card.fsrsState.state` | 0→New, 1→Learning, 2→Review, 3→Relearning |
| `cards.queue` | `card.queueState` | -3→SiblingBuried, -2→UserBuried, -1→Suspended, 0→New, 1→Learning, 2→Review, 3→DayLearn, 4→Preview |
| `cards.due` | `card.position` **or** `card.fsrsState.dueAt` | **Polymorphic** — see C2 / `AnkiDueCodec` |
| `cards.ivl` | `card.fsrsState.scheduledDays` | Days; 0 for learning cards |
| `cards.factor` | `card.fsrsState.easeFactor` | Permille → float (2500 → 2.5) |
| `cards.reps` | `card.fsrsState.reps` | Direct |
| `cards.lapses` | `card.fsrsState.lapses` | Direct |
| `cards.flags` | `card.flags` | `flags mod 8` |
| `cards.odue` / `odid` | — | Filtered decks are out of scope; preserve verbatim for round-trip |
| `notes.guid` | `note.guid` | **Direct — this is the idempotency key** |
| `notes.flds` | `note.fields` | Split on `0x1F` (unit separator) |
| `notes.tags` | `note.tags` | Space-separated, leading/trailing space in Anki |
| `notes.sfld` | `note.sortField` | Direct |
| `col.crt` | `collectionCreatedAt` | Required to decode review-card `due` |

Sources: [AnkiDroid Database Structure](https://github.com/ankidroid/Anki-Android/wiki/Database-Structure), [Understanding the Anki APKG Format](https://eikowagenknecht.com/posts/understanding-the-anki-apkg-format/), [Anki Manual — Exporting](https://docs.ankiweb.net/exporting.html)

---

## Wprowadzone zmiany (v2 → v3)

### Korekty modelu danych

- **Dodano encję `Note`** (Note → 1..n Cards). Bez niej niemożliwe są karty odwrotne, ukrywanie rodzeństwa (sibling burying) ani wierny import `.apkg` — a plan jednocześnie zakładał tabelę `card_templates` i import z Anki.
- **Poprawiono rozumienie pola `due`.** W Anki jest ono polimorficzne: dla kart nowych to *pozycja w kolejce*, dla uczonych *epoch w sekundach*, dla powtórkowych *liczba dni od utworzenia kolekcji*. Trzymanie jednego `Long` i nazywanie go „Anki-compatible" zepsułoby harmonogram przy imporcie. Wprowadzono `dueAt: Instant` wewnętrznie + `AnkiDueCodec` na granicy import/eksport, oraz `collectionCreatedAt`.
- **Usunięto `retrievability` i `averageInterval` jako kolumny.** To wartości pochodne (funkcje czasu i innych pól) — zapisane są nieaktualne w chwili zapisu.
- **Dodano `QueueState`** (suspended / buried / preview) obok `CardState`, plus `flags` i obsługę leechy.
- **Dodano modele, których brakowało mimo istnienia tabel:** `ReviewLog` (z `stability_before`, `difficulty_before`, `durationMs`, `fsrsVersion`, `parametersHash` — bez nich optymalizacja FSRS jest niemożliwa) oraz `Deck` (z `sourceLanguage`, `targetLanguage`, `parentDeckId`, `contentVersion`).
- **Rozdzielono `DeckConfig` (preset współdzielony) od `DeckSettings` (per-deck)**, zgodnie z tym, czego użytkownicy Anki oczekują; dodano `learningSteps`, `relearningSteps`, `reviewOrder`, `burySiblings`, `enableFuzz`.
- **Gramatyka i asocjacje przypięte do `note`, a nie `card`** — wskazówka gramatyczna dotyczy treści, nie kierunku powtórki.
- **`SentenceWordLink`:** dodano `lemma`, zakres pozycji (`startIndex`/`endIndex` — inaczej „heute Abend" nie da się połączyć jako całość), `resolution` i nullowalny `wordCardId`; spisano reguły dla odmiany, kontrakcji, homografów i brakujących referencji.
- **Zarezerwowano tabelę `media`** i dodano indeksy pod realne zapytania sesji nauki.

### Nowe elementy przekrojowe (C1–C7)

- **Gotowość na synchronizację bez synchronizacji** — `guid`, `updatedAt`, tombstones, `usn`. Jeden dzień pracy teraz zamiast wielotygodniowej migracji po starcie; `guid` jest też kluczem idempotencji przy re-imporcie.
- **Czas i granica doby** — wszystko na `Instant`, konfigurowalna godzina przełomu doby (domyślnie 04:00), obsługa stref czasowych i DST.
- **Lokalizacja** — rozdzielono język UI, język ojczysty (L1) i język uczony (L2). Prompt miał „Polish" zaszyte na sztywno, a przykładowy deck niemiecki miał tłumaczenia po angielsku.
- **Dostępność jako wymóg przekrojowy**, nie jeden punkt w fazie 9; zalecono zastąpienie emoji (⭐📘🔗💡) prawdziwymi ikonami.
- **Offline-first** — asocjacje pregenerowane w deckach wbudowanych, cache, kolejka offline.
- **Bezpieczeństwo i RODO** — klucz API do Keystore/Keychain zamiast DataStore (plaintext!), zgoda na wysyłkę treści do LLM, DPA z dostawcą, minimalizacja danych, oznaczanie treści generowanych przez AI (AI Act).
- **Bezpieczeństwo treści** — prompt każe generować „absurdalne, emocjonalne" sceny; dodano ograniczenia negatywne, moderację i zgłaszanie.

### Zmiany w planie faz

- **Nowa faza 3.5 (retrofit schematu)** — wszystkie powyższe korekty modelu, dopóki nie ma danych produkcyjnych.
- **Nowa faza 4 (design system + szkielet aplikacji)** — motyw, komponenty, nawigacja, konwencja stanów UI, i18n, **onboarding** (którego plan w ogóle nie zawierał).
- **Przebudowano kolejność: „walking skeleton" w tygodniu 10.** W v2 pierwsza działająca sesja nauki pojawiała się dopiero w połowie 5-tygodniowego bloku UI — czyli najbardziej ryzykowny komponent (FSRS) nie byłby sprawdzony przez człowieka przez 2/3 harmonogramu.
- **Import `.apkg` przeniesiony z tygodnia 4 na fazę 11 i zdegradowany do „Could Have (v1.1)".** To trzy różne formaty (`anki2`, `anki21`, `anki21b` ze zstd i protobuf, schemat 18) — realnie 2–3 tygodnie pracy, a nie dwa checkboxy. CSV zostaje wcześniej, eksport `.apkg` przed importem.
- **Nowa faza 14 (beta i wydanie)** — plan v2 kończył się na testach, bez ścieżki do użytkowników: polityka prywatności, formularze Data Safety / App Privacy, pytania Apple o treści AI, rating wiekowy, beta z prawdziwymi uczącymi się, rollout etapowy, kill switch.
- **Nowa faza 15 (po starcie)** — lokalna optymalizacja parametrów FSRS na zebranych `review_logs`, na urządzeniu (argument prywatnościowy w store).

### Braki funkcjonalne uzupełnione

- **Cofanie ostatniej oceny (undo)** — użytkownicy Anki tego oczekują, brak czytany jest jako błąd.
- **Podgląd następnego interwału na przyciskach ocen** — kluczowa pętla zaufania do SRS.
- **Kopia zapasowa i przywracanie** — jedyna nieodwracalna porażka aplikacji SRS to utrata historii powtórek; `Reset progress` bez backupu to funkcja kasująca dane.
- **Wyszukiwanie (FTS5) i akcje masowe w przeglądarce kart** — bez nich przeglądarka jest tylko podglądem; lista 50 tys. kart wymaga też paginacji.
- **TTS (wymowa)** — niemal darmowe przez API platformy, duża wartość w aplikacji językowej.
- **Suspend / bury / flagi / leeche**, subdecki, wykrywanie duplikatów w edytorze.
- **Precyzyjna definicja „retention"** w statystykach (true retention na kartach w stanie Review, w podziale young/mature).
- **Reguła: zmiana typu decka ukrywa, nigdy nie kasuje** danych.

### Uzupełnienia w treściach wbudowanych

- Rozszerzony schemat: `schemaVersion`, `deckGuid`, `guid` kart, `contentVersion`, `sourceLanguage`, `license`, pregenerowane `associations`, zakresy pozycji w `wordLinks`.
- **Ścieżka aktualizacji treści** — dodanie 200 kart w v1.1 bez kasowania postępu użytkownika (dopasowanie po `guid`). W v2 jedyną drogą było usunięcie i ponowny import, czyli utrata postępów.
- **Linter treści w CI** — nierozwiązane `wordCardRef`, duplikaty, za długie asocjacje itd. blokują build.
- **Kontrakt importu:** idempotentny, transakcyjny, decki słów przed deckami zdań (w v2 ta kolejność nie była zapisana, a błąd byłby cichy), z benchmarkiem.
- **Licencjonowanie treści** — jedyny punkt planu z ryzykiem prawnym, w v2 nieobecny. Tatoeba to CC-BY 2.0 FR (wymaga atrybucji, audio bywa non-commercial); kod Anki jest na AGPL/GPL — format wolno zaimplementować ze specyfikacji, kodu nie wolno skopiować; nazwy „Anki" nie używać w tytule w store.

### Uzupełnienia w silniku FSRS

- Przypięcie wersji i **21 parametrów FSRS-6 (`w0`–`w20`)**, w tym `w20` (personalizowany decay) i `w17`–`w19` (pamięć krótkotrwała / powtórki tego samego dnia).
- **Testy zgodności z implementacją referencyjną** — różnica między „napisaliśmy coś w kształcie FSRS" a „zaimplementowaliśmy FSRS".
- Kroki uczenia/przeuczania, fuzz interwałów (deterministyczny per karta), clamping, `nextIntervalPreview(rating)` dla UI.
- **Rozdzielenie schedulera od algorytmu** — „jaki jest nowy stan karty" i „którą kartę pokazać" to dwa różne problemy.

### Proces

- **Definition of Done wspólne dla wszystkich faz** — v2 opisywał czynności, nie rezultaty.
- **Rejestr ryzyk** (12 pozycji z mitygacją i fazą-właścicielem).
- **Estymacja 22 tygodni** zamiast 14, z wyznaczoną ścieżką krytyczną, pracami równoległymi i listą tego, co ciąć najpierw, gdyby trzeba było zmieścić się w 16.
- **Osiem otwartych decyzji `[DECIDE]`** z wartościami domyślnymi, m.in.: czy Desktop/Web są w v1 (stack deklaruje 4 platformy, fazy budują 2), model dostępu do LLM (BYOK vs proxy — z modelem kosztowym), źródło treści, zakres `.apkg` w v1.
- **Metryki sukcesu v1**, żeby wiadomo było, co instrumentować w fazie 12.