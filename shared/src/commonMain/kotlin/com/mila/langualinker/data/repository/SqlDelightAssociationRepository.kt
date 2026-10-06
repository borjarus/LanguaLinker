package com.mila.langualinker.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.mila.langualinker.association.Association
import com.mila.langualinker.database.AppDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SqlDelightAssociationRepository(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : AssociationRepository {

    private val queries get() = database.associationsQueries

    override fun getAssociationsByCardId(cardId: Long): Flow<List<Association>> =
        queries.getAssociationsByCardId(cardId).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun getFavoritesByCardId(cardId: Long): List<Association> =
        withContext(dispatcher) {
            queries.getFavoritesByCardId(cardId).executeAsList().map { it.toDomain() }
        }

    override suspend fun insertAssociation(association: Association): Long =
        withContext(dispatcher) {
            queries.transactionWithResult {
                queries.insertAssociation(
                    cardId = association.cardId,
                    content = association.content,
                    type = association.type.name,
                    isFavorite = if (association.isFavorite) 1L else 0L,
                    createdAt = association.createdAt.toEpochMilliseconds(),
                )
                queries.lastInsertRowId().executeAsOne()
            }
        }

    /**
     * Marking an association favorite clears the previous favorite for the same card, so the
     * "one favorite per card" rule holds even without the partial unique index that arrives
     * with the Phase 3.5 schema retrofit.
     */
    override suspend fun updateFavorite(id: Long, isFavorite: Boolean) = withContext(dispatcher) {
        queries.transaction {
            if (isFavorite) {
                val cardId = queries.getAssociationById(id).executeAsOneOrNull()?.card_id
                if (cardId != null) {
                    queries.getFavoritesByCardId(cardId).executeAsList()
                        .filter { it.id != id }
                        .forEach { queries.updateFavorite(isFavorite = 0L, id = it.id) }
                }
            }
            queries.updateFavorite(isFavorite = if (isFavorite) 1L else 0L, id = id)
        }
    }

    override suspend fun deleteAssociation(id: Long) {
        withContext(dispatcher) { queries.deleteAssociation(id) }
    }

    override suspend fun deleteAssociationsByCardId(cardId: Long) {
        withContext(dispatcher) { queries.deleteAssociationsByCardId(cardId) }
    }
}
