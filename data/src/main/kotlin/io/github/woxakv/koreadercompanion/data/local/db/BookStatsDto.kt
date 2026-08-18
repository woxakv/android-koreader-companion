package io.github.woxakv.koreadercompanion.data.local.db

import io.github.woxakv.koreadercompanion.domain.model.ReadingProgress

data class BookStatsDto(
    val id: Long,
    val title: String,
    val authors: String?,
    val lastOpen: Long,
    val pages: Int,
    val totalReadTime: Long,
    val totalReadPages: Int,
)

fun BookStatsDto.toReadingProgress(maxPageRead: Int): ReadingProgress = ReadingProgress(
    currentPage = maxPageRead,
    totalPages = pages,
    totalReadTimeSeconds = totalReadTime,
    totalReadPages = totalReadPages,
)
