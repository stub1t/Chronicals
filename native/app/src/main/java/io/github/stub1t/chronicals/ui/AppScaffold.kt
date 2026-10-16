package io.github.stub1t.chronicals.ui

import androidx.annotation.StringRes
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.navigation
import io.github.stub1t.chronicals.ui.analysis.AnalysisScreen
import io.github.stub1t.chronicals.ui.analysis.ReportScreen
import io.github.stub1t.chronicals.ui.calendar.CalendarScreen
import io.github.stub1t.chronicals.ui.calendar.DayPage
import io.github.stub1t.chronicals.ui.calendar.SearchScreen
import java.time.LocalDate
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.Diary
import io.github.stub1t.chronicals.data.Settings
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.stub1t.chronicals.ui.me.EntryTypesPage
import io.github.stub1t.chronicals.ui.me.MeActions
import io.github.stub1t.chronicals.ui.me.MeScreen
import io.github.stub1t.chronicals.ui.me.MeViewModel
import io.github.stub1t.chronicals.ui.me.MedicationsPage
import io.github.stub1t.chronicals.ui.me.ProfilePage
import io.github.stub1t.chronicals.ui.me.TriggersPage
import io.github.stub1t.chronicals.ui.me.WelcomeScreen
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.stub1t.chronicals.ui.meals.MealsScreen
import io.github.stub1t.chronicals.ui.today.TodayScreen

enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector, val startRoute: String = route) {
    Today("today", R.string.nav_today, Icons.Outlined.Home),
    Meals("meals", R.string.nav_meals, Icons.Outlined.Restaurant),
    Calendar("calendar", R.string.nav_calendar, Icons.Outlined.CalendarMonth, "calendar/month"),
    Analysis("analysis", R.string.nav_insights, Icons.Outlined.BarChart, "analysis/main"),
    Me("me", R.string.nav_me, Icons.Outlined.AccountCircle, "me/main"),
}

/** Short confirmations ("Headache saved") at the bottom of the screen. */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/**
 * The five tabs. Each keeps its screen state while another is shown (saveState/restoreState), and
 * switching has no animation, so a tab appears on the next frame.
 */
@Composable
fun AppScaffold(
    diary: Diary,
    settings: Settings,
    meActions: MeActions,
    placeSearch: @Composable () -> Unit,
    openLog: OpenLog?,
    onLogOpened: () -> Unit,
    importUri: android.net.Uri?,
    onImport: (android.net.Uri) -> Unit,
    onImportCancelled: () -> Unit,
) {
    val nav = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as io.github.stub1t.chronicals.ChronicalsApp
    // One for Me and its pages, so they share what they loaded.
    val meVm: MeViewModel = viewModel { MeViewModel(diary) }
    // The introduction runs once, and only for an empty diary: an update from the old app or a restored backup skips it.
    var welcome by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (settings.onboarded) return@LaunchedEffect
        val empty = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { diary.countEntries() == 0 && diary.profile().displayName.isNullOrBlank() }
        if (empty) welcome = true else settings.onboarded = true
    }
    if (welcome) {
        WelcomeScreen(meVm, settings, meActions, placeSearch) { welcome = false }
        return
    }
    val current by nav.currentBackStackEntryAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { app.messages.collect { snackbar.showSnackbar(it) } }
    // A tapped "still there?" notification: today's log opens on Today, an older one on its day page.
    LaunchedEffect(openLog) {
        val target = openLog ?: return@LaunchedEffect
        if (target.date != io.github.stub1t.chronicals.data.Times.today()) nav.navigate("calendar/day/${target.date}")
        else nav.navigate(Tab.Today.route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }
    }
    CompositionLocalProvider(LocalSnackbar provides snackbar) { Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                for (tab in Tab.entries) {
                    val selected = current?.destination?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            // A second tap on the selected tab goes back to its first screen.
                            if (selected) nav.popBackStack(tab.startRoute, inclusive = false)
                            else nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.Today.route,
            modifier = Modifier.padding(padding),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None },
        ) {
            val openMe = { nav.navigate(Tab.Me.route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }
            composable(Tab.Today.route) { TodayScreen(diary, openLog, onLogOpened, openSettings = openMe) }
            composable(Tab.Meals.route) { MealsScreen(diary) }
            navigation(startDestination = "calendar/month", route = Tab.Calendar.route) {
                composable("calendar/month") {
                    CalendarScreen(diary, openDay = { nav.navigate("calendar/day/$it") }, openSearch = { nav.navigate("calendar/search") })
                }
                composable("calendar/day/{date}") { entry ->
                    val date = LocalDate.parse(entry.arguments?.getString("date"))
                    DayPage(diary, date, openLog, onLogOpened, openSettings = openMe, onBack = { nav.popBackStack() }, onDate = {
                        nav.navigate("calendar/day/$it") { popUpTo("calendar/day/{date}") { inclusive = true } }
                    })
                }
                composable("calendar/search") {
                    SearchScreen(diary, onBack = { nav.popBackStack() }, openDay = { nav.navigate("calendar/day/$it") })
                }
            }
            navigation(startDestination = "analysis/main", route = Tab.Analysis.route) {
                composable("analysis/main") { AnalysisScreen(diary, openReport = { nav.navigate("analysis/report") }) }
                composable("analysis/report") { ReportScreen(diary, onBack = { nav.popBackStack() }) }
            }
            navigation(startDestination = "me/main", route = Tab.Me.route) {
                composable("me/main") { MeScreen(meVm, settings, meActions) { nav.navigate(it) } }
                composable("me/profile") { ProfilePage(meVm, onBack = { nav.popBackStack() }, placeSearch = placeSearch) }
                composable("me/medications") { MedicationsPage(meVm, onBack = { nav.popBackStack() }) }
                composable("me/entry-types") { EntryTypesPage(meVm, onBack = { nav.popBackStack() }) }
                composable("me/triggers") { TriggersPage(meVm, onBack = { nav.popBackStack() }) }
                composable("welcome") { WelcomeScreen(meVm, settings, meActions, placeSearch) { nav.popBackStack() } }
            }
        }
    } }
    importUri?.let { uri ->
        val name = remember(uri) { uri.lastPathSegment?.substringAfterLast('/') ?: "" }
        AlertDialog(onDismissRequest = onImportCancelled,
            text = { Text(stringResource(R.string.settings_import_confirm, name)) },
            confirmButton = { TextButton(onClick = { onImport(uri) }) { Text(stringResource(R.string.settings_import)) } },
            dismissButton = { TextButton(onClick = onImportCancelled) { Text(stringResource(R.string.cancel)) } })
    }
}
