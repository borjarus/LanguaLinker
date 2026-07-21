package com.mila.langualinker.data.repository

import com.mila.langualinker.domain.model.SentenceWordLink

interface SentenceWordLinkRepository {
    suspend fun getLinksBySentenceCardId(sentenceCardId: Long): List<SentenceWordLink>
    suspend fun insertLink(link: SentenceWordLink)
    suspend fun deleteLink(id: Long)
    suspend fun deleteLinksBySentenceCardId(sentenceCardId: Long)
}
