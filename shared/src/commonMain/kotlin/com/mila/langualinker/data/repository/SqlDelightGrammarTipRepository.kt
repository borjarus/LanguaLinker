package com.mila.langualinker.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mila.langualinker.database.AppDatabase
import com.mila.langualinker.domain.model.GrammarTip
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightGrammarTipRepository(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : GrammarTipRepository {

    private val queries get() = database.grammar_tipsQueries

    override fun getTipsByCardId(cardId: Long): Flow<List<GrammarTip>> =
        queries.getTipsByCardId(cardId).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun insertTip(tip: GrammarTip): Long = withContext(dispatcher) {
        queries.transactionWithResult { insertTipInTransaction(tip) }
    }

    internal fun insertTipInTransaction(tip: GrammarTip): Long {
        queries.insertTip(
            cardId = tip.cardId,
            content = tip.content,
            sortOrder = tip.order.toLong(),
            source = tip.source.name,
            createdAt = tip.createdAt.toEpochMilliseconds(),
        )
        return queries.lastInsertRowId().executeAsOne()
    }

    override suspend fun updateTipOrder(id: Long, order: Int) {
        withContext(dispatcher) { queries.updateTipOrder(sortOrder = order.toLong(), id = id) }
    }

    /**
     * Applies a whole new ordering in one transaction, so a crash mid-drag cannot leave two
     * tips sharing an order value.
     */
    suspend fun reorderTips(tipIdsInOrder: List<Long>) {
        withContext(dispatcher) {
            queries.transaction {
                tipIdsInOrder.forEachIndexed { index, tipId ->
                    queries.updateTipOrder(sortOrder = index.toLong(), id = tipId)
                }
            }
        }
    }

    override suspend fun updateTipContent(id: Long, content: String) {
        withContext(dispatcher) { queries.updateTipContent(content = content, id = id) }
    }

    override suspend fun deleteTip(id: Long) {
        withContext(dispatcher) { queries.deleteTip(id) }
    }

    override suspend fun deleteTipsByCardId(cardId: Long) {
        withContext(dispatcher) { queries.deleteTipsByCardId(cardId) }
    }
}
