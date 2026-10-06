package com.mila.langualinker.data.repository

import com.mila.langualinker.database.AppDatabase
import com.mila.langualinker.domain.model.LinkResolution
import com.mila.langualinker.domain.model.SentenceWordLink
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqlDelightSentenceWordLinkRepository(
    private val database: AppDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : SentenceWordLinkRepository {

    private val queries get() = database.sentence_word_linksQueries

    override suspend fun getLinksBySentenceCardId(sentenceCardId: Long): List<SentenceWordLink> =
        withContext(dispatcher) {
            queries.getLinksBySentenceCardId(sentenceCardId).executeAsList().map { it.toDomain() }
        }

    override suspend fun insertLink(link: SentenceWordLink) {
        withContext(dispatcher) { insertLinkInTransaction(link) }
    }

    internal fun insertLinkInTransaction(link: SentenceWordLink) {
        queries.insertLink(
            sentenceCardId = link.sentenceCardId,
            wordCardId = link.wordCardId,
            positionInSentence = link.positionInSentence.toLong(),
            surfaceForm = link.surfaceForm,
            resolution = link.resolution.name,
        )
    }

    suspend fun updateResolution(id: Long, wordCardId: Long?, resolution: LinkResolution) {
        withContext(dispatcher) {
            queries.updateLinkResolution(
                wordCardId = wordCardId,
                resolution = resolution.name,
                id = id,
            )
        }
    }

    override suspend fun deleteLink(id: Long) {
        withContext(dispatcher) { queries.deleteLink(id) }
    }

    override suspend fun deleteLinksBySentenceCardId(sentenceCardId: Long) {
        withContext(dispatcher) { queries.deleteLinksBySentenceCardId(sentenceCardId) }
    }
}
