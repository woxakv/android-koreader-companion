package io.github.woxakv.koreadercompanion.presentation.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget
import io.github.woxakv.koreadercompanion.presentation.navigation.StorageGrantUiState

@Composable
fun OnboardingScreen(
    state: StorageGrantUiState,
    onTreeSelected: (StorageTarget, String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val koreaderAccessGranted = state.grants[StorageTarget.KOREADER] == true
    val booksAccessGranted = state.grants[StorageTarget.BOOKS] == true

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Welcome", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            "KOReader Companion reads two folders on your device: the koreader folder for " +
                "your reading stats, and your Books folder for cover art. Android won't let " +
                "us grant both at once, so pick them separately below.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(24.dp))
        StorageGrantSection(
            index = 1,
            target = StorageTarget.KOREADER,
            name = "koreader",
            qualifierSuffix = "(required)",
            granted = koreaderAccessGranted,
            hintUri = KOREADER_FOLDER_HINT_URI,
            onTreeSelected = onTreeSelected,
        )

        Spacer(Modifier.height(24.dp))
        StorageGrantSection(
            index = 2,
            target = StorageTarget.BOOKS,
            name = "Books",
            qualifierSuffix = "(optional, for cover art)",
            granted = booksAccessGranted,
            hintUri = STORAGE_ROOT_HINT_URI,
            onTreeSelected = onTreeSelected,
        )

        Spacer(Modifier.height(24.dp))
        StorageGrantSection(
            index = 3,
            target = StorageTarget.MIHON,
            name = "Mihon",
            qualifierSuffix = "(optional, for manga stats)",
            granted = state.grants[StorageTarget.MIHON] == true,
            hintUri = MIHON_FOLDER_HINT_URI,
            onTreeSelected = onTreeSelected,
        )

        state.error?.let { error ->
            Spacer(Modifier.height(16.dp))
            Text(error, style = MaterialTheme.typography.bodyMedium)
        }

        if (koreaderAccessGranted) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onContinue) {
                Text(if (booksAccessGranted) "Continue" else "Continue without cover art")
            }
        }
    }
}
