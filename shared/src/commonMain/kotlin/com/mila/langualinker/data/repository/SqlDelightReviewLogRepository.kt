package com.mila.langualinker.data.repository

import com.mila.langualinker.database.AppDatabase
import com.mila.langualinker.domain.model.ReviewLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqlDelightReviewLogRepository(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ReviewLogRepository {

    private val queries get() = database.review_logsQueries

    override suspend fun insertReviewLog(log: ReviewLog) = withContext(dispatcher) {
        insertReviewLogInTransaction(log)
    }

    /**
     * For callers that already hold a transaction — a rating must persist the card state and
     * its review log atomically, or the log stops being a trustworthy source for undo and for
     * the FSRS optimiser.
     */
    internal fun insertReviewLogInTransaction(log: ReviewLog) {
        queries.insertReviewLog(
            cardId = log.cardId,
            rating = log.rating.name,
            reviewedAt = log.reviewedAt.toEpochMilliseconds(),
            scheduledDays = log.scheduledDays.toLong(),
            elapsedDays = log.elapsedDays.toLong(),
            stability = log.stability.toDouble(),
            difficulty = log.difficulty.toDouble(),
        )
    }

    override suspend fun getLogsByCardId(cardId: Long): List<ReviewLog> = withContext(dispatcher) {
        queries.getLogsByCardId(cardId).executeAsList().map { it.toDomain() }
    }

    suspend fun getLogsByCardIdPaged(cardId: Long, limit: Int, offset: Int): List<ReviewLog> =
        withContext(dispatcher) {
            queries.getLogsByCardIdPaged(cardId, limit.toLong(), offset.toLong())
                .executeAsList()
                .map { it.toDomain() }
        }

    override suspend fun deleteLogsByCardId(cardId: Long) {
        withContext(dispatcher) { queries.deleteLogsByCardId(cardId) }
    }
}
