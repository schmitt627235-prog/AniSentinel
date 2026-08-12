package de.anisentinel.app.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CompassCalibration
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.testTag
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.anisentinel.app.R
import kotlinx.coroutines.launch

private enum class MainDestination(
    val route: String,
    @StringRes val label: Int,
    val icon: ImageVector
) {
    HOME("home", R.string.nav_start, Icons.Outlined.Home),
    CALENDAR("calendar", R.string.nav_calendar, Icons.Outlined.CalendarMonth),
    FAVORITES("favorites", R.string.nav_favorites, Icons.Outlined.FavoriteBorder),
    DISCOVER("discover", R.string.nav_discover, Icons.Outlined.CompassCalibration),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings)
}

@Composable
fun AniSentinelApp(notificationTarget: android.net.Uri? = null, onNotificationTargetConsumed: () -> Unit = {}) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selected = MainDestination.entries.firstOrNull {
        it.route == backStackEntry?.destination?.route
    }

    fun navigate(destination: MainDestination) {
        navController.navigate(destination.route) {
            popUpTo(MainDestination.HOME.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                selectedRoute = backStackEntry?.destination?.route.orEmpty(),
                onMainDestination = { route ->
                    navigate(MainDestination.entries.first { it.route == route })
                    scope.launch { drawerState.close() }
                },
                onSecondaryDestination = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                    scope.launch { drawerState.close() }
                },
                onAbout = {
                    navController.navigate("about") { launchSingleTop = true }
                    scope.launch { drawerState.close() }
                },
                onClose = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 720.dp
            val bottomLabelSize = when {
                maxWidth < 360.dp -> 6.sp
                maxWidth < 420.dp -> 9.sp
                else -> 10.sp
            }
            Row(Modifier.fillMaxSize()) {
                if (useRail) {
                    MainNavigationRail(selected = selected, onSelect = ::navigate)
                }
                Scaffold(
                    bottomBar = {
                        if (!useRail) {
                            NavigationBar {
                                MainDestination.entries.forEach { destination ->
                                    NavigationBarItem(
                                        modifier = Modifier.testTag(
                                            UiTags.NAV_PREFIX + destination.route
                                        ),
                                        selected = selected == destination,
                                        onClick = { navigate(destination) },
                                        icon = { Icon(destination.icon, contentDescription = null) },
                                        label = {
                                            Text(
                                                text = androidx.compose.ui.res.stringResource(destination.label),
                                                maxLines = 1,
                                                softWrap = false,
                                                fontSize = bottomLabelSize
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                ) { padding ->
                    androidx.compose.runtime.LaunchedEffect(notificationTarget) {
                        val parts = notificationTarget?.pathSegments.orEmpty()
                        if (notificationTarget?.scheme == "anisentinel" && notificationTarget.host == "release" && parts.size >= 4) {
                            navController.navigate("release/${android.net.Uri.encode(parts[0])}/${parts[1]}/${parts[2]}/${parts[3]}") {
                                launchSingleTop = true
                            }
                            onNotificationTargetConsumed()
                        }
                    }
                    NavHost(
                        navController = navController,
                        startDestination = MainDestination.HOME.route,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(MainDestination.HOME.route) {
                            HomeRoute(
                                padding,
                                onMenu = { scope.launch { drawerState.open() } },
                                onAnimeClick = { navController.navigate("details/$it") }
                            )
                        }
                        composable(MainDestination.CALENDAR.route) {
                            CalendarScreen(
                                padding,
                                onMenu = { scope.launch { drawerState.open() } },
                                onAnimeClick = { navController.navigate("details/$it") }
                            )
                        }
                        composable(MainDestination.FAVORITES.route) {
                            FavoritesScreen(
                                padding,
                                onMenu = { scope.launch { drawerState.open() } },
                                onAnimeClick = { navController.navigate("details/$it") }
                            )
                        }
                        composable(MainDestination.DISCOVER.route) {
                            DiscoverScreen(
                                padding,
                                onMenu = { scope.launch { drawerState.open() } },
                                onAnimeClick = { navController.navigate("details/$it") }
                            )
                        }
                        composable(MainDestination.SETTINGS.route) {
                            SettingsScreen(
                                padding,
                                onMenu = { scope.launch { drawerState.open() } },
                                onAboutClick = {
                                    navController.navigate("about") { launchSingleTop = true }
                                }
                            )
                        }
                        composable("about") {
                            AboutScreen(padding, onBack = navController::navigateUp)
                        }
                        composable("providers") {
                            ProvidersScreen(padding, navController::navigateUp, onAnimeClick = { navController.navigate("details/$it") })
                        }
                        composable("season") {
                            CurrentSeasonScreen(padding, navController::navigateUp, onAnimeClick = { navController.navigate("details/$it") })
                        }
                        composable("dubs") {
                            DubReleasesScreen(padding, navController::navigateUp, onAnimeClick = { navController.navigate("details/$it") })
                        }
                        composable("news") {
                            NewsScreen(
                                padding,
                                navController::navigateUp,
                                onNewsClick = { id -> navController.navigate("news/${android.net.Uri.encode(id)}") }
                            )
                        }
                        composable("news/{announcementId}") {
                            NewsDetailScreen(padding, navController::navigateUp)
                        }
                        composable("statistics") {
                            ReleaseStatisticsScreen(padding, navController::navigateUp)
                        }
                        composable("changelog") {
                            ChangelogScreen(padding, navController::navigateUp)
                        }
                        composable("details/{animeId}") { entry ->
                            AnimeDetailScreen(
                                scaffoldPadding = padding,
                                animeId = entry.arguments?.getString("animeId").orEmpty(),
                                onBack = navController::navigateUp
                            )
                        }
                        composable("release/{animeId}/{season}/{episode}/{language}") { entry ->
                            AnimeDetailScreen(
                                scaffoldPadding = padding,
                                animeId = entry.arguments?.getString("animeId").orEmpty(),
                                onBack = navController::navigateUp,
                                focusedSeason = entry.arguments?.getString("season")?.toIntOrNull(),
                                focusedEpisode = entry.arguments?.getString("episode")?.toIntOrNull(),
                                focusedLanguage = entry.arguments?.getString("language")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainNavigationRail(
    selected: MainDestination?,
    onSelect: (MainDestination) -> Unit
) {
    NavigationRail {
        MainDestination.entries.forEach { destination ->
            NavigationRailItem(
                modifier = Modifier.testTag(UiTags.NAV_PREFIX + destination.route),
                selected = selected == destination,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(androidx.compose.ui.res.stringResource(destination.label)) }
            )
        }
    }
}
