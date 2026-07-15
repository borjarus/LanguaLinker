# Implementation Plan for a Flashcards App (Anki-like) in Kotlin Multiplatform

## Project Goal

A mobile app (Android + iOS) for learning through spaced repetition using the **FSRS** algorithm and a built-in mnemonic association system based on methods (Substitution Word Technique + Chain Association Method). The app will come with default sentence/phrase decks for each language (e.g. Deck: German).

---

## Architecture and Technology Stack

- **KMP (Kotlin Multiplatform)** — shared logic: domain, data, FSRS engine, association engine.
- **Compose Multiplatform** — UI (Android + iOS + Desktop + Web).
- **SQLDelight** — local database (shared).
- **Koin** — DI (shared).
- **Kotlinx.serialization** — data serialization.
- **Kotlinx.datetime** — date/time handling (FSRS requires precise timestamps).
- **DataStore** — user settings.
- **Ktor** — requests to the association-generation model (LLM API).
- **Bundled content** — built-in language decks in the app assets.

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

```kotlin
data class Card(
    val id: Long,
    val deckId: Long,
    val front: String,
    val back: String,
    val sortField: String,
    val tags: List<String>,
    val cardType: CardType,      // Sentence | Word
    val position: Int,
    val fsrsState: CardFsrsState
)

data class CardFsrsState(
    val due: Long,               // Anki-compatible due value
    val stability: Float,
    val difficulty: Float,
    val retrievability: Float,
    val easeFactor: Float,
    val averageInterval: Int,
    val lastReviewDate: LocalDate,
    val nextReviewDate: LocalDate,
    val scheduledDays: Int,
    val elapsedDays: Int,
    val reps: Int,
    val lapses: Int,
    val state: CardState
)

enum class Rating { Again, Hard, Good, Easy }
enum class CardState { New, Learning, Review, Relearning }
enum class CardType { Sentence, Word }
```

**Import/export requirement:** the `due` field must always be read from and written to `.apkg` / CSV / JSON payloads. This is what lets the user export a collection and later re-import it without losing the review progress already accumulated on each card.

## Association Data Model

```kotlin
data class Association(
    val id: Long,
    val cardId: Long,
    val content: String,
    val type: AssociationType,   // Generated | UserSaved
    val isFavorite: Boolean,
    val createdAt: Instant
)

enum class AssociationType { Generated, UserSaved }
```

## Grammar Tip Data Model

A card can have zero, one, or multiple grammar tips attached. Grammar tips are primarily meant for sentence-type cards, since a single word rarely needs grammatical context on its own.

```kotlin
data class GrammarTip(
    val id: Long,
    val cardId: Long,
    val content: String,
    val order: Int,
    val source: GrammarTipSource,  // Bundled | Generated | UserAdded
    val createdAt: Instant
)

enum class GrammarTipSource { Bundled, Generated, UserAdded }
```

## Word-Sentence Link Model

Every word inside a sentence card can be linked to its corresponding entry in a **word deck**, so the user can jump from a sentence to a specific word to see its own associations and grammar tips.

```kotlin
data class SentenceWordLink(
    val id: Long,
    val sentenceCardId: Long,
    val wordCardId: Long,
    val positionInSentence: Int,
    val surfaceForm: String
)
```

- `surfaceForm` lets the UI highlight the exact word as written in the sentence.
- A sentence card can have multiple `SentenceWordLink` rows.
- In newly created sentence cards, each individual word should be linkable to a word-deck card.
- Tapping a linked word in Study Session or Card Detail opens a quick preview: translation, associations, and grammar tips for that specific word.

## Deck Type Model

Decks now carry a **type**, configurable in Deck Settings. At the start, three types are supported:

```kotlin
enum class DeckType {
    Linguistic,
    TextWithAssociations,
    Simple
}

data class DeckSettings(
    val deckId: Long,
    val type: DeckType,
    val requestRetention: Float,
    val maximumInterval: Int,
    val newCardsPerDay: Int
)
```

- **Linguistic** — standard language deck: front/back, FSRS, associations, grammar tips, and word links.
- **TextWithAssociations** — free text (paragraph, quote, definition, note) with generated mnemonic associations for the whole text.
- **Simple** — plain flashcard: front/back only, no associations, no grammar tips, no word links.

Deck type is selected in deck settings and determines which UI sections and fields are available.

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

---

## Implementation Phases

### PHASE 1 — Project Setup (Week 1)

- [x] Initialize the KMP project (Android + iOS targets).
- [x] Configure Gradle: KMP, Compose Multiplatform, dependency versions.
- [x] Add dependencies: SQLDelight, Koin, kotlinx.datetime, kotlinx.serialization, DataStore, Ktor.
- [x] Module structure:
  - `shared/` — domain, data, FSRS engine, association engine
  - `androidApp/`
  - `iosApp/`
