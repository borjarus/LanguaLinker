# Implementation Plan for a Flashcards App (Anki-like) in Kotlin Multiplatform

## Project Goal

A mobile app (Android + iOS) for learning through spaced repetition using the **FSRS** algorithm and a built-in mnemonic association system based on methods from the book *"3 surprising language-learning techniques"* (Substitution Word Technique + Chain Association Method). The app will come with default sentence/phrase decks for each language (e.g. Deck: German). [file:1]

---

## Architecture and Technology Stack

- **KMP (Kotlin Multiplatform)** — shared logic: domain, data, FSRS engine, association engine. [file:1]
- **Compose Multiplatform** — UI (Android + iOS). [file:1]
- **SQLDelight** — local database (shared). [file:1]
- **Koin** — DI (shared). [file:1]
- **Kotlinx.serialization** — data serialization. [file:1]
- **Kotlinx.datetime** — date/time handling (FSRS requires precise timestamps). [file:1]
- **DataStore** — user settings. [file:1]
- **Ktor** — requests to the association-generation model (LLM API). [file:1]
- **Bundled content** — built-in language decks in the app assets. [file:1]

---

## FSRS Data Model

Each card stores the fields required by the FSRS algorithm. [file:1]

```kotlin
data class CardFsrsState(
    val stability: Float,
    val difficulty: Float,
    val retrievability: Float,
    val lastReviewDate: LocalDate,
    val nextReviewDate: LocalDate,
    val scheduledDays: Int,
    val elapsedDays: Int,
    val reps: Int,
    val lapses: Int,
    val state: CardState   // New | Learning | Review | Relearning
)

enum class Rating { Again, Hard, Good, Easy }
enum class CardState { New, Learning, Review, Relearning }
```

## Association Data Model

```kotlin
data class Association(
    val id: Long,
    val cardId: Long,
    val content: String,         // association text
    val type: AssociationType,   // Generated | UserSaved
    val isFavorite: Boolean,
    val createdAt: Instant
)

enum class AssociationType { Generated, UserSaved }
```

---

## Built-in Language Decks (Bundled Content)

- JSON/CSV files in `assets/decks/` bundled with the app. [file:1]
- On first launch, they are imported into SQLDelight. [file:1]
- Example structure of a `de_deck.json` deck: [file:1]

```json
{
  "language": "de",
  "name": "German",
  "cards": [
    { "front": "das Haus", "back": "house", "tags": ["noun", "A1"] },
    { "front": "Guten Morgen", "back": "Good morning", "tags": ["phrase", "A1"] }
  ]
}
```

- MVP: German, English. [file:1]
- Additional languages as app updates. [file:1]

---

## Association Generation System

### Approach: External LLM API (MVP)

- Ktor sends a prompt to an LLM (OpenAI / Gemini / custom endpoint). [file:1]
- The prompt includes: [file:1]
  - the current word/sentence from the flashcard,
  - source and target language,
  - an instruction to use the **Substitution Word Technique** — replace the foreign word with a phonetically similar Polish image-word and create a vivid, absurd, emotional scene connecting the two. [file:1]
- Example for `der Schlüssel` (key): [file:1]
  > *"Schlüssel sounds like 'szlusel' → imagine a CIGARETTE being pushed into a lock instead of a key, and a heart-shaped cloud of smoke flying out of the keyhole."* [file:1]

### Custom Model Option (v2 / optional)

- Fine-tuning a small model (e.g. Mistral 7B / Phi-3) on pairs: word → mnemonic association using the substitution technique. [file:1]
- Run locally via ONNX Runtime / llama.cpp (Android/iOS). [file:1]
- Requires preparing a training dataset (~5000 examples). [file:1]
- Decision: **start with the API, use a custom model as an option in v2 once user data is available**. [file:1]

### Prompt Engineering (key)

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

- [x] Initialize the KMP project (Android + iOS targets). [file:1]
- [x] Configure Gradle: KMP, Compose Multiplatform, dependency versions. [file:1]
- [ ] Add dependencies: SQLDelight, Koin, kotlinx.datetime, kotlinx.serialization, DataStore, Ktor. [file:1]
- [ ] Module structure: [file:1]
  - `shared/` — domain, data, FSRS engine, association engine
  - `androidApp/`
  - `iosApp/`
