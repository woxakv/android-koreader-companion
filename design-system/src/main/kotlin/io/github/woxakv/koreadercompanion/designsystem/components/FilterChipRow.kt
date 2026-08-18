package io.github.woxakv.koreadercompanion.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import io.github.woxakv.koreadercompanion.designsystem.theme.EinkColors

/**
 * Generic left-aligned filter chip row with a bottom rule (tab-strip
 * convention), each option its own boxed chip; the selected chip inverts
 * to black background / white text rather than using color, per
 * `EinkTheme`'s strict black/white, ripple-disabled convention.
 */
@Composable
fun <T> FilterChipRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .drawBehind {
                val strokeWidth = 1.dp.toPx()
                drawLine(
                    color = EinkColors.Outline,
                    start = Offset(0f, size.height - strokeWidth / 2),
                    end = Offset(size.width, size.height - strokeWidth / 2),
                    strokeWidth = strokeWidth,
                )
            }
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        options.forEachIndexed { index, (value, label) ->
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .border(1.dp, EinkColors.OnBackground)
                    .background(if (isSelected) EinkColors.OnBackground else EinkColors.Background)
                    .clickable { onSelected(value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) EinkColors.Background else EinkColors.OnBackground,
                )
            }
            if (index != options.lastIndex) {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}
