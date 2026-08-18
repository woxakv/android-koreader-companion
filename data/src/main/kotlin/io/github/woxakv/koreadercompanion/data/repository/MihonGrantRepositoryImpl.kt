package io.github.woxakv.koreadercompanion.data.repository

import io.github.woxakv.koreadercompanion.data.local.mihon.MihonBackupDataSource
import io.github.woxakv.koreadercompanion.domain.repository.MihonGrantRepository
import io.github.woxakv.koreadercompanion.domain.repository.StorageAccessRepository
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget
import javax.inject.Inject

class MihonGrantRepositoryImpl @Inject constructor(
    private val storageAccessRepository: StorageAccessRepository,
    private val mihonBackupDataSource: MihonBackupDataSource,
) : MihonGrantRepository {

    override suspend fun isGrantNarrow(): Boolean {
        val treeUriString = storageAccessRepository.grantedTreeUriString(StorageTarget.MIHON) ?: return false
        return mihonBackupDataSource.isNarrowGrant(treeUriString)
    }
}
