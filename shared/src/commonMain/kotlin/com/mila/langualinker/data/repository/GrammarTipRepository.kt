package com.mila.langualinker.data.repository

import com.mila.langualinker.domain.model.GrammarTip
import kotlinx.coroutines.flow.Flow

interface GrammarTipRepository {
    fun getTipsByCardId(cardId: Long): Flow<List<GrammarTip>>
    suspend fun insertTip(tip: GrammarTip): Long
    suspend fun updateTipOrder(id: Long, order: Int)
    suspend fun updateTipContent(id: Long, content: String)
    suspend fun deleteTip(id: Long)
    suspend fun deleteTipsByCardId(cardId: Long)
}
