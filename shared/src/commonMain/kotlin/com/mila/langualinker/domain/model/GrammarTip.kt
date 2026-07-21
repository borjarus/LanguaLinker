package com.mila.langualinker.domain.model

import kotlin.time.Instant

data class GrammarTip(
    val id: Long,
    val cardId: Long,
    val content: String,
    val order: Int,
    val source: GrammarTipSource,
    val createdAt: Instant,
)

enum class GrammarTipSource { Bundled, Generated, UserAdded }