- [ ] CI/CD: GitHub Actions — build checks for Android + iOS. [file:1]

### PHASE 2 — FSRS Engine (Week 2)

- [ ] Implement the FSRS memory model in `shared/domain/fsrs/`: [file:1]
  - `FsrsAlgorithm.kt` — logic for calculating the next interval
  - `FsrsParameters.kt` — 17 weights + configuration
  - `FsrsScheduler.kt` — review scheduling
- [ ] Unit tests for the FSRS engine — verify compatibility with the reference implementation. [file:1]
- [ ] Support states: `New → Learning → Review → Relearning`. [file:1]
- [ ] Calculate: `stability`, `difficulty`, `retrievability`, `nextInterval`. [file:1]

### PHASE 3 — Data Layer (Week 3)

- [ ] SQLDelight schema: [file:1]
  - table `decks`
  - table `cards` (+ FSRS state fields)
  - table `review_logs`
  - table `card_templates`
  - table `associations` (content, type: Generated/UserSaved, isFavorite, cardId)
- [ ] Repositories: `DeckRepository`, `CardRepository`, `ReviewLogRepository`, `AssociationRepository`. [file:1]
- [ ] Database migrations. [file:1]
- [ ] DataStore — settings: `requestRetention`, `maximumInterval`, theme, LLM API key. [file:1]
- [ ] Repository integration tests. [file:1]

### PHASE 4 — Bundled Content + Import (Week 4)

- [ ] Prepare JSON files for language decks (`assets/decks/`): [file:1]
  - German: ~500 basic words and phrases (A1–B1)
  - English: ~500 basic words and phrases (A1–B1)
- [ ] `BundledDeckImporter` — read from assets, parse, and save to the DB on first launch. [file:1]
- [ ] DataStore flag: `bundledDecksImported: Boolean`. [file:1]
- [ ] User CSV import (front; back; deck; tags). [file:1]
- [ ] Import `.apkg` (Anki package — ZIP + SQLite). [file:1]
- [ ] `ExportDeckUseCase` — export to CSV. [file:1]

### PHASE 5 — Association Engine (Week 5)

- [ ] `AssociationRepository` — CRUD + `getFavoriteAssociation(cardId)` + `getGeneratedAssociations(cardId)`. [file:1]
- [ ] `GenerateAssociationUseCase` — call the LLM API through Ktor and parse the response. [file:1]
- [ ] `SaveFavoriteAssociationUseCase` — save the favorite association. [file:1]
- [ ] `AssociationViewModel` — state management: Loading / Success / Error. [file:1]
- [ ] Error handling: no network, API error → show a message to the user. [file:1]
- [ ] Configure the API key in settings (DataStore, do not hardcode). [file:1]
- [ ] Unit tests: API mocking, favorite-saving logic. [file:1]

### PHASE 6 — Domain / Use Cases (Week 6)

- [ ] `GetDueCardsUseCase` — cards due for review today. [file:1]
- [ ] `ScheduleReviewUseCase` — call FSRS + save to DB. [file:1]
- [ ] `CreateDeckUseCase` / `UpdateDeckUseCase` / `DeleteDeckUseCase`. [file:1]
- [ ] `CreateCardUseCase` / `UpdateCardUseCase` / `DeleteCardUseCase`. [file:1]
- [ ] `GetStudyStatsUseCase` — statistics (retention rate, cards for today, streak span). [file:1]

### PHASE 7 — UI Screens (Weeks 7–11)

#### Screen 1: Dashboard (Home Screen)

**Path:** `/home` [file:1]

**Content:** [file:1]
- Deck list with summary: name, language icon, number of cards due today (blue badge), new cards, overdue cards.
- `+ New deck` button.
- Daily progress bar (how many cards completed / how many remain).
- "Day streak" widget.
- Quick `Study everything` button.

**Actions:** [file:1]
- tap a deck → `Deck Overview`
- long tap → options (rename, delete, export)
- tap `+` → `Create Deck`

#### Screen 2: Deck Overview

**Path:** `/deck/{id}` [file:1]

**Content:** [file:1]
- Deck name (editable inline).
- Deck statistics: New / Due / Overdue.
- `Start review` button (primary CTA).
- `Browse cards` button.
- `Add card` button.
- Deck settings section (`requestRetention`, learning steps).

