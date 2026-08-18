package io.github.woxakv.koreadercompanion.domain.model

data class Book(
    val title: String,
    val author: String?,
    val filePath: String?,
    val totalPages: Int,
)
