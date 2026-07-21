package com.mila.langualinker.data.repository

import com.mila.langualinker.domain.model.ReviewLog

interface ReviewLogRepository {
    suspend fun insertReviewLog(log: ReviewLog)
    suspend fun getLogsByCardId(cardId: Long): List<ReviewLog>
    suspend fun deleteLogsByCardId(cardId: Long)
}
