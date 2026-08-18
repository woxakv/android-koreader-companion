package io.github.woxakv.koreadercompanion.domain.repository

import io.github.woxakv.koreadercompanion.core.result.Try
import io.github.woxakv.koreadercompanion.domain.model.CurrentBook

interface CurrentBookRepository {
    suspend fun getCurrentBook(): Try<CurrentBook>
}
