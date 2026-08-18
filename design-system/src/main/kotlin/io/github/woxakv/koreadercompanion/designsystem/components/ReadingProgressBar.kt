package io.github.woxakv.koreadercompanion.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.woxakv.koreadercompanion.designsystem.theme.EinkColors

@Composable
fun ReadingProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp),
        color = EinkColors.ProgressFill,
        trackColor = EinkColors.ProgressTrack,
        strokeCap = ProgressIndicatorDefaults.LinearStrokeCap,
    )
}