- [x] CI/CD: GitHub Actions — build checks for Android + iOS.

### PHASE 2 — FSRS-6 Engine (Week 2)

- [x] Implement the FSRS memory model in `shared/domain/fsrs/`
- [x] Unit tests for the FSRS engine
- [x] Support states: `New → Learning → Review → Relearning`
- [x] Calculate: `stability`, `difficulty`, `retrievability`, `nextInterval`

### PHASE 3 — Data Layer (Week 3)

- [x] SQLDelight schema:
  - table `decks` (+ `type` column)
  - table `deck_settings`
  - table `cards` (+ full FSRS / Anki-equivalent fields: `due`, `stability`, `difficulty`, `retrievability`, `easeFactor`, `averageInterval`, `reps`, `lapses`, `sortField`, `position`, `cardType`)
  - table `review_logs`
  - table `card_templates`
  - table `associations`
  - table `grammar_tips`
  - table `sentence_word_links`
- [x] Repositories:
  - `DeckRepository`
  - `CardRepository`
  - `ReviewLogRepository`
  - `AssociationRepository`
  - `GrammarTipRepository`
  - `SentenceWordLinkRepository`
- [x] Database migrations
- [x] DataStore — settings: `requestRetention`, `maximumInterval`, theme, LLM API key
- [x] Repository integration tests

### PHASE 4 — Bundled Content + Import (Week 4)

- [ ] Prepare JSON files for language decks:
  - German: sentence deck + matching word deck
  - English: sentence deck + matching word deck
- [ ] Default bundled content = sentences; single words in separate word decks
- [ ] `BundledDeckImporter`
  - parse `grammarTips`
  - parse `wordLinks`
  - resolve `wordCardRef`
  - set `deck.type`
- [ ] DataStore flag: `bundledDecksImported: Boolean`
- [ ] User CSV import (`front; back; deck; tags; due`)
- [ ] Import `.apkg` (Anki package — ZIP + SQLite)
  - map `due`, `ease`, `reps`, `lapses`, `ivl`
  - preserve reviewed-card progress
- [ ] `ExportDeckUseCase` — export to CSV / `.apkg`, always including `due`
- [ ] Round-trip import/export test for reviewed cards:
  - export deck
  - re-import deck
  - verify `due`, `stability`, `difficulty`, `reps`, `lapses` are unchanged

### PHASE 5 — Association Engine (Week 5)

- [ ] `AssociationRepository`
- [ ] `GenerateAssociationUseCase` — branch behavior by `DeckType`
- [ ] `SaveFavoriteAssociationUseCase`
- [ ] `AssociationViewModel`
- [ ] Error handling: no network, API error
- [ ] Configure API key in settings
- [ ] Unit tests

### PHASE 6 — Domain / Use Cases (Week 6)

- [ ] `GetDueCardsUseCase`
- [ ] `ScheduleReviewUseCase`
- [ ] `CreateDeckUseCase` / `UpdateDeckUseCase` / `DeleteDeckUseCase`
- [ ] `CreateCardUseCase` / `UpdateCardUseCase` / `DeleteCardUseCase`
- [ ] `AddGrammarTipUseCase` / `RemoveGrammarTipUseCase` / `ReorderGrammarTipsUseCase`
- [ ] `LinkWordToSentenceUseCase` / `UnlinkWordFromSentenceUseCase` / `GetLinkedWordDetailsUseCase`
- [ ] `GetStudyStatsUseCase`

### PHASE 7 — UI Screens (Weeks 7–11)

#### Screen 1: Dashboard

**Path:** `/home`

**Content:**
- Deck list with summary: name, language icon, deck type badge, due today, new cards, overdue cards
- `+ New deck`
- Daily progress bar
- Day streak
- Quick `Study everything`

#### Screen 2: Deck Overview

**Path:** `/deck/{id}`

**Content:**
- Deck name
- Deck statistics
- `Start review`
- `Browse cards`
- `Add card`
- Deck settings section, including `DeckType`

#### Screen 3: Study Session

**Path:** `/study/{deckId}`

For sentence cards in linguistic decks:

```text
💡 Show association | 📘 Grammar tips (2)
🔗 Ich  🔗 gehe  🔗 heute  🔗 Abend  🔗 ins  🔗 Kino
```

- Grammar tips section is hidden by default
- Linked words open quick previews with:
  - translation
  - associations
  - grammar tips
- Visible sections depend on `DeckType`:
  - `Simple` → front/back only
  - `TextWithAssociations` → association section for full text
  - `Linguistic` → associations + grammar tips + linked words

#### Screen 4: Session Summary

