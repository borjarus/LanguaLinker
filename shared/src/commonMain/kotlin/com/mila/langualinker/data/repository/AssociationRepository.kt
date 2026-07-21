package com.mila.langualinker.data.repository

import com.mila.langualinker.association.Association
import kotlinx.coroutines.flow.Flow

interface AssociationRepository {
    fun getAssociationsByCardId(cardId: Long): Flow<List<Association>>
    suspend fun getFavoritesByCardId(cardId: Long): List<Association>
    suspend fun insertAssociation(association: Association): Long
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)
    suspend fun deleteAssociation(id: Long)
    suspend fun deleteAssociationsByCardId(cardId: Long)
}
