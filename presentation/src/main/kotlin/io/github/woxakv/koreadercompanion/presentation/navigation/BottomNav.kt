package io.github.woxakv.koreadercompanion.presentation.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Same 5-shade grayscale palette as
// presentation/.../widget/CalendarGridGraphRenderer.kt's BUCKET_FILL_COLORS
// (index 0 = lightest .. 4 = darkest), reused here for the nav icons.
private val ICON_SELECTED_COLOR = Color(0xFF000000)
private val ICON_UNSELECTED_COLOR = Color(0xFFA0A0A0)

/**
 * A single bottom-nav destination: a route to navigate to, a label shown
 * under the icon, and the Material icon to render for it.
 */
data class NavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val navItems: List<NavItem> = listOf(
    NavItem(
        route = AppDestinations.CURRENTLY_READING,
        label = "Home",
        icon = Icons.Default.Home,
    ),
    NavItem(
        route = AppDestinations.STATS,
        label = "Stats",
        icon = Icons.Default.BarChart,
    ),
    NavItem(
        route = AppDestinations.CONFIG,
        label = "Config",
        icon = Icons.Default.Settings,
    ),
)

/**
 * Bottom navigation bar: [items] laid out horizontally and distributed
 * evenly.
 */
@Composable
fun BottomNav(
    items: List<NavItem>,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        items.forEach { item ->
            BottomNavItem(
                item = item,
                selected = currentRoute == item.route,
                onClick = { onNavigate(item.route) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    item: NavItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .wrapContentHeight()
            .padding(8.dp),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = item.label,
            tint = if (selected) ICON_SELECTED_COLOR else ICON_UNSELECTED_COLOR,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            ),
        )
    }
}
