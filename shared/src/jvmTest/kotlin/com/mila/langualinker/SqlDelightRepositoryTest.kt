package com.mila.langualinker

import com.mila.langualinker.association.Association
import com.mila.langualinker.association.AssociationType
import com.mila.langualinker.data.repository.SqlDelightAssociationRepository
import com.mila.langualinker.data.repository.SqlDelightCardRepository
import com.mila.langualinker.data.repository.SqlDelightDeckRepository
import com.mila.langualinker.data.repository.SqlDelightGrammarTipRepository
import com.mila.langualinker.data.repository.SqlDelightReviewLogRepository
import com.mila.langualinker.data.repository.SqlDelightSentenceWordLinkRepository
import com.mila.langualinker.database.AppDatabase
import com.mila.langualinker.domain.model.Card
import com.mila.langualinker.domain.model.CardType
import com.mila.langualinker.domain.model.Deck
import com.mila.langualinker.domain.model.DeckSettings
import com.mila.langualinker.domain.model.DeckType
import com.mila.langualinker.domain.model.GrammarTip
import com.mila.langualinker.domain.model.GrammarTipSource
import com.mila.langualinker.domain.model.LinkResolution
import com.mila.langualinker.domain.model.ReviewLog
import com.mila.langualinker.domain.model.SentenceWordLink
import com.mila.langualinker.fsrs.CardFsrsState
import com.mila.langualinker.fsrs.CardState
import com.mila.langualinker.fsrs.Rating
import com.mila.langualinker.testdb.createTestDatabase
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration tests for the SQLDelight-backed repositories.
 *
 * These cover the two things a fake repository can never check: that the SQL is correct, and
 * that the row/domain mapping survives a round trip through SQLite's much smaller type system
 * (`REAL` is a double, dates are strings, tag lists are one comma-joined column).
 */
class SqlDelightRepositoryTest {

    private class Repos(val database: AppDatabase = createTestDatabase()) {
        val decks = SqlDelightDeckRepository(database)
        val cards = SqlDelightCardRepository(database)
        val tips = SqlDelightGrammarTipRepository(database)
        val links = SqlDelightSentenceWordLinkRepository(database)
        val associations = SqlDelightAssociationRepository(database)
        val reviewLogs = SqlDelightReviewLogRepository(database)
    }

    private fun fsrsState(
        state: CardState = CardState.Review,
        nextReview: LocalDate = LocalDate(2026, 7, 22),
    ) = CardFsrsState(
        due = 172_800L,
        stability = 12.5f,
        difficulty = 4.25f,
        retrievability = 0.84f,
        easeFactor = 2.65f,
        averageInterval = 10,
        lastReviewDate = LocalDate(2026, 7, 20),
        nextReviewDate = nextReview,
        scheduledDays = 2,
        elapsedDays = 1,
        reps = 7,
        lapses = 2,
        state = state,
    )

    private fun card(
        deckId: Long,
        front: String = "Ich gehe ins Kino.",
        position: Int = 0,
        tags: List<String> = listOf("a1", "travel"),
        cardType: CardType = CardType.Sentence,
        fsrs: CardFsrsState = fsrsState(),
    ) = Card(
        id = 0L,
        deckId = deckId,
        front = front,
        back = "Idę do kina.",
        tags = tags,
        cardType = cardType,
        position = position,
        fsrsState = fsrs,
        createdAt = Instant.fromEpochMilliseconds(1_780_000_000_000),
    )

    // ── Decks ─────────────────────────────────────────────────────────────────

