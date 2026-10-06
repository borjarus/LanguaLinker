# Plan szczegółowy — rozpiska faz krok po kroku

**Wersja:** 1 (uzupełnienie do `plan.md` v3)
**Data:** 2026-09-03
**Status:** Fazy 1–3 ukończone; niniejszy dokument rozpisuje fazy 3.5–15

Ten dokument nie zastępuje `plan.md` — jest jego operacyjnym uszczegółowieniem.
Każda faza jest rozbita na ponumerowane kroki w kolejności wykonywania, z podziałem na
przygotowanie, implementację, testy i kryteria ukończenia (DoD). Decyzje projektowe,
uzasadnienia i decyzje otwarte (`[DECIDE]`) pozostają w `plan.md`.

**Obowiązuje wszędzie wspólne Definition of Done z `plan.md`** (demonstracja na
prawdziwym urządzeniu, zielone CI na obu targetach, migracja + test migracji przy każdej
zmianie schematu, stringi zewnętrzne, stany loading/empty/error/offline, TalkBack +
VoiceOver, aktualizacja dokumentacji).

---

## Fazy ukończone (podsumowanie)

| Faza | Zakres | Stan |
|---|---|---|
| 1 — Project Setup | KMP (Android + iOS), Gradle, zależności, struktura modułów, CI | ✅ |
| 2 — FSRS-6 Engine | Model pamięci FSRS, stany kart, unit testy | ✅ (uzupełnienia — konformancja, scheduler — przeniesione do fazy 3.5/6) |
| 3 — Data Layer | Schemat SQLDelight, repozytoria `SqlDelight*Repository`, migracje (schemat v2), ustawienia per-platforma, 135 testów | ✅ |

Zaległości z fazy 1 przeniesione do fazy 3.5: ktlint/detekt w CI, konfiguracja podpisywania
buildów, crash reporting + Kermit, decyzja D-01 (usunięcie nieużywanych targetów — `webApp`
obecnie nie buduje się).

---

## FAZA 3.5 — Retrofit schematu i modelu (tydzień 4)

**Cel:** wprowadzić wszystkie korekty modelu danych (C1, C2, C6) teraz, dopóki nie ma danych
produkcyjnych. Każda pozycja to dziś jedna migracja, po starcie — wielotygodniowa operacja
na danych użytkowników.

### Krok 1 — przygotowanie

1. Zamrozić bieżący schemat jako fixture: wygenerować bazę w aktualnej wersji (v2) i zapisać
   ją jako plik testowy do testów migracji (analogicznie do istniejącego fixture v1).
2. Spisać docelowy schemat (v3) w jednym pliku roboczym przed dotknięciem `.sq` —
   wszystkie zmiany poniżej wchodzą jako **jedna** migracja `2 → 3`, nie dziesięć drobnych.

### Krok 2 — sync-readiness (C1)

3. Do tabel `decks`, `notes`, `cards`, `associations`, `grammar_tips`, `sentence_word_links`,
   `deck_settings` dodać kolumny: `guid TEXT NOT NULL UNIQUE`, `updated_at INTEGER NOT NULL`,
   `deleted_at INTEGER NULL`, `usn INTEGER NOT NULL DEFAULT -1`.
4. Wprowadzić interfejs `Syncable` w modelach domenowych i zaimplementować go we wszystkich
   encjach użytkownika.
5. Przełączyć operacje DELETE w repozytoriach na soft-delete (`deleted_at = now`); wszystkie
   zapytania SELECT filtrują `deleted_at IS NULL`.
6. Generowanie `guid`: UUID v4 dla nowych rekordów; dla treści wbudowanych `guid` przychodzi
   z pliku decka (faza 5).

### Krok 3 — model Note → Cards

7. Utworzyć tabele `notes` i `note_types` (+ szablony kart jako część `note_types`).
8. Dodać `cards.note_id NOT NULL` i `cards.template_ordinal`.
9. Migracja danych: dla każdej istniejącej karty utworzyć notatkę z polami `[front, back]`,
   typ notatki „Basic", `template_ordinal = 0`.
10. Modele domenowe: `Note`, `NoteType`, `CardTemplate` (wg definicji w `plan.md`);
    aktualizacja `Card` o `noteId` i `templateOrdinal`.

### Krok 4 — czas i `due` (C2)

11. Zastąpić polimorficzne `due: Long` kolumną `due_at INTEGER` (epoch millis, `Instant`).
12. Dodać do ustawień `collection_created_at` (ustawiane na lokalny cutoff dnia przy pierwszym
    uruchomieniu) oraz `rollover_hour` (domyślnie 4) i `zone_id`.
13. `lastReviewDate: LocalDate` → `last_reviewed_at INTEGER NULL` (`Instant`).
14. Usunąć kolumny pochodne: `retrievability`, `average_interval` (retrievability liczone
    na odczycie w `CardFsrsState.retrievability(now, decay)`; opcjonalnie dodać
    `retrievability_at_last_review` jako niemutowalny snapshot do statystyk).

### Krok 5 — stan kolejki, flagi, leech

15. Dodać `cards.queue_state INTEGER NOT NULL` (mapowanie na `QueueState`) i
    `cards.flags INTEGER NOT NULL DEFAULT 0`.
16. Migracja: istniejące karty dostają `queue_state` wyprowadzony z `CardState`
    (New→New, Learning→Learning, Review→Review, Relearning→Learning).
17. Dodać `LeechConfig` do konfiguracji decka (próg 8 lapsów, akcja `TagOnly`).

### Krok 6 — DeckConfig jako współdzielony preset

18. Utworzyć tabelę `deck_config` z polami: `request_retention`, `maximum_interval`,
    `fsrs_parameters` (JSON, 21 wag), `learning_steps`, `relearning_steps`, `enable_fuzz`,
    `new_cards_per_day`, `reviews_per_day`, `new_card_order`, `new_card_gather_priority`,
    `review_order`, `bury_siblings`, `leech_threshold`, `leech_action`.
19. Zredukować `deck_settings` do: `deck_id`, `type`, `config_id`.
20. Migracja: utworzyć preset „Default" i podpiąć do niego wszystkie istniejące decki.
21. Dodać do `decks`: `parent_deck_id NULL`, `source_language`, `target_language`,
    `config_id`, `content_version`, `is_bundled` (część już istnieje po fazie 4-starej —
    zweryfikować i uzupełnić braki).

