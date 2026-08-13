package de.anisentinel.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CompassCalibration
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import de.anisentinel.app.R

private data class DrawerItem(
    @StringRes val label: Int,
    val icon: ImageVector,
    val route: String? = null
)

@Composable
fun AppDrawer(
    selectedRoute: String,
    onMainDestination: (String) -> Unit,
    onSecondaryDestination: (String) -> Unit,
    onAbout: () -> Unit,
    onClose: () -> Unit
) {
    val mainItems = listOf(
        DrawerItem(R.string.nav_start, Icons.Outlined.Home, "home"),
        DrawerItem(R.string.nav_calendar, Icons.Outlined.CalendarMonth, "calendar"),
        DrawerItem(R.string.nav_favorites, Icons.Outlined.FavoriteBorder, "favorites"),
        DrawerItem(R.string.nav_discover, Icons.Outlined.CompassCalibration, "discover")
    )
    val secondaryItems = listOf(
        DrawerItem(R.string.drawer_providers, Icons.Outlined.SmartDisplay, "providers"),
        DrawerItem(R.string.drawer_season, Icons.Outlined.Today, "season"),
        DrawerItem(R.string.drawer_dubs, Icons.Outlined.NewReleases, "dubs"),
        DrawerItem(R.string.anticipated_titles, Icons.Outlined.LocalFireDepartment),
        DrawerItem(R.string.drawer_news, Icons.Outlined.MenuBook, "news"),
        DrawerItem(R.string.postponements_title, Icons.Outlined.EventBusy, "postponements"),
        DrawerItem(R.string.drawer_stats, Icons.Outlined.ShowChart, "statistics"),
        DrawerItem(R.string.drawer_changelog, Icons.Outlined.NewReleases, "changelog")
    )

    ModalDrawerSheet(
        modifier = Modifier.fillMaxHeight().widthIn(max = 360.dp).testTag(UiTags.DRAWER)
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            IconButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close_menu))
            }
            Image(
                painter = painterResource(R.drawable.ic_launcher_art),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 88.dp, vertical = 4.dp)
            )
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.slogan),
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.padding(top = 8.dp))
            mainItems.forEach { item ->
                DrawerNavigationItem(item, selectedRoute == item.route) {
                    item.route?.let(onMainDestination)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(
                stringResource(R.string.drawer_secondary),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(12.dp)
            )
            secondaryItems.forEach { item ->
                val enabled = item.route != null
                DrawerNavigationItem(item, selectedRoute == item.route, enabled = enabled) {
                    item.route?.let(onSecondaryDestination)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            DrawerNavigationItem(
                DrawerItem(R.string.nav_settings, Icons.Outlined.Settings, "settings"),
                selectedRoute == "settings"
            ) { onMainDestination("settings") }
            DrawerNavigationItem(
                DrawerItem(R.string.drawer_about, Icons.Outlined.Info, "about"),
                selectedRoute == "about",
                onClick = onAbout
            )
            Spacer(Modifier.padding(bottom = 12.dp))
        }
    }
}

@Composable
private fun DrawerNavigationItem(
    item: DrawerItem,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        label = {
            Text(
                if (enabled) {
                    stringResource(item.label)
                } else {
                    "${stringResource(item.label)} · ${stringResource(R.string.coming_soon)}"
                }
            )
        },
        selected = selected,
        onClick = if (enabled) onClick else ({}),
        icon = { Icon(item.icon, contentDescription = null) },
        modifier = Modifier
            .padding(NavigationDrawerItemDefaults.ItemPadding)
            .alpha(if (enabled) 1f else .45f)
            .then(if (enabled) Modifier else Modifier.semantics { disabled() })
    )
}
