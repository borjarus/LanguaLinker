package com.mila.langualinker.association

import kotlin.time.Instant

data class Association(
    val id: Long,
    val cardId: Long,
    val content: String,
    val type: AssociationType,
    val isFavorite: Boolean,
    val createdAt: Instant,
)

enum class AssociationType {
    Generated,
    UserSaved,
}