### Krok 7 — ReviewLog rozszerzony

22. Rozszerzyć `review_logs` o: `state_before`, `stability_before`, `difficulty_before`,
    `duration_ms`, `review_kind`, `fsrs_version`, `parameters_hash`.
23. Zaktualizować model `ReviewLog` i repozytorium.

### Krok 8 — asocjacje i wskazówki przy notatce

24. `associations.card_id` → `note_id`; `grammar_tips.card_id` → `note_id`
    (migracja: przez `cards.note_id` utworzone w kroku 3).
25. Rozszerzyć `associations` o: `source_language`, `target_language`, `prompt_version`,
    `model_id NULL`, `moderation_verdict`, `cache_key`; do enuma `AssociationType` dodać
    `Bundled`.
26. Dodać częściowy indeks unikalny: `CREATE UNIQUE INDEX ... ON associations(note_id)
    WHERE is_favorite = 1` — jeden ulubiony na notatkę wymuszony w schemacie.

### Krok 9 — SentenceWordLink (dokończenie)

27. `resolution` i nullowalny `word_card_id` już są (wciągnięte wcześniej). Dodać brakujące:
    `lemma`, `start_index`, `end_index` (zakres zamiast punktowego `position_in_sentence` —
    inaczej „heute Abend" nie da się połączyć jako jedna jednostka).
28. Zaktualizować resolver w `BundledDeckImporter` tak, by join szedł po `lemma`.

### Krok 10 — media (rezerwacja), indeksy, wydajność

29. Utworzyć tabelę `media` (`note_id`, `file_name`, `mime_type`, `checksum`, `kind`) —
    bez żadnego UI, sama rezerwacja schematu.
30. Dodać indeksy pod realne zapytania sesji: `(deck_id, queue_state, due_at)`,
    `(deck_id, queue_state, position)`, `(note_id)` na cards/associations/grammar_tips,
    `(note_id, is_favorite)`.
31. Wygenerować syntetyczny fixture: 50 000 kart + 500 000 review logów (skrypt/test
    generujący, zapisany deterministycznie z seedem).

### Krok 11 — SecureStorage (C6)

32. Zdefiniować `expect class SecureStorage` w `shared/platform/` z `actual` dla:
    Android (Keystore + EncryptedSharedPreferences), iOS (Keychain), desktop/testy
    (in-memory / plik — wyraźnie oznaczone jako niebezpieczne, tylko dev).
33. Przenieść klucz API LLM z DataStore do `SecureStorage`; migracja jednorazowa przy
    starcie (odczyt ze starego miejsca → zapis do nowego → usunięcie starego).
34. Audyt: klucz nie trafia do logów Kermit ani do crash reportów.

### Krok 12 — zaległości fazy 1

35. ktlint + detekt w CI, z jednorazowym przejściem formatowania po istniejącym kodzie
    (osobny commit, żeby nie zaśmiecić diffów merytorycznych).
36. Kermit + crash reporting (Sentry KMP lub Crashlytics) podpięte w obu appkach.
37. Konfiguracja podpisywania i build types `debug`/`release`/`beta`.
38. Decyzja D-01 i usunięcie/wyłączenie nieużywanych targetów (`webApp` z `wasmJs`).

### Testy fazy 3.5

- Test migracji `v2 → v3` na fixture: zero utraty danych, wszystkie karty mają notatki,
  wszystkie `guid` unikalne.
- Test soft-delete: usunięta karta znika z zapytań, ale rekord istnieje z tombstone.
- Test indeksu jednego ulubionego: druga próba `is_favorite = 1` na tej samej notatce
  rzuca błąd constraint.
- Benchmark: „cards due today" na kolekcji 50 000 kart < 50 ms na urządzeniu ze średniej półki.
- Test C2: karta oceniona 23:59 i 00:01 ląduje we właściwych dniach nauki przy cutoff 04:00,
  w `Europe/Warsaw`, przez zmianę czasu (DST).
- Inspekcja pliku DataStore: brak plaintextowego sekretu na dysku.

### DoD fazy 3.5

- [ ] Migracja + test migracji zielone na obu targetach.
- [ ] Benchmark zapytania due < 50 ms.
- [ ] Żaden sekret nie leży plaintextem na dysku.
- [ ] ktlint/detekt aktywne w CI, build czysty.

---

## FAZA 4 — Design System i szkielet aplikacji (tydzień 5)

**Cel:** wspólny fundament UI zanim powstanie pierwszy ekran — motyw, komponenty,
nawigacja, konwencja stanów, i18n, onboarding.

### Krok 1 — motyw i tokeny

1. Zdefiniować tokeny kolorów dla motywu jasnego **i** ciemnego (semantyczne:
   `surface`, `onSurface`, `primary`, `success`, `warning`, `error`, kolory stanów karty).
2. Skala typograficzna (Material 3 jako baza), skala odstępów (4/8/12/16/24/32),
   kształty, elewacje.
3. `AppTheme` composable w `shared/ui/theme/`, reagujący na motyw systemowy +
   ręczny wybór w ustawieniach.

### Krok 2 — biblioteka komponentów

4. Zaimplementować komponenty bazowe, każdy z parametrami stanu i preview:
   `DeckCard`, `StatTile`, `PrimaryButton`, `RatingButtonRow` (4 przyciski z miejscem na
   podgląd interwału), `SectionHeader`, `EmptyState`, `ErrorState`, `LoadingSkeleton`,
   `Chip`, `BottomSheet`.
5. Zestaw ikon wektorowych zastępujący emoji (⭐📘🔗💡 → ikony + etykiety tekstowe) — C4.
6. Ekran-galeria (`/gallery`, tylko debug) renderujący każdy komponent w obu motywach,
   przy 100 % i 200 % skali czcionki.

### Krok 3 — nawigacja

7. Wybrać bibliotekę nawigacji (Compose MP Navigation / Voyager / Decompose) — decyzja
   zapisana jako ADR.
8. Graf nawigacji dla wszystkich 9 tras z ekranami-placeholderami:
   `/home`, `/deck/{id}`, `/study/{deckId}`, `/summary`, `/card/{id}`, `/editor`,
   `/browser`, `/stats`, `/settings`.
9. Deep linki (`/deck/{id}`, `/study/{deckId}`) i predictive back na Androidzie.

### Krok 4 — konwencja stanu

10. Jeden sealed `UiState<T>` (`Loading | Empty | Content | Error`) używany przez każdy ekran.
11. Konwencja ViewModel + `StateFlow` wspólna dla platform; jeden w pełni opracowany
    przykład (ekran galerii lub placeholder Home) jako wzorzec do kopiowania.

### Krok 5 — i18n (C3)

12. Scaffolding zasobów tekstowych (Compose MP resources / moko-resources) z plikami PL i EN.
13. Zasoby liczby mnogiej („2 karty" / „5 kart") — plurals, nie konkatenacja.
14. Check w CI wykrywający literalne stringi w composable'ach (detekt rule lub własny task).

### Krok 6 — dostępność bazowa (C4)

15. Minimalny touch target 48 dp, kolejność fokusa, contentDescription na wszystkim
    interaktywnym.
16. Sanity check dynamic type: galeria przy 200 % bez przycięć.

### Krok 7 — testy screenshotowe

17. Podpiąć Paparazzi/Roborazzi; snapshoty wszystkich komponentów galerii w obu motywach.
    Od tej pory każda zmiana UI produkuje widoczny diff.

### Krok 8 — onboarding

18. Flow pierwszego uruchomienia: wybór języka ojczystego (L1) → wybór języka uczonego
    (L2) → dzienny cel → (iOS) pytanie o notyfikacje **odroczone** do końca pierwszej sesji →
    opcjonalna zgoda na generowanie AI (C6, z możliwością odmowy — apka działa bez tego).
19. Zapis wyborów w ustawieniach; onboarding pokazywany tylko raz, z możliwością powrotu
    z ustawień.

### DoD fazy 4

- [ ] Każdy komponent renderuje się w galerii w obu motywach, 100 % i 200 % skali czcionki.
- [ ] Nawigacja między wszystkimi 9 trasami działa (placeholdery).
- [ ] Onboarding przechodzi end-to-end na Androidzie i iOS.
- [ ] Snapshoty screenshotowe w CI.
- [ ] Zero literalnych stringów w UI (check w CI aktywny).

---

## FAZA 5 — Treści wbudowane i importer (tygodnie 6–7)

**Cel:** pełnowartościowe decki DE/EN (zdania + słowa) z pregenerowanymi asocjacjami
i wskazówkami, importowane transakcyjnie, idempotentnie, z lintem w CI.
Obecny stan wyjściowy: 4 decki / 113 kart, importer z resolverem lemma już działa.

### Krok 1 — decyzje wejściowe

1. Rozstrzygnąć `[DECIDE] D-04` (źródło treści; domyślnie LLM + weryfikacja native speakera)
   i `[DECIDE] D-06` (języki UI; domyślnie PL + EN).
2. Ustalić docelowy rozmiar decków (rekomendacja: 300–500 zdań + 800–1 200 słów na język)
   — ta liczba steruje nakładem pracy nad treścią bardziej niż jakakolwiek decyzja w kodzie.

### Krok 2 — schemat plików decków

3. Sfinalizować `schemaVersion: 1` wg rozszerzonego schematu z `plan.md`
   (`deckGuid`, `contentVersion`, `sourceLanguage`, `license`, `guid` kart, `associations`,
   `wordLinks` z `startIndex`/`endIndex`).
4. Napisać plik JSON-Schema (`content/schema/deck.schema.json`) i walidować nim pliki
   w linterze.
5. Zaktualizować `BundledDeckSchema.kt` do nowego schematu; importer odrzuca nieznany
   `schemaVersion` zamiast importować połowicznie.

### Krok 3 — produkcja treści (może biec równolegle z krokami 4–6)

6. Wygenerować kandydatów (LLM) dla: deck zdań DE, deck słów DE, deck zdań EN, deck słów EN —
   tłumaczenia na **polski** (nie angielski — C3), tagi poziomów A1/A2.
7. Pregenerować asocjacje mnemoniczne i wskazówki gramatyczne dla wszystkich kart
   (skrypt w `content/tools/`, z wersjonowanym promptem).
8. Przegląd jakości i bezpieczeństwa (C7) przez native speakera / weryfikatora —
   każda karta zaakceptowana ręcznie przed wejściem do repo.
9. Uzupełnić `license` w każdym pliku; jeśli użyto korpusu zewnętrznego — przygotować
   ekran atrybucji (do fazy 10).

### Krok 4 — importer (rozszerzenie istniejącego)

10. `BundledDeckImporter`: parsowanie `associations` (typ `Bundled`), zapis z `cache_key`;
    ustawianie `deck.contentVersion`, `sourceLanguage`, `targetLanguage`.
11. Resolver `wordCardRef`: reguły lemma/kontrakcje/homografy z `plan.md`; linki
    wielotokenowe przez `startIndex`/`endIndex`.
12. Kolejność: decki słów **przed** deckami zdań (już wymuszone — utrzymać test, który
    głośno failuje przy naruszeniu).
13. Ścieżka aktualizacji treści: import gdy `bundled.contentVersion > local.contentVersion`,
    dopasowanie kart po `guid` — nowe karty dochodzą, zmienione się aktualizują,
    **postęp nauki (`due_at`, `stability`, `reps`) pozostaje nietknięty**.
14. Import nieblokujący: pierwszy start pokazuje stan „przygotowuję decki" z postępem,
    nie zamrożony splash.
15. Jeśli benchmark (krok 6) nie przejdzie — przejście na NDJSON streaming + batchowane
    inserty w jednej transakcji.

### Krok 5 — linter treści w CI

16. Rozszerzyć `BundledContentLinterTest` / task Gradle o pełną listę reguł:
    nierozwiązany `wordCardRef`, duplikat `guid` (w decku i między deckami), duplikat
    `front` w decku, brak wymaganych tipów/asocjacji dla typu decka, malformed JSON,
    nieznany `schemaVersion`, asocjacja dłuższa niż projektowy limit znaków, brak
    `sourceLanguage`/`targetLanguage`.
17. Asercje liczby kart per deck („German - Sentences ma 500 kart" jako test, nie nadzieja).
18. Fixture celowo zepsutego decka + test, że linter go łapie.

### Krok 6 — benchmark

19. Import 5 000 kart < 5 s na urządzeniu ze średniej półki, z widocznym postępem.

### Kryteria akceptacji fazy 5

- Świeża instalacja → decki DE (zdania + słowa) obecne, każdy `wordCardRef` rozwiązany,
  zero nierozwiązanych linków.
- Ponowne uruchomienie importera niczego nie zmienia (test idempotencji).
- Podbicie `contentVersion` + dodanie 50 kart zachowuje `due`, `stability`, `reps`
  wszystkich wcześniejszych kart (test regresyjny — chroni każdą przyszłą aktualizację treści).
- Import 5 000 kart < 5 s.
- Linter failuje build na zepsutym fixture.

---

## FAZA 6 — Domena / przypadki użycia (tydzień 8)

**Cel:** wszystkie use case'y potrzebne do walking skeleton (faza 7) i zarządzania treścią,
z testami na fake'owych repozytoriach.

### Krok 1 — dokończenie silnika (zaległości z fazy 2)

1. Przypiąć wersję FSRS-6 i domyślny wektor 21 wag (`w0`–`w20`) z implementacji
   referencyjnej; test asertujący liczbę i wartości wag.
2. Obsługa powtórek tego samego dnia (komponent pamięci krótkotrwałej, `w17`–`w19`).
3. Kroki uczenia/przeuczania (`[1m, 10m]`, `[10m]`) z graduacją i easy-bonusem.
4. Deterministyczny fuzz interwałów (seed z `guid` karty), clamping min/max interwału,
   reguła „Hard".
5. `nextIntervalPreview(rating)` dla wszystkich 4 ocen — API pod przyciski w UI.
6. **Testy konformacji** przeciw wektorom referencyjnym (`rs-fsrs`/`py-fsrs`) — fixtures
   w `commonTest/fsrs/vectors/`, tolerancja 1e-4; sekwencje wg Appendixu A w `plan.md`.
7. Testy property-based: monotoniczność w ocenie, difficulty ∈ [1,10], stability > 0,
   brak NaN/Infinity dla dowolnych wejść (w tym 10-letni elapsed).

### Krok 2 — Scheduler (osobny komponent, nie część FSRS)

8. `Scheduler` w `shared/domain/scheduler/`: budowanie kolejki, dzienne limity nowych
   i powtórek, `newCardGatherPriority`, `reviewOrder`, interleaving nowe/powtórki,
   bury rodzeństwa, wykluczenie suspended/buried, cutoff dnia (C2).
9. Testy kolejności kolejki: limity respektowane, rodzeństwo ukryte, karta suspended
   nigdy nie wychodzi.

### Krok 3 — use case'y krytyczne dla sesji

10. `GetDueCardsUseCase` — cienka fasada na Scheduler.
11. `ScheduleReviewUseCase` — ocena → FSRS → zapis karty + `ReviewLog` **w jednej
    transakcji**; wypełnia `state_before`, `stability_before`, `difficulty_before`,
    `duration_ms`, `fsrs_version`, `parameters_hash`.
12. `UndoLastReviewUseCase` — odtworzenie poprzedniego stanu karty z ostatniego wpisu
    `review_logs` (dlatego pola `*_before` istnieją) + usunięcie/oznaczenie wpisu.
13. `GetStudyStatsUseCase` — liczby due/new/overdue per deck, streak, czas nauki.

### Krok 4 — zarządzanie treścią

14. `CreateDeckUseCase` / `UpdateDeckUseCase` / `DeleteDeckUseCase` (soft-delete).
15. `CreateCardUseCase` / `UpdateCardUseCase` / `DeleteCardUseCase` — tworzenie przez
    notatkę (Note + karty z szablonów).
16. `AddGrammarTipUseCase` / `RemoveGrammarTipUseCase` / `ReorderGrammarTipsUseCase`
    (reorder transakcyjny — crash w połowie nie zostawia zdublowanych `order`).
17. `LinkWordToSentenceUseCase` / `UnlinkWordFromSentenceUseCase` /
    `GetLinkedWordDetailsUseCase` (z regułami resolucji: exact → znormalizowany → picker).
18. `SuspendCardUseCase` / `BuryCardUseCase` / `SetFlagUseCase`.
19. `ChangeDeckTypeUseCase` — reguła ukrywa-nie-kasuje + payload ostrzeżenia
    („3 asocjacje i 5 wskazówek zostanie ukrytych").
20. `SearchCardsUseCase` — podzbiór składni Anki: free text, `deck:`, `tag:`, `is:due`,
    `is:new`, `is:suspended`, `added:7`; backend: tabela wirtualna FTS5 nad polem
    sortowania/treścią (osobna migracja).

### Testy fazy 6

- Każdy use case: unit testy z fake repozytorium.
- `ScheduleReviewUseCase`: test transakcyjnego rollbacku — symulowany crash między
  aktualizacją karty a zapisem logu zostawia bazę spójną.
- `UndoLastReviewUseCase`: ocena → undo → stan karty bitowo równy stanowi sprzed oceny.
- Parser zapytań `SearchCardsUseCase`: testy tablicowe składni.

### DoD fazy 6

- [ ] Wektory konformacji FSRS zielone.
- [ ] `nextIntervalPreview` dostępne z warstwy UI.
- [ ] Scheduler z testami kolejności.
- [ ] Test rollbacku transakcji zielony.

---

## FAZA 7 — Walking Skeleton: Dashboard → Sesja → Podsumowanie (tygodnie 9–10)

**Cel:** po tej fazie aplikacją da się realnie uczyć niemieckiego, offline, na obu
platformach. Wszystko później pogrubia działający produkt.

### Krok 1 — Dashboard (`/home`)

1. Lista decków: nazwa, ikona języka, badge typu decka, due dziś / nowe / zaległe
   (dane z `GetStudyStatsUseCase`, odświeżane reaktywnie).
2. Pasek dziennego postępu + licznik streak.
3. Zdefiniować streak **precyzyjnie** (decyzja zapisana w ADR): łamie się na cutoffie dnia
   (C2); dzień z ≥ 1 powtórką liczy się; bez dni ochronnych w v1.
4. `+ Nowy deck` (minimalny dialog — pełny edytor decka w fazie 10).
5. `Ucz się wszystkiego` (kolejka łączona przez Scheduler).
6. Stany: pusty (brak decków — CTA do decków wbudowanych), offline indicator,
   pull-to-refresh.

### Krok 2 — Sesja nauki (`/study/{deckId}`)

7. Pętla: front → odsłonięcie → 4 przyciski ocen z **podglądem następnego interwału**
   („1 min · 8 min · 4 d · 9 d") z `nextIntervalPreview`.
8. Sekcje zależne od `DeckCapabilities`: `Simple` → tylko front/back;
   `TextWithAssociations` → sekcja asocjacji dla całego tekstu;
   `Linguistic` → asocjacje + wskazówki gramatyczne (domyślnie zwinięte) + linkowane słowa.
9. Linkowane słowa: tap otwiera bottom sheet z tłumaczeniem, asocjacjami i wskazówkami
   danego słowa (`GetLinkedWordDetailsUseCase`); homograf (`Ambiguous`) → arkusz
   disambiguacji, wybór zapisany jako `UserOverridden`.
10. **Undo** (toolbar + gest), podpięte do `UndoLastReviewUseCase`.
11. Akcje na karcie bez opuszczania sesji: suspend, bury, flaga, edytuj (deep link do
    edytora po fazie 9 — na razie placeholder), usuń.
12. Skróty klawiaturowe: 1–4 = ocena, spacja = odsłonięcie.
13. Pomiar czasu odpowiedzi do `ReviewLog.durationMs` z limitem bezczynności
    (karta otwarta 40 minut nie zapisuje 40 minut nauki).
14. **Przetrwanie process death**: oceny zapisywane natychmiast (transakcja per ocena);
    stan sesji (pozycja w kolejce) odtwarzany po zabiciu procesu.
15. Przycisk TTS dla strony w języku uczonym — `expect/actual`
    (`android.speech.tts` / `AVSpeechSynthesizer`).
16. Stan „brak kart na dziś": oferta nauki z wyprzedzeniem lub podniesienia limitu
    nowych — nie ślepy zaułek.

### Krok 3 — Podsumowanie sesji

17. Liczba powtórek, rozkład ocen, retencja sesji, czas trwania.
18. Lista kart „Again" z opcją natychmiastowej powtórki.
19. Afirmacja streaka + zapowiedź następnych due („kolejnych 12 kart za 3 h").

### Krok 4 — spięcie i weryfikacja na urządzeniach

20. Pełny przebieg na fizycznym Androidzie **i** iPhonie (R5: luki Compose MP na iOS —
    input, scroll, a11y — mają wyjść teraz, nie w fazie 13).
21. Pomiar zimnego startu → pierwsza karta; cel < 2 s.
22. Nagranie przejścia TalkBack i VoiceOver przez sesję (C4 — od tej fazy co fazę).

### Kryteria akceptacji fazy 7

- Użytkownik: instalacja → onboarding → 20 kart niemieckiego → zamknięcie → następnego
  dnia poprawne liczniki due.
- Oceny przeżywają zabicie procesu.
- Cały flow działa w trybie samolotowym.
- Zimny start → pierwsza karta < 2 s na urządzeniu ze średniej półki.

---

## FAZA 8 — Silnik asocjacji (tydzień 11)

**Cel:** generowanie mnemotechnik przez LLM API — z cache, kolejką offline, moderacją
i limitami; nigdy nie blokuje sesji nauki.

### Krok 1 — prompty

1. `AssociationPromptBuilder` z **trzema wersjonowanymi szablonami**:
   słowo (Substitution Word), zdanie (Chain Association / story method),
   cały tekst (key-term chaining); `[NATIVE_LANGUAGE]` jako parametr, nie „Polish" na sztywno.
2. Ograniczenia negatywne w system prompcie (C7): bez treści seksualnych, drastycznej
   przemocy, wyzwisk, realnych osób.
3. Wymuszony format wyjścia: JSON (`{"association": "...", "substituteWord": "..."}`),
   tryb JSON dostawcy; twardy limit znaków zgodny z projektowanym obszarem UI
   (ten sam limit egzekwuje linter treści).
4. Przypiąć `temperature`, `model`, `max_tokens`; `promptVersion` i `modelId` zapisywane
   przy każdej wygenerowanej asocjacji.

### Krok 2 — klient i use case

5. Klient Ktor z timeoutem 10 s i anulowaniem przy opuszczeniu ekranu.
6. `GenerateAssociationUseCase`: branch po `DeckType` + `CardType`; ścisłe parsowanie JSON
   z jednym retry na błąd parsowania.
7. Minimalizacja danych (C6): w request idzie wyłącznie tekst karty, L1, L2 — nigdy
   ID użytkownika/urządzenia, nazwa decka ani historia.
8. `SaveFavoriteAssociationUseCase` — indeks „jeden ulubiony na notatkę" z fazy 3.5
   robi resztę.

### Krok 3 — cache i offline (C5)

9. Cache lokalny kluczowany `(text, L1, L2, promptVersion)` — trafienie nie dotyka sieci.
10. Kolejka offline: żądanie bez sieci → stan „wygeneruje się po połączeniu" (nie toast
    błędu) → retry po odzyskaniu łączności.

### Krok 4 — moderacja i bezpieczeństwo (C7)

11. Pass moderacyjny na każdym wygenerowanym wyniku; blokada → jeden cichy retry →
    neutralny komunikat zastępczy.
12. Akcja „zgłoś tę asocjację" logująca prompt + output do przeglądu.
13. Badge „wygenerowane przez AI" na asocjacjach typu `Generated` (C6 / AI Act).

### Krok 5 — limity i UX

14. Rate limiting i UI quoty (D-02); czytelny komunikat przy wyczerpaniu darmowego limitu.
15. „Wygeneruj ponownie" i „3 warianty" — pierwsza propozycja często nie jest tą,
    która zostaje.
16. Pięć rozróżnionych, przetestowanych stanów błędu: brak sieci, błąd API, zła odpowiedź,
    blokada moderacji, przekroczona quota — nie jeden generyczny toast.
17. Klucz API w ustawieniach czytany/zapisywany przez `SecureStorage`.
18. Skeleton natychmiast po tapnięciu; generacja nigdy nie blokuje oceniania karty.

### Testy fazy 8

- Unit testy z fake'owym klientem LLM (wszystkie 5 stanów błędu).
- Test kontraktowy na nagranej prawdziwej odpowiedzi.
- Test cache: drugie żądanie o ten sam klucz = zero wywołań sieciowych.
- Test kolejki offline: żądanie offline → reconnect → wynik.

### DoD fazy 8

- [ ] Generacja bez sieci pokazuje stan oczekujący i kończy się po reconnect.
- [ ] Celowo niebezpieczne wejście jest blokowane i logowane.
- [ ] p90 latencji zmierzone i zapisane (cel < 4 s).

---

## FAZA 9 — Szczegóły karty, edytor, przeglądarka (tygodnie 12–13)

### Krok 1 — Szczegóły karty / menedżer asocjacji (`/card/{id}`)

1. Front/back, ulubiona asocjacja, historia wygenerowanych, lista wskazówek gramatycznych,
   sekcja linkowanych słów; dodawanie / edycja / usuwanie / zmiana kolejności.
2. Panel stanu FSRS: stability, difficulty, bieżąca retrievability (liczona na żywo),
   data due, reps, lapses — dla power userów i do własnego debugowania.
3. Lista historii powtórek z `review_logs` (data, ocena, interwał).

### Krok 2 — Edytor karty (`/editor`)

4. Pola `Front`, `Back`, `Asocjacja`, `Wskazówki`, `Linkowane słowa`, wybór decka, tagi,
   podgląd na żywo, `Zapisz` / `Zapisz i dodaj kolejną`.
5. Widoczność pól sterowana `DeckCapabilities` (nie ręcznymi ifami per ekran).
6. Selektor typu notatki (Basic / Basic-and-reversed / Sentence) — notatka z 2 szablonami
   tworzy 2 karty.
7. **Wykrywanie duplikatów** po `Front` w obrębie decka, na żywo podczas pisania.
8. Edytor linków słów: tap na słowo w zdaniu → linkowanie; arkusz disambiguacji dla
   `Ambiguous`; brak dopasowania → picker, nigdy ciche zgadywanie.
9. Guard niezapisanych zmian przy nawigacji wstecz.
10. „Generuj asocjację" inline — tworzenie karty i mnemotechniki to jeden flow.

### Krok 3 — Przeglądarka kart (`/browser`)

11. Lista z podglądem, stanem FSRS, due/pozycją; ikony asocjacji / wskazówek / linków
    (prawdziwe ikony, C4).
12. Pasek wyszukiwania na `SearchCardsUseCase` + FTS5, z udokumentowanym podzbiorem składni.
13. Sortowanie po dowolnej kolumnie; filtry: tag, deck, stan, flaga.
14. **Multi-select + akcje masowe**: zmiana decka, tagi +/−, suspend, flaga, usuń,
    repozycjonowanie nowych — każda akcja masowa w jednej transakcji.
15. Paging (windowed query / PagingSource) — 50 000 wierszy nie może być jednym
    `LazyColumn` nad pełnym zapytaniem.

### DoD fazy 9

- [ ] Przeglądarka otwiera i scrolluje kolekcję 50 000 kart w 60 fps (fixture z fazy 3.5).
- [ ] Wyszukiwanie < 200 ms.
- [ ] Masowe suspendowanie 1 000 kart w jednej transakcji.
- [ ] Notatka „Basic-and-reversed" tworzy i pokazuje 2 niezależnie planowane karty.

---

## FAZA 10 — Przegląd decka, statystyki, ustawienia (tydzień 14)

### Krok 1 — Przegląd decka (`/deck/{id}`)

1. Nazwa, statystyki, `Rozpocznij powtórkę`, `Przeglądaj karty`, `Dodaj kartę`,
   sekcja ustawień decka z wyborem `DeckType` (przez `ChangeDeckTypeUseCase`
   z ostrzeżeniem ukrywa-nie-kasuje).
2. Drzewo subdecków („German::A1::Verbs") z rolowanymi licznikami; rename / move / delete
   z jasnym ostrzeżeniem co zostanie zniszczone.
3. Picker presetu opcji (`DeckConfig`) z informacją „ten preset jest używany przez 4 decki"
   przed edycją.

### Krok 2 — Statystyki (`/stats`)

4. Prognoza powtórek (respektująca dzienne limity i cutoff dnia — inaczej kłamie),
   heatmapa aktywności, streak, liczba powtórek, czas nauki.
5. **Retention zdefiniowana precyzyjnie**: true retention = `Good+Easy / wszystkie oceny`
   na kartach w stanie `Review`, w wybieralnym oknie, bez ocen w stanie uczenia;
   raportowana per bucket (young < 21 d, mature ≥ 21 d). Definicja w tooltipie i w ADR.
6. Rozkład kart po stanach (new / learning / young / mature / suspended); rozkład stability.
7. Scatter „difficulty vs stability" za przełącznikiem „zaawansowane".
8. Empty state dla każdego wykresu — nowy użytkownik nie może zobaczyć pustego obszaru
   wyglądającego jak crash.

### Krok 3 — Ustawienia (`/settings`)

9. FSRS (requestRetention, maxInterval — per preset), asocjacje, motyw, notyfikacje,
   import/eksport, about.
10. Pickery języka ojczystego (L1) i języka UI (C3).
11. Godzina cutoffu dnia + strefa czasowa (C2).
12. **Lokalny backup / restore**: eksport całej kolekcji do jednego pliku + automatyczny
    tygodniowy backup lokalny (rolling, np. 3 ostatnie). `Reset progress` dostępny
    **dopiero po** istniejącym backupie lub wyraźnym potwierdzeniu z hasłem-frazą.
13. Prywatność: przełącznik zgody na generowanie AI, opt-in analityki (D-08),
    „usuń wszystkie moje dane".
14. Ekran atrybucji (jeśli D-04 użył licencjonowanego korpusu) + licencje open-source.
15. Diagnostyka: wersja appki, wersja schematu DB, wersja FSRS, „eksportuj log debug".

### DoD fazy 10

- [ ] Backup → czysta instalacja → restore → identyczne liczniki due i historia powtórek.
- [ ] Prognoza zgodna z rzeczywistą kolejką Schedulera na fixture.
- [ ] Zmiana presetu ostrzega o liczbie dotkniętych decków.

---

## FAZA 11 — Interop: CSV i Anki import/eksport (tygodnie 15–17)

**Kolejność wewnętrzna jest częścią planu ryzyka: CSV → eksport `.apkg` → import `.apkg`
(legacy → nowy format).** Całość cięta do samego CSV przy presji harmonogramu.

### Krok 1 — CSV (2 dni, pokrywa większość realnych potrzeb)

1. Import `front; back; deck; tags; due`: detekcja delimitera (`,`/`;`/tab — polski Excel
   pisze średniki) i kodowania (BOM), mapowanie po nagłówkach (już częściowo istnieje
   w `CsvDeckImporter` — rozszerzyć).
2. Krok podglądu i mapowania kolumn przed importem.
3. Dry run: „412 nowych, 8 duplikatów, 2 błędy" przed zatwierdzeniem.
4. Eksport CSV zawsze z `due` jako ISO-8601 (czytelny dla człowieka).

### Krok 2 — eksport `.apkg` (najpierw eksport — łatwiejszy, daje fixtures dla importu)

5. `AnkiDueCodec`: konwersja `dueAt: Instant` ↔ polimorficzne `due` Anki
   (new → pozycja; learning → epoch s; review → dni od `collectionCreatedAt`);
   pełne pokrycie testami czterech typów kart **zanim** powstanie writer.
6. `ApkgWriter`: SQLite schema 11 (legacy), mapowanie pól wg Appendixu B (`factor`
   permille, `flds` łączone 0x1F, tagi spacjami z otoczeniem, `guid` notatek).
7. Implementacja wyłącznie z udokumentowanej specyfikacji — **żadnego kodu Anki/AnkiDroid
   (AGPL/GPL) w aplikacji**.
8. Weryfikacja: wyeksportowany plik otwiera się w aktualnym Anki desktop bez błędów,
   liczby due się zgadzają.

### Krok 3 — import `.apkg`

9. Najpierw formaty legacy 1 i 2 (nieskompresowany SQLite, schema 11); `anki21b`
   (zstd + protobuf, schema 18) później, do tego czasu czytelny komunikat
   „nieobsługiwana wersja eksportu — wyeksportuj w starszym formacie".
10. Mapowanie: `due`, `factor`, `reps`, `lapses`, `ivl`, `queue`, `type`, `flags`,
    `odue`/`odid` (zachowane verbatim), `guid` notatek — przez `AnkiDueCodec`.
11. Typy notatek i szablony: notatka z 2 kartami importuje się jako 2 karty.
12. Media: import plików + przepisanie referencji, albo jawne pominięcie z ostrzeżeniem —
    nigdy ciche gubienie obrazków.
13. Polityka konfliktów przy re-imporcie: dopasowanie po `guid`, wybór użytkownika
    `update | skip | duplicate`.

### Krok 4 — testy round-trip

14. Eksport → re-import → `due`, `stability`, `difficulty`, `reps`, `lapses` bez zmian.
15. Pokrycie: wszystkie 4 stany kart, suspended i buried, notatki 2-kartowe, tagi ze
    spacjami, unicode, pusty deck, deck 20 000 kart (wydajność).
16. **Korpus fixtures firm trzecich**: 5–10 prawdziwych `.apkg` z różnych wersji Anki
    w repo testowym — testowanie tylko własnych eksportów nie dowodzi interoperacyjności.

### DoD fazy 11

- [ ] Deck wyeksportowany z aktualnego Anki desktop importuje się z poprawnymi datami due
  dla każdego stanu karty, zweryfikowane przez re-eksport i diff z wartościami Anki.

---

## FAZA 12 — Notyfikacje i instrumentacja (tydzień 18)

### Krok 1 — wspólna logika

1. `expect/actual` `Notifications` w `shared/platform/`.
2. Reguły: dzienne przypomnienie z liczbą kart; brak kart → brak powiadomienia;
   quiet hours; czas przypomnienia per dzień; snooze; deep link prosto do sesji.

### Krok 2 — Android

3. WorkManager + kanał notyfikacji; obsługa ograniczeń exact-alarm na API 31+.
4. Obejście OEM battery killers (Xiaomi/Huawei/Samsung): wykrycie agresywnego OEM +
   ekran instruktażowy w appce; **nie polegać na notyfikacjach jako jedynym mechanizmie
   retencji** (R11).

### Krok 3 — iOS

5. Lokalne notyfikacje `UNUserNotificationCenter` pre-schedulowane w oknie kroczącym
   (np. 14 dni; limit 64 pending), odświeżane przy każdym otwarciu appki.
6. Liczba kart w treści iOS to predykcja z momentu schedulowania — użyć sformułowania
   generycznego albo zaakceptować nieświeżość (decyzja w ADR).

### Krok 4 — moment pytania o zgodę

7. Prompt o pozwolenie na notyfikacje **po pierwszej ukończonej sesji**, nie przy pierwszym
   uruchomieniu (duża różnica w opt-in).

### Krok 5 — instrumentacja metryk sukcesu (D-08)

8. Eventy: start sesji, liczba powtórek, asocjacja wygenerowana/zapisana, latencja i błędy
   generacji, crash-free rate — dokładnie pod tabelę metryk sukcesu z `plan.md`.
9. Opt-in, przegląd prywatności, **nigdy treść kart w telemetrii**.
10. Weryfikacja liczenia D7 retention i median session start w danych testowych.

### DoD fazy 12

- [ ] Przypomnienie przychodzi o skonfigurowanej porze na obu platformach; deep link
  otwiera sesję.
- [ ] Zero powiadomień przy pustej kolejce.
- [ ] Dashboard metryk pokazuje eventy z builda beta.

---

## FAZA 13 — Szlif, wydajność, hardening testów (tygodnie 19–20)

### Krok 1 — animacje i szlif UI

1. Animacja odwrócenia karty, reveal asocjacji i wskazówek, przejścia ekranów.

### Krok 2 — domknięcie testów

2. Unit: FSRS, scheduler, silnik asocjacji, repozytoria wskazówek i linków,
   `AnkiDueCodec`, importery.
3. UI (end-to-end): dodaj kartę → sesja → ocena → zapis asocjacji; karta zdaniowa ze
   wskazówkami i linkami; podgląd linkowanego słowa.
4. UI dodatkowe: undo, suspend/bury, zmiana typu decka (dane zachowane), asocjacja
   offline, onboarding, import.
5. Monkey/fuzz testing na sesji nauki i importerze.

### Krok 3 — wydajność z konkretnymi budżetami

6. Zimny start < 2 s; scroll przeglądarki 50 000 kart w 60 fps; zapytanie due < 50 ms;
   import 5 000 kart < 5 s; budżet rozmiaru APK/IPA; sufit pamięci podczas importu —
   każdy budżet jako test/pomiar w CI lub udokumentowany pomiar na urządzeniu.
7. Baseline Profile dla Androida (tani, mierzalny zysk na zimnym starcie Compose).
8. Test baterii/termiki: 30-minutowa sesja nauki.

### Krok 4 — QA lokalizacji i dostępności

9. Każdy ekran przy 200 % skali czcionki w PL i EN; niemieckie złożenia w UI;
   sanity check RTL (tanio teraz, drogo później).
10. Finalny audyt a11y (C4 egzekwowane per faza — to weryfikacja, nie pierwszy kontakt).

### Krok 5 — drill utraty danych (R7)

11. Force-kill podczas: importu, sesji, migracji — weryfikacja odzysku w każdym przypadku.
12. Przegląd obsługi błędów API (wszystkie ścieżki z fazy 8 wciąż rozróżnialne).

### DoD fazy 13

- [ ] Wszystkie budżety wydajności zmierzone i spełnione (lub odchylenia świadomie
  zaakceptowane i zapisane).
- [ ] Drill utraty danych: 3/3 scenariusze odzyskane bez utraty ocen.

---

## FAZA 14 — Beta i wydanie (tygodnie 21–22)

### Krok 1 — zgodność i sklepy (zacząć równolegle już od fazy 8!)

1. Polityka prywatności + regulamin: hosting, linki w appce i w obu sklepach
   (aplikacja rozmawiająca z LLM API nie może wyjść bez tego).
2. Google Play Data Safety + Apple App Privacy — spójne z tym, co appka faktycznie wysyła (C6).
3. Apple: pytania o treści AI w App Review — zadeklarować moderację i zgłaszanie (C7);
   rating wiekowy (D-07).
4. Google Play: target API, deklaracja `POST_NOTIFICATIONS` i exact-alarm; Play Integrity
   jeśli proxy tego wymaga.
5. Ścieżka usunięcia konta/danych (dla appki lokalnej: udokumentować brak konta).
6. DPA z dostawcą LLM + pisemne zobowiązanie no-training; rejestr przetwarzania (RODO);
   lista subprocesorów.
7. Trademark check nazwy; „Anki" tylko w opisie („compatible with Anki decks"),
   nigdy w tytule/podtytule.
8. Ekran atrybucji open-source (SQLDelight, Koin, Ktor, …).

### Krok 2 — release engineering

9. Klucze podpisujące w CI (secure storage); powtarzalne buildy release.
10. Schemat wersjonowania + changelog czytelny dla użytkownika.
11. Crash reporting zweryfikowany na buildzie release z uploadem symboli/dSYM.
12. Assety sklepowe: ikona, screenshoty (obie platformy, kilka rozmiarów), feature graphic,
    opisy PL i EN, słowa kluczowe ASO.

### Krok 3 — beta zamknięta (min. 2 tygodnie)

13. TestFlight + Play internal testing, 20–50 prawdziwych uczących się — jedyna faza,
    w której FSRS spotyka prawdziwe wspomnienia.
14. Kanał feedbacku + rytm triage'u (np. codzienny przegląd zgłoszeń); poprawki płyną
    równolegle z betą.
15. Monitorowanie metryk sukcesu z fazy 12 na populacji beta.

### Krok 4 — rollout

16. Staged rollout w Play: 5 % → 20 % → 50 % → 100 %, z bramką crash-rate na każdym progu.
17. Kill switch generacji asocjacji (remote config) na wypadek incydentu kosztowego lub
    bezpieczeństwa.
18. Spisany plan rollbacku.
19. Przejście pełnej checklisty *Release & Compliance* z `plan.md` — pozycja po pozycji.

### DoD fazy 14

- [ ] Aplikacja opublikowana w obu sklepach; rollout na 100 % lub świadomie wstrzymany.
- [ ] Crash-free ≥ 99,5 % na populacji beta.
- [ ] Wszystkie pozycje checklisty compliance odhaczone.

---

## FAZA 15 — Po starcie: optymalizator FSRS i v1.1

### Krok 1 — lokalna optymalizacja parametrów

1. Implementacja on-device optymalizacji 21 wag FSRS z własnych `review_logs`
   użytkownika (historia nie opuszcza telefonu — przewaga prywatnościowa warta
   komunikacji w sklepie).
2. Oferowana po ~1 000 powtórek; przed zastosowaniem pokazuje porównanie
   przewidywanego obciążenia przed/po.
3. Filtrowanie historii po `parameters_hash` — logi z różnych wektorów wag nie mieszają
   się w treningu (po to te pola istnieją od fazy 3.5).

### Krok 2 — walidacja jakości schedulingu

4. Agregowana (opt-in, anonimizowana) analiza retencji: czy nasza implementacja
   planuje tak dobrze jak referencja (cel: true retention 85–92 %).

### Krok 3 — backlog v1.1 (priorytetowo)

5. Import `.apkg` jeśli odroczony (D-05) → target Desktop (D-01) → TTS wszędzie →
   karty z obrazem/audio → kolejne języki → synchronizacja (schemat już gotowy — C1).

---

## Ścieżka krytyczna i punkty kontrolne

```text
3.5 (retrofit) → 4 (design system) → 6 (use cases) → 7 (walking skeleton)
                                     ↑
        5 (treści) biegnie równolegle od tygodnia 5 (autor treści ≠ developer)
```

| Punkt kontrolny | Kiedy | Co musi być prawdą |
|---|---|---|
| KM1 | koniec fazy 3.5 | migracja zielona, benchmark due < 50 ms, sekrety poza plaintextem |
| KM2 | koniec fazy 4 | 9 tras nawigowalnych, galeria komponentów, onboarding e2e |
| KM3 | koniec fazy 6 | wektory konformacji FSRS zielone — **bez tego nie zaczynać fazy 7** |
| KM4 | koniec fazy 7 | **pierwszy demowalny produkt** — nauka offline na obu platformach |
| KM5 | koniec fazy 10 | wszystkie 9 ekranów kompletne, backup/restore działa |
| KM6 | koniec fazy 13 | budżety wydajności spełnione, drill utraty danych zaliczony |
| KM7 | koniec fazy 14 | apka w sklepach, crash-free ≥ 99,5 % |

**Cięcia przy presji czasu (w tej kolejności):** import `.apkg` (→ v1.1), statystyki ponad
podstawowe liczniki, akcje masowe w przeglądarce, TTS, jeden z dwóch języków treści.
**Nigdy nie ciąć:** fazy 3.5, testów konformacji FSRS, backupów, bety.
