package io.github.woxakv.koreadercompanion.presentation.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget
import io.github.woxakv.koreadercompanion.presentation.navigation.StorageGrantUiState
import io.github.woxakv.koreadercompanion.presentation.onboarding.KOREADER_FOLDER_HINT_URI
import io.github.woxakv.koreadercompanion.presentation.onboarding.MIHON_FOLDER_HINT_URI
import io.github.woxakv.koreadercompanion.presentation.onboarding.STORAGE_ROOT_HINT_URI
import io.github.woxakv.koreadercompanion.presentation.onboarding.StorageGrantSection

@Composable
fun ConfigScreen(
    state: StorageGrantUiState,
    onTreeSelected: (StorageTarget, String) -> Unit,
    onRevoke: (StorageTarget) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Storage access", style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(24.dp))
        StorageGrantSection(
            index = 1,
            target = StorageTarget.KOREADER,
            name = "koreader",
            qualifierSuffix = null,
            granted = state.grants[StorageTarget.KOREADER] == true,
            hintUri = KOREADER_FOLDER_HINT_URI,
            onTreeSelected = onTreeSelected,
            onRevoke = onRevoke,
        )

        Spacer(Modifier.height(24.dp))
        StorageGrantSection(
            index = 2,
            target = StorageTarget.BOOKS,
            name = "Books",
            qualifierSuffix = null,
            granted = state.grants[StorageTarget.BOOKS] == true,
            hintUri = STORAGE_ROOT_HINT_URI,
            onTreeSelected = onTreeSelected,
            onRevoke = onRevoke,
        )

        Spacer(Modifier.height(24.dp))
        StorageGrantSection(
            index = 3,
            target = StorageTarget.MIHON,
            name = "Mihon",
            qualifierSuffix = null,
            granted = state.grants[StorageTarget.MIHON] == true,
            hintUri = MIHON_FOLDER_HINT_URI,
            onTreeSelected = onTreeSelected,
            onRevoke = onRevoke,
        )
        if (state.grants[StorageTarget.MIHON] == true && state.mihonGrantNarrow) {
            Spacer(Modifier.height(8.dp))
            Text("Re-grant the Mihon folder to also show manga cover art.", style = MaterialTheme.typography.bodyMedium)
        }

        state.error?.let { error ->
            Spacer(Modifier.height(16.dp))
            Text(error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