    @Test
    fun deck_insertAndRead_roundTrips() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)

        val deck = repos.decks.getDeckById(deckId)
        assertEquals(Deck(deckId, "German A1", "de", DeckType.Linguistic), deck)
        assertEquals(listOf("German A1"), repos.decks.getAllDecks().first().map { it.name })
    }

    @Test
    fun deck_getByNameAndLanguage_findsTheDeck() = runTest {
        val repos = Repos()
        repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)
        repos.decks.insertDeck("German A1", "en", DeckType.Simple.name)

        val de = repos.decks.getDeckByNameAndLanguage("German A1", "de")
        assertEquals(DeckType.Linguistic, de?.type)
        assertNull(repos.decks.getDeckByNameAndLanguage("German A1", "pl"))
    }

    @Test
    fun deck_update_persistsTypeChange() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)

        repos.decks.updateDeck(Deck(deckId, "Niemiecki A1", "de", DeckType.Simple))

        val deck = repos.decks.getDeckById(deckId)!!
        assertEquals("Niemiecki A1", deck.name)
        assertEquals(DeckType.Simple, deck.type)
    }

    @Test
    fun deck_delete_cascadesToCardsTipsLinksAndLogs() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)
        val cardId = repos.cards.insertCard(card(deckId))
        repos.tips.insertTip(tip(cardId))
        repos.links.insertLink(link(cardId, wordCardId = null))
        repos.reviewLogs.insertReviewLog(reviewLog(cardId))
        repos.associations.insertAssociation(association(cardId))

        repos.decks.deleteDeck(deckId)

        assertNull(repos.decks.getDeckById(deckId))
        assertTrue(repos.cards.getCardsByDeckId(deckId).first().isEmpty())
        assertTrue(repos.tips.getTipsByCardId(cardId).first().isEmpty())
        assertTrue(repos.links.getLinksBySentenceCardId(cardId).isEmpty())
        assertTrue(repos.reviewLogs.getLogsByCardId(cardId).isEmpty())
        assertTrue(repos.associations.getAssociationsByCardId(cardId).first().isEmpty())
    }

    @Test
    fun deckSettings_upsertAndRead_roundTrips() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)

        repos.decks.upsertDeckSettings(
            DeckSettings(deckId, DeckType.Linguistic, 0.92f, 18_000, 30)
        )
        repos.decks.upsertDeckSettings(
            DeckSettings(deckId, DeckType.Linguistic, 0.85f, 36_500, 15)
        )

        val settings = repos.decks.getDeckSettings(deckId)!!
        assertEquals(0.85f, settings.requestRetention)
        assertEquals(36_500, settings.maximumInterval)
        assertEquals(15, settings.newCardsPerDay)
        assertEquals(DeckType.Linguistic, settings.type, "type comes from the deck itself")
    }

    // ── Cards ─────────────────────────────────────────────────────────────────

    @Test
    fun card_insertAndRead_preservesEveryFsrsField() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)
        val cardId = repos.cards.insertCard(card(deckId))

        val stored = repos.cards.getCardById(cardId)!!
        assertEquals("Ich gehe ins Kino.", stored.front)
        assertEquals("Idę do kina.", stored.back)
        assertEquals(listOf("a1", "travel"), stored.tags)
        assertEquals(CardType.Sentence, stored.cardType)
        assertEquals(fsrsState(), stored.fsrsState)
        assertEquals(Instant.fromEpochMilliseconds(1_780_000_000_000), stored.createdAt)
    }

    @Test
    fun card_getCardsByDeckId_ordersByPosition() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)
        repos.cards.insertCard(card(deckId, front = "third", position = 2))
        repos.cards.insertCard(card(deckId, front = "first", position = 0))
        repos.cards.insertCard(card(deckId, front = "second", position = 1))

        val fronts = repos.cards.getCardsByDeckId(deckId).first().map { it.front }
        assertEquals(listOf("first", "second", "third"), fronts)
    }

    @Test
    fun card_getDueCards_usesIsoDateComparison() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)
        repos.cards.insertCard(
            card(deckId, front = "overdue", fsrs = fsrsState(nextReview = LocalDate(2026, 7, 1)))
        )
        repos.cards.insertCard(
            card(deckId, front = "today", position = 1, fsrs = fsrsState(nextReview = LocalDate(2026, 7, 15)))
        )
        repos.cards.insertCard(
            card(deckId, front = "future", position = 2, fsrs = fsrsState(nextReview = LocalDate(2026, 9, 1)))
        )

        val due = repos.cards.getDueCards(deckId, "2026-07-15").map { it.front }
        assertEquals(listOf("overdue", "today"), due)
    }

    @Test
    fun card_getNewCards_returnsOnlyNewState() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)
        repos.cards.insertCard(card(deckId, front = "new", fsrs = fsrsState(state = CardState.New)))
        repos.cards.insertCard(
            card(deckId, front = "review", position = 1, fsrs = fsrsState(state = CardState.Review))
        )

        assertEquals(listOf("new"), repos.cards.getNewCards(deckId).map { it.front })
    }

    @Test
    fun card_updateFsrsState_persistsTheNewSchedule() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)
        val cardId = repos.cards.insertCard(card(deckId, fsrs = fsrsState(state = CardState.New)))

        val updated = fsrsState(state = CardState.Relearning).copy(reps = 9, lapses = 3)
        repos.cards.updateCardFsrsState(cardId, updated)

        assertEquals(updated, repos.cards.getCardById(cardId)!!.fsrsState)
    }

    @Test
    fun card_updateCard_changesContentButNotSchedule() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)
        val cardId = repos.cards.insertCard(card(deckId))

        val stored = repos.cards.getCardById(cardId)!!
        repos.cards.updateCard(stored.copy(front = "Neue Vorderseite", tags = listOf("b1")))

        val updated = repos.cards.getCardById(cardId)!!
        assertEquals("Neue Vorderseite", updated.front)
        assertEquals(listOf("b1"), updated.tags)
        assertEquals(fsrsState(), updated.fsrsState, "editing content must not reset scheduling")
    }

    @Test
    fun card_emptyTagList_roundTripsAsEmpty() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)
        val cardId = repos.cards.insertCard(card(deckId, tags = emptyList()))

        assertEquals(emptyList(), repos.cards.getCardById(cardId)!!.tags)
    }

    // ── Word links ────────────────────────────────────────────────────────────

    @Test
    fun link_insertAndRead_roundTripsResolution() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)
        val sentenceId = repos.cards.insertCard(card(deckId))
        val wordId = repos.cards.insertCard(card(deckId, front = "gehen", position = 1, cardType = CardType.Word))

        repos.links.insertLink(
            SentenceWordLink(0L, sentenceId, wordId, 0, "gehe", LinkResolution.Resolved)
        )
        repos.links.insertLink(
            SentenceWordLink(0L, sentenceId, null, 1, "Kino", LinkResolution.Unresolved)
        )

        val links = repos.links.getLinksBySentenceCardId(sentenceId)
        assertEquals(2, links.size)
        assertEquals(wordId, links[0].wordCardId)
        assertEquals(LinkResolution.Resolved, links[0].resolution)
        assertNull(links[1].wordCardId)
        assertEquals(LinkResolution.Unresolved, links[1].resolution)
    }

    @Test
    fun link_deletingTheWordCard_unresolvesInsteadOfOrphaning() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)
        val sentenceId = repos.cards.insertCard(card(deckId))
        val wordId = repos.cards.insertCard(card(deckId, front = "gehen", position = 1, cardType = CardType.Word))
        repos.links.insertLink(
            SentenceWordLink(0L, sentenceId, wordId, 0, "gehe", LinkResolution.Resolved)
        )

        repos.cards.deleteCard(wordId)

        val link = repos.links.getLinksBySentenceCardId(sentenceId).single()
        assertNull(link.wordCardId, "the link must not keep a dangling card id")
        assertEquals(LinkResolution.Unresolved, link.resolution)
        assertEquals("gehe", link.surfaceForm, "the surface form survives so the UI can still render it")
    }

    @Test
    fun link_updateResolution_recordsAUserOverride() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)
        val sentenceId = repos.cards.insertCard(card(deckId))
        val wordId = repos.cards.insertCard(card(deckId, front = "sie", position = 1, cardType = CardType.Word))
        repos.links.insertLink(
            SentenceWordLink(0L, sentenceId, null, 0, "sie", LinkResolution.Ambiguous)
        )
        val linkId = repos.links.getLinksBySentenceCardId(sentenceId).single().id

        repos.links.updateResolution(linkId, wordId, LinkResolution.UserOverridden)

        val link = repos.links.getLinksBySentenceCardId(sentenceId).single()
        assertEquals(wordId, link.wordCardId)
        assertEquals(LinkResolution.UserOverridden, link.resolution)
    }

    // ── Grammar tips ──────────────────────────────────────────────────────────

    @Test
    fun tips_areReturnedInSortOrder() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)
        val cardId = repos.cards.insertCard(card(deckId))
        repos.tips.insertTip(tip(cardId, content = "third", order = 2))
        repos.tips.insertTip(tip(cardId, content = "first", order = 0))
        repos.tips.insertTip(tip(cardId, content = "second", order = 1))

        val contents = repos.tips.getTipsByCardId(cardId).first().map { it.content }
        assertEquals(listOf("first", "second", "third"), contents)
    }

    @Test
    fun tips_reorder_leavesNoDuplicateOrderValues() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)
        val cardId = repos.cards.insertCard(card(deckId))
        val a = repos.tips.insertTip(tip(cardId, content = "a", order = 0))
        val b = repos.tips.insertTip(tip(cardId, content = "b", order = 1))
        val c = repos.tips.insertTip(tip(cardId, content = "c", order = 2))

        repos.tips.reorderTips(listOf(c, a, b))

        val tips = repos.tips.getTipsByCardId(cardId).first()
        assertEquals(listOf("c", "a", "b"), tips.map { it.content })
        assertEquals(listOf(0, 1, 2), tips.map { it.order })
    }

    @Test
    fun tips_updateContent_persists() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)
        val cardId = repos.cards.insertCard(card(deckId))
        val tipId = repos.tips.insertTip(tip(cardId))

        repos.tips.updateTipContent(tipId, "Poprawiona wskazówka")

        assertEquals(
            "Poprawiona wskazówka",
            repos.tips.getTipsByCardId(cardId).first().single().content,
        )
    }

    // ── Associations ──────────────────────────────────────────────────────────

    @Test
    fun association_insertAndRead_roundTrips() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)
        val cardId = repos.cards.insertCard(card(deckId))

        val id = repos.associations.insertAssociation(
            association(cardId, content = "Kino kojarzy się z kinem w Kinie", isFavorite = true)
        )

        val stored = repos.associations.getAssociationsByCardId(cardId).first().single()
        assertEquals(id, stored.id)
        assertEquals("Kino kojarzy się z kinem w Kinie", stored.content)
        assertEquals(AssociationType.Generated, stored.type)
        assertTrue(stored.isFavorite)
    }

    @Test
    fun association_markingAFavorite_clearsThePreviousOne() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)
        val cardId = repos.cards.insertCard(card(deckId))
        val first = repos.associations.insertAssociation(
            association(cardId, content = "first", isFavorite = true)
        )
        val second = repos.associations.insertAssociation(
            association(cardId, content = "second", isFavorite = false)
        )

        repos.associations.updateFavorite(second, isFavorite = true)

        val favorites = repos.associations.getFavoritesByCardId(cardId)
        assertEquals(1, favorites.size, "a card must never have two favorite associations")
        assertEquals(second, favorites.single().id)
        assertEquals(
            false,
            repos.associations.getAssociationsByCardId(cardId).first()
                .single { it.id == first }.isFavorite,
        )
    }

    // ── Review logs ───────────────────────────────────────────────────────────

    @Test
    fun reviewLog_insertAndRead_roundTripsAndOrdersNewestFirst() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)
        val cardId = repos.cards.insertCard(card(deckId))

        repos.reviewLogs.insertReviewLog(
            reviewLog(cardId, rating = Rating.Good, at = 1_000L)
        )
        repos.reviewLogs.insertReviewLog(
            reviewLog(cardId, rating = Rating.Again, at = 3_000L)
        )
        repos.reviewLogs.insertReviewLog(
            reviewLog(cardId, rating = Rating.Easy, at = 2_000L)
        )

        val logs = repos.reviewLogs.getLogsByCardId(cardId)
        assertEquals(listOf(Rating.Again, Rating.Easy, Rating.Good), logs.map { it.rating })
        assertEquals(3, logs.first().scheduledDays)
        assertNotNull(logs.first().reviewedAt)
    }

    @Test
    fun reviewLog_paging_returnsAWindow() = runTest {
        val repos = Repos()
        val deckId = repos.decks.insertDeck("German A1", "de", DeckType.Linguistic.name)
        val cardId = repos.cards.insertCard(card(deckId))
        repeat(5) { i ->
            repos.reviewLogs.insertReviewLog(reviewLog(cardId, at = (i + 1) * 1_000L))
        }

        val page = repos.reviewLogs.getLogsByCardIdPaged(cardId, limit = 2, offset = 1)
        assertEquals(2, page.size)
        assertEquals(4_000L, page.first().reviewedAt.toEpochMilliseconds())
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private fun tip(cardId: Long, content: String = "Verb second position.", order: Int = 0) =
        GrammarTip(
            id = 0L,
            cardId = cardId,
            content = content,
            order = order,
            source = GrammarTipSource.Bundled,
            createdAt = Clock.System.now(),
        )

    private fun link(cardId: Long, wordCardId: Long?) = SentenceWordLink(
        id = 0L,
        sentenceCardId = cardId,
        wordCardId = wordCardId,
        positionInSentence = 0,
        surfaceForm = "Ich",
        resolution = if (wordCardId == null) LinkResolution.Unresolved else LinkResolution.Resolved,
    )

    private fun association(
        cardId: Long,
        content: String = "Skojarzenie",
        isFavorite: Boolean = false,
    ) = Association(
        id = 0L,
        cardId = cardId,
        content = content,
        type = AssociationType.Generated,
        isFavorite = isFavorite,
        createdAt = Instant.fromEpochMilliseconds(1_780_000_000_000),
    )

    private fun reviewLog(
        cardId: Long,
        rating: Rating = Rating.Good,
        at: Long = 1_000L,
    ) = ReviewLog(
        id = 0L,
        cardId = cardId,
        rating = rating,
        reviewedAt = Instant.fromEpochMilliseconds(at),
        scheduledDays = 3,
        elapsedDays = 2,
        stability = 11.5f,
        difficulty = 4.5f,
    )
}
