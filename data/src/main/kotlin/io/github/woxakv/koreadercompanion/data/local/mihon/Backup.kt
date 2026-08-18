@file:OptIn(ExperimentalSerializationApi::class)

package io.github.woxakv.koreadercompanion.data.local.mihon

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * Minimal subset of Mihon's `.tachibk` backup schema - a gzip-compressed
 * Protobuf payload. Field numbers below were confirmed against Mihon's own
 * open-source backup models (github.com/mihonapp/mihon) and a real backup
 * file pulled from a test device during planning.
 *
 * Only the fields this app actually uses are declared - the real Mihon
 * classes have many more (author, description, genre, scanlator,
 * dateFetch, dateUpload, ...). Protobuf decoding via kotlinx-serialization
 * skips unknown fields automatically, so omitting them is correct and
 * deliberate, not an oversight.
 */
@Serializable
data class Backup(
    @ProtoNumber(1) val backupManga: List<BackupManga> = emptyList(),
)

@Serializable
data class BackupManga(
    @ProtoNumber(1) val source: Long,
    @ProtoNumber(2) val url: String,
    @ProtoNumber(3) val title: String,
    @ProtoNumber(9) val thumbnailUrl: String? = null,
    @ProtoNumber(100) val favorite: Boolean = true,
    @ProtoNumber(106) val lastModifiedAt: Long = 0,
    @ProtoNumber(16) val chapters: List<BackupChapter> = emptyList(),
    @ProtoNumber(104) val history: List<BackupHistory> = emptyList(),
)

@Serializable
data class BackupChapter(
    @ProtoNumber(1) val url: String,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(4) val read: Boolean = false,
    @ProtoNumber(6) val lastPageRead: Long = 0,
    @ProtoNumber(9) val chapterNumber: Float = 0F,
)

@Serializable
data class BackupHistory(
    // Matches a BackupChapter.url.
    @ProtoNumber(1) val url: String,
    // Epoch millis.
    @ProtoNumber(2) val lastRead: Long,
    // Millis actually spent reading.
    @ProtoNumber(3) val readDuration: Long = 0,
)
