package io.github.woxakv.koreadercompanion.data.local.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StorageAccessLocalDataSource"

@Singleton
class StorageAccessLocalDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) {
    private fun keyFor(target: StorageTarget) = stringPreferencesKey("granted_tree_uri_${target.name}")

    suspend fun grantedTreeUriString(target: StorageTarget): String? =
        dataStore.data.first()[keyFor(target)]

    suspend fun persistGrant(target: StorageTarget, treeUriString: String) {
        val uri = Uri.parse(treeUriString)
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        dataStore.edit { it[keyFor(target)] = treeUriString }
    }

    fun resolveGrantedRoot(treeUriString: String): DocumentFile? =
        DocumentFile.fromTreeUri(context, Uri.parse(treeUriString))

    suspend fun clearGrant(target: StorageTarget) {
        val treeUriString = grantedTreeUriString(target)
        dataStore.edit { it.remove(keyFor(target)) }
        if (treeUriString != null) {
            // Best-effort: the permission may already be gone (e.g. the user
            // revoked it from Android's own settings), which throws - that's
            // not a reason to fail the revoke, since the datastore key above
            // is already cleared either way.
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(treeUriString),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }.onFailure { error -> Log.w(TAG, "Failed to release URI permission for $target", error) }
        }
    }
}