- reviewed cards
- rating distribution
- retention
- session duration

#### Screen 5: Card Detail / Association Manager

- card front/back
- favorite association
- generated association history
- grammar tips list
- linked words section
- add / edit / remove / reorder

#### Screen 6: Card Editor

- `Front`
- `Back`
- `Association`
- `Grammar tips`
- `Linked words`
- Deck selector
- Tags
- Live preview
- `Save` / `Save and add another`

Field visibility depends on `DeckType`.

#### Screen 7: Card Browser

- card list with preview
- FSRS state
- due / position preview
- icons:
  - ⭐ association
  - 📘 grammar tips
  - 🔗 linked words

#### Screen 8: Statistics

- review forecast
- activity heatmap
- retention
- stability distribution
- streak, review count, study time

#### Screen 9: Settings

- FSRS settings
- association settings
- theme
- notifications
- import / export
- reset progress
- about

**Deck-level settings:**
- deck type picker
- per-type sub-settings

### PHASE 8 — Notifications (Week 12)

- [ ] Push notification scheduling
- [ ] Daily reminder with the number of cards to study
- [ ] No cards → do not send

### PHASE 9 — Polish and Testing (Weeks 13–14)

- [ ] Card flip animation
- [ ] Association reveal animation
- [ ] Grammar tips reveal animation
- [ ] Screen transition animations
- [ ] Unit tests:
  - FSRS engine
  - Association engine
  - GrammarTipRepository
  - SentenceWordLinkRepository
- [ ] UI tests:
  - add card → session → rate → save association
  - add sentence card with grammar tips and word links
  - verify linked word preview
- [ ] Accessibility
- [ ] Performance profiling
- [ ] API error handling

---

## Directory Structure

```text
project/
├── shared/
│   ├── src/commonMain/kotlin/
│   │   ├── domain/
│   │   │   ├── fsrs/
│   │   │   ├── association/
│   │   │   ├── grammar/
│   │   │   ├── wordlink/
│   │   │   ├── model/
│   │   │   │   ├── Deck.kt
│   │   │   │   ├── DeckSettings.kt
│   │   │   │   ├── DeckType.kt
│   │   │   │   ├── Card.kt
│   │   │   │   ├── Association.kt
│   │   │   │   ├── GrammarTip.kt
│   │   │   │   ├── SentenceWordLink.kt
│   │   │   │   └── ReviewLog.kt
│   │   │   └── usecase/
│   │   ├── data/
│   │   │   ├── database/
│   │   │   ├── repository/
│   │   │   ├── api/
│   │   │   ├── bundled/
│   │   │   └── settings/
│   │   └── di/
│   └── src/commonTest/kotlin/
├── androidApp/
│   └── src/main/
│       ├── assets/decks/
│       └── kotlin/ui/
└── iosApp/
    └── iosApp/
        └── Resources/decks/
```

---

## Implementation Priorities (MoSCoW)

| Feature | Priority |
|---|---|
| FSRS engine (Again/Hard/Good/Easy) | Must Have |
| Bundled language decks (DE, EN) — sentences + word decks | Must Have |
| Study session with FSRS intervals | Must Have |
| Association generation (LLM API) | Must Have |
| Save favorite association | Must Have |
| Association section hidden by default | Must Have |
| Grammar tips per card (0..n) | Must Have |
| Deck type selection: Linguistic / TextWithAssociations / Simple | Must Have |
| Full Anki-equivalent card fields | Must Have |
| `due` field preserved on import/export | Must Have |
| Word links for sentence cards | Should Have |
| Card browser + Association Manager | Should Have |
| Statistics | Should Have |
| CSV / `.apkg` import | Should Have |
| Notifications | Should Have |
| Manual association entry | Should Have |
| Generated association history | Could Have |
| Custom local model | Could Have (v2) |
| Cards with image/audio | Could Have |
| Synchronization | Won't Have (v1) |

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
```

## Wprowadzone zmiany

- Dodano `GrammarTip` jako relację 0..n na kartę.
- Dodano `SentenceWordLink`, żeby każde słowo w zdaniu mogło wskazywać na kartę w decku słów.
- Doprecyzowano, że w nowo tworzonym zdaniu każdy osobny wyraz powinien być relacją do decku ze słowami.
- Dodano `DeckType` i `DeckSettings` z trzema typami: `Linguistic`, `TextWithAssociations`, `Simple`.
- Zmieniono bundled content tak, aby domyślnie bazował na zdaniach/frazach, a słowa były w osobnych deckach.
- Dodano pełen zestaw pól zgodnych z Anki dla każdej karty.
- Dopisano, że `Due` musi być zachowywane przy imporcie i eksporcie, aby po imporcie nie tracić progresu przeanalizowanych fiszek.

