package io.github.woxakv.koreadercompanion.presentation.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun FolderPickerButton(
    label: String,
    onTreeSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialUri: Uri? = null,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onTreeSelected(uri.toString())
    }

    Button(onClick = { launcher.launch(initialUri) }, modifier = modifier) {
        Text(label)
    }
}
