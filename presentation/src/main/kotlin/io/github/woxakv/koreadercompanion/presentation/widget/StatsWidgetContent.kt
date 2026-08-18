package io.github.woxakv.koreadercompanion.presentation.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import io.github.woxakv.koreadercompanion.domain.model.ReadingStatsSummary

// Strict black on white regardless of system theme, matching the e-ink
// default: no day/night variation to keep.
private val WidgetTextColor = ColorProvider(day = Color.Black, night = Color.Black)

// Used when there is no reading history yet, so every requested StatItem
// still renders with a "0" value rather than the row being omitted.
private val zeroSummary = ReadingStatsSummary(
    avgMinutesLast7Days = 0,
    streakDays = 0,
    minutesToday = 0,
    pagesToday = 0,
    minutesThisMonth = 0,
    pagesThisMonth = 0,
    minutesThisWeek = 0,
    pagesThisWeek = 0,
)

/**
 * Pure Glance content: no reference to MainActivity/KOReader package
 * lookups or any other Android component construction, so the tap action is
 * passed in rather than built here (only the app module has that context).
 */
@Composable
fun StatsWidgetContent(
    summary: ReadingStatsSummary?,
    rows: List<List<StatItem>> = DEFAULT_STAT_ROWS,
    onClick: Action,
    modifier: GlanceModifier = GlanceModifier.fillMaxSize(),
) {
    val resolvedSummary = summary ?: zeroSummary

    Column(
        // No background of our own is drawn here anymore - the launcher/
        // system background shows through instead, since `modifier` always
        // fills the allotted bounds regardless of background color (the
        // gap/border artifact the old white background worked around was
        // caused by not fully filling the bounds, not by the background
        // color itself). This has not yet been visually confirmed on-device;
        // if a border reappears around the widget, revisit this.
        modifier = modifier
            .padding(8.dp)
            .clickable(onClick),
        // Centers the two rows within whatever height the widget is given
        // rather than top-aligning them - otherwise a box taller than the
        // content strictly needs (e.g. the standalone widget's minHeight,
        // sized generously for the longest possible values) leaves visible
        // dead space below instead of looking intentional. Same fix as
        // CurrentlyReadingWidgetContent.kt's root Row from plan 009.
        verticalAlignment = Alignment.CenterVertically,
    ) {
        rows.forEachIndexed { rowIndex, row ->
            if (row.size <= 2) {
                // This Month/This Week: a single centered line combining
                // both stats ("This Month: 4h 36m · 352 pages   This Week:
                // 32m · 57 pages") rather than one line per stat - keeps
                // the whole row to the same one-line height as row 1's
                // label, instead of doubling the row's height. Only the
                // value half is bold (matches row 1's label/value weight
                // split) - Glance's Text has no per-span styling, so this
                // needs separate label/value Text elements side by side
                // rather than one Text over a single formatted string,
                // wrapped in a Box to center the whole line as a group.
                Box(modifier = GlanceModifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row {
                        row.forEachIndexed { itemIndex, item ->
                            Text(
                                text = "${item.label()}: ",
                                style = TextStyle(color = WidgetTextColor, fontSize = 13.sp),
                            )
                            Text(
                                text = item.value(resolvedSummary),
                                style = TextStyle(color = WidgetTextColor, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                            )
                            if (itemIndex != row.lastIndex) {
                                Text(text = "   ", style = TextStyle(fontSize = 13.sp))
                            }
                        }
                    }
                }
            } else {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    row.forEach { item ->
                        Column(
                            modifier = GlanceModifier.defaultWeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = item.label(),
                                style = TextStyle(color = WidgetTextColor, fontSize = 12.sp, textAlign = TextAlign.Center),
                            )
                            Text(
                                text = item.value(resolvedSummary),
                                style = TextStyle(
                                    color = WidgetTextColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    textAlign = TextAlign.Center,
                                ),
                            )
                        }
                    }
                }
            }
            if (rowIndex != rows.lastIndex) {
                Spacer(GlanceModifier.height(6.dp))
            }
        }
    }
}
