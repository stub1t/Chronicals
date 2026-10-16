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
import androidx.compose.material3.Scaffold
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
import io.github.stub1t.chronicals.ui.calendar.CalendarScreen
import io.github.stub1t.chronicals.ui.calendar.DayPage
import io.github.stub1t.chronicals.ui.calendar.SearchScreen
import java.time.LocalDate
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.stub1t.chronicals.R
import io.github.stub1t.chronicals.data.Diary
import io.github.stub1t.chronicals.ui.meals.MealsScreen
import io.github.stub1t.chronicals.ui.today.TodayScreen

enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector, val startRoute: String = route) {
    Today("today", R.string.nav_today, Icons.Outlined.Home),
    Meals("meals", R.string.nav_meals, Icons.Outlined.Restaurant),
    Calendar("calendar", R.string.nav_calendar, Icons.Outlined.CalendarMonth, "calendar/month"),
    Analysis("analysis", R.string.nav_insights, Icons.Outlined.BarChart),
    Me("me", R.string.nav_me, Icons.Outlined.AccountCircle),
}

/** Short confirmations ("Headache saved") at the bottom of the screen. */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/**
 * The five tabs. Each keeps its screen state while another is shown (saveState/restoreState), and
 * switching has no animation, so a tab appears on the next frame.
 */
@Composable
fun AppScaffold(diary: Diary) {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val snackbar = remember { SnackbarHostState() }
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
            composable(Tab.Today.route) { TodayScreen(diary) }
            composable(Tab.Meals.route) { MealsScreen(diary) }
            navigation(startDestination = "calendar/month", route = Tab.Calendar.route) {
                composable("calendar/month") {
                    CalendarScreen(diary, openDay = { nav.navigate("calendar/day/$it") }, openSearch = { nav.navigate("calendar/search") })
                }
                composable("calendar/day/{date}") { entry ->
                    val date = LocalDate.parse(entry.arguments?.getString("date"))
                    DayPage(diary, date, onBack = { nav.popBackStack() }, onDate = {
                        nav.navigate("calendar/day/$it") { popUpTo("calendar/day/{date}") { inclusive = true } }
                    })
                }
                composable("calendar/search") {
                    SearchScreen(diary, onBack = { nav.popBackStack() }, openDay = { nav.navigate("calendar/day/$it") })
                }
            }
            composable(Tab.Analysis.route) { AnalysisScreen(diary) }
            composable(Tab.Me.route) { Placeholder(R.string.nav_me) }
        }
    } }
}

/** Tabs that are not ported yet. */
@Composable
private fun Placeholder(@StringRes title: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(title)) }
}
