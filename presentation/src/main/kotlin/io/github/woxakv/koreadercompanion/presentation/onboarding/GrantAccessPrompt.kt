package io.github.woxakv.koreadercompanion.presentation.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The koreader-folder re-grant prompt, shown inline on the Currently Reading
 * screen when that (required) access is missing or lost. The Books folder
 * (cover art, optional) is only ever asked for during onboarding - losing
 * it just means covers stop showing, not a PermissionRequired state.
 */
@Composable
fun GrantAccessPrompt(
    reason: String,
    onTreeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            "Choose your koreader folder so KOReader Companion can read your reading stats.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(reason, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(16.dp))
        FolderPickerButton(
            label = "Choose koreader Folder",
            onTreeSelected = onTreeSelected,
            initialUri = KOREADER_FOLDER_HINT_URI,
        )
    }
}