#### Screen 3: Study Session (Study Screen) ⭐

**Path:** `/study/{deckId}` [file:1]

This is the most important screen in the app. [file:1]

**Content (question phase — card front):** [file:1]
- Session progress bar (e.g. 12/47).
- Flashcard front field (word / sentence in the foreign language).
- `Show answer` button.

**Content (answer phase — card back):** [file:1]
- Front field (still visible).
- Translation (card back).
- **Association section** (collapsed/hidden by default): [file:1]
  - If a saved favorite association exists → display it as a hidden card (tap/click reveals it).
  - If no favorite exists → display a `💡 Show association` button (generates via API) + Loading state.
  - After generation: show the association text + `⭐ Save as favorite` and `🔄 Generate new` buttons.
  - If a favorite already exists → `🔄 Generate new` temporarily replaces it (does not overwrite without confirmation).
- **FSRS rating buttons:** `Again` | `Hard` | `Good` | `Easy`. [file:1]
  - Under each button, display the **next interval** (e.g. "10 min", "3 days", "8 days", "21 days"). [file:1]
- Card flip animation (front → back). [file:1]

**Association logic in Study Session:** [file:1]

```kotlin
onShowAnswer():
  val fav = associationRepo.getFavoriteAssociation(cardId)
  if (fav != null) → state = ShowFavorite(fav, hidden=true)
  else             → state = Empty (button "Show association")

onRevealAssociation():
  state = ShowFavorite(fav, hidden=false)

onGenerateAssociation():
  state = Loading
  val generated = generateAssociationUseCase(card)
  state = ShowGenerated(generated)

onSaveFavorite(association):
  associationRepo.saveFavorite(cardId, association)
  state = ShowFavorite(association, hidden=false)
```

**Rating buttons (Again/Hard/Good/Easy):** [file:1]
- call `ScheduleReviewUseCase` with `Rating` → save the new FSRS state.
- After the queue is exhausted → `Session Summary`. [file:1]

#### Screen 4: Session Summary

**Path:** `/study/{deckId}/summary` [file:1]

**Content:** [file:1]
- Number of reviewed cards.
- Rating distribution: Again / Hard / Good / Easy.
- Session retention (%).
- Session duration.
- `Done` button → Home.
- `Continue` button (if there are still cards left).

#### Screen 5: Card Detail / Association Manager

**Path:** `/card/{id}/associations` [file:1]

**Access:** tap the association icon in Card Browser. [file:1]

**Content:** [file:1]
- Card front and back (preview).
- Currently saved favorite association (manually editable).
- List of previously generated associations (history).
- `🔄 Generate new association` button.
- `⭐ Set as favorite` button for each generated one.
- `✏️ Enter your own association manually` button.

#### Screen 6: Card Editor

**Path:** `/card/create` or `/card/{id}/edit` [file:1]

**Content:** [file:1]
- `Front` field — text.
- `Back` field — text.
- `Association` field (optional, manual input or generate with a button).
- Deck selector (dropdown).
- Tags (chip input).
- Live flashcard preview.
- `Save` / `Save and add another` button.

#### Screen 7: Card Browser

**Path:** `/deck/{id}/browse` [file:1]

**Content:** [file:1]
- Card list with preview (front, FSRS state, ⭐ icon if it has a saved association).
- Search bar.
- Filters: New / Learning / Review / Suspended.
- Multi-select + bulk actions (delete, suspend, change deck, reset state).

**Actions:** [file:1]
- tap a card → `Card Editor`
- tap the ⭐/💡 icon → `Card Detail / Association Manager`

#### Screen 8: Statistics

**Path:** `/stats` [file:1]

**Content:** [file:1]
- Review forecast — bar chart: how many cards over the next 30 days.
- Activity heatmap (365 days).
- Retention (%).
- Card stability distribution.
- Streak, total review count, study time.

#### Screen 9: Settings

**Path:** `/settings` [file:1]

**Sections:** [file:1]
- **FSRS:** `requestRetention` (slider 70–99%), `maximumInterval`, new cards/day limit.
- **Associations:** field for API key (OpenAI/Gemini), connection test, model (`gpt-4o-mini` / `gemini-flash`).
- **Theme:** Light / Dark / System.
- **Notifications:** daily reminder with time.
- **Import / Export:** import `.apkg` / CSV, export collection.
- **Reset progress** — dangerous action with confirmation.
- **About app:** version, links.

