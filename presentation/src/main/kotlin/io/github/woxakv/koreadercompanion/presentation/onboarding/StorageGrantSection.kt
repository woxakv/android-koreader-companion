package io.github.woxakv.koreadercompanion.presentation.onboarding

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.woxakv.koreadercompanion.domain.repository.StorageTarget

/**
 * One "N. {name} folder {qualifierSuffix}" section, shared by OnboardingScreen and
 * ConfigScreen (deliberate near-duplicate screens by design - see plan 015's Context
 * & decisions). The picker/re-grant button is *always* shown, relabeled
 * "Change {name} Folder" once granted rather than hidden once granted - Config's fix
 * for this (a live-tuning correction from plan 012) is now the shared behavior for
 * both screens; don't regress back to hiding the button once granted.
 *
 * [qualifierSuffix] lets each screen keep its own heading convention: Onboarding
 * appends "(required)" / "(optional, ...)"; Config appends nothing (a deliberate
 * choice from an earlier plan - those qualifiers "read oddly outside the onboarding
 * framing").
 */
@Composable
fun StorageGrantSection(
    index: Int,
    target: StorageTarget,
    name: String,
    qualifierSuffix: String?,
    granted: Boolean,
    hintUri: Uri?,
    onTreeSelected: (StorageTarget, String) -> Unit,
    modifier: Modifier = Modifier,
    // Null on Onboarding - there's nothing granted yet to remove there.
    // Config passes a real handler so each grant can be revoked independently.
    onRevoke: ((StorageTarget) -> Unit)? = null,
) {
    Column(modifier = modifier) {
        val heading = if (qualifierSuffix != null) {
            "$index. $name folder $qualifierSuffix"
        } else {
            "$index. $name folder"
        }
        Text(heading, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (granted) {
            Text("Granted", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FolderPickerButton(
                label = if (granted) "Change $name Folder" else "Choose $name Folder",
                onTreeSelected = { treeUriString -> onTreeSelected(target, treeUriString) },
                initialUri = hintUri,
            )
            if (granted && onRevoke != null) {
                Spacer(Modifier.width(16.dp))
                Text(
                    "Remove",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { onRevoke(target) },
                )
            }
        }
    }
}