### PHASE 8 — Notifications (Week 12)

- [ ] Push notification scheduling (AlarmManager Android / UNUserNotification iOS). [file:1]
- [ ] Daily reminder with the number of cards to study. [file:1]
- [ ] No cards → do not send. [file:1]

### PHASE 9 — Polish and Testing (Weeks 13–14)

- [ ] Card flip animation (`AnimatedContent` + `graphicsLayer rotationY`). [file:1]
- [ ] Association reveal animation (blur fade or slide-down). [file:1]
- [ ] Screen transition animations (Compose Navigation). [file:1]
- [ ] Unit tests: FSRS engine, AssociationEngine (mock API). [file:1]
- [ ] UI tests: key flow (add card → session → rate → check association → save favorite). [file:1]
- [ ] Accessibility: `contentDescription`, contrast, font sizes. [file:1]
- [ ] Performance profiling (10,000+ cards). [file:1]
- [ ] Association API error handling: missing key, timeout, token limit. [file:1]

---

## Directory Structure

```text
project/
├── shared/
│   ├── src/commonMain/kotlin/
│   │   ├── domain/
│   │   │   ├── fsrs/
│   │   │   │   ├── FsrsAlgorithm.kt
│   │   │   │   ├── FsrsParameters.kt
│   │   │   │   ├── FsrsScheduler.kt
│   │   │   │   └── models/
│   │   │   ├── association/
│   │   │   │   ├── AssociationPromptBuilder.kt
│   │   │   │   ├── GenerateAssociationUseCase.kt
│   │   │   │   └── SaveFavoriteAssociationUseCase.kt
│   │   │   ├── model/
│   │   │   │   ├── Deck.kt
│   │   │   │   ├── Card.kt
│   │   │   │   ├── Association.kt
│   │   │   │   └── ReviewLog.kt
│   │   │   └── usecase/
│   │   ├── data/
│   │   │   ├── database/        (SQLDelight .sq files)
│   │   │   ├── repository/
│   │   │   ├── api/             (Ktor - LLM client)
│   │   │   ├── bundled/         (BundledDeckImporter)
│   │   │   └── settings/        (DataStore)
│   │   └── di/
│   │       └── AppModule.kt
│   └── src/commonTest/kotlin/
│       ├── domain/fsrs/
│       └── domain/association/
├── androidApp/
│   └── src/main/
│       ├── assets/decks/        (de_deck.json, en_deck.json, ...)
│       └── kotlin/ui/
└── iosApp/
    └── iosApp/
        └── Resources/decks/     (the same JSON files)
```

---

## Implementation Priorities (MoSCoW)

| Feature | Priority |
|---|---|
| FSRS engine (Again/Hard/Good/Easy) | Must Have |
| Bundled language decks (DE, EN) | Must Have |
| Study session with FSRS intervals | Must Have |
| Association generation (LLM API) | Must Have |
| Save favorite association | Must Have |
| Association section in Study Session (hidden by default) | Must Have |
| Card browser + Association Manager | Should Have |
| Statistics (forecast, retention) | Should Have |
| CSV / .apkg import | Should Have |
| Notifications | Should Have |
| Manual association entry | Should Have |
| Generated association history | Could Have |
| Custom local model (ONNX/llama.cpp) | Could Have (v2) |
| Cards with image/audio | Could Have |
| Synchronization (AnkiWeb / custom backend) | Won't Have (v1) |
```

---

## Key Technical Decisions

- **LLM via API instead of a local model (v1)** — faster delivery, no model overhead on the device; a custom model may be considered in v2 once data about association effectiveness is collected. [file:1]
- **Association hidden by default** — the user first tries to remember independently; the association is support, not a crutch. [file:1]
- **Favorite association as a single record per card** — simpler design; generated history stored in a separate table for review. [file:1]
- **AssociationPromptBuilder as a separate class** — easy prompt replacement and testability without network access. [file:1]
- **SQLDelight instead of Room** — native KMP support, type-safe queries. [file:1]
- **Bundled content in assets** — no network required on first launch, the user gets value immediately. [file:1]
- **Review logs from the start** — necessary for future FSRS weight optimization and analysis of association effectiveness. [file:1]
