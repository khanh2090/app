package com.fitness.companion.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fitness.companion.domain.model.ExerciseType
import com.fitness.companion.presentation.counter.CounterScreen
import com.fitness.companion.presentation.history.HistoryScreen
import com.fitness.companion.presentation.home.HomeScreen
import com.fitness.companion.presentation.profile.ProfileScreen
import com.fitness.companion.presentation.workout.GuidesScreen
import com.fitness.companion.presentation.workout.WorkoutPlayerScreen

object Routes {
    const val ARG_EXERCISE_ID = "exerciseId"

    const val HOME = "home"
    const val COUNTER = "counter"
    const val GUIDES = "guides"
    const val PLAYER = "player/{$ARG_EXERCISE_ID}"
    const val HISTORY = "history"
    const val PROFILE = "profile"

    fun player(exerciseId: String) = "player/$exerciseId"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val title: String,
    val icon: ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.HOME, "Trang chủ", "Fitness Companion", Icons.Filled.Home),
    TopLevelDestination(Routes.COUNTER, "Đếm", "Đếm số lần tập", Icons.Filled.AddCircle),
    TopLevelDestination(Routes.GUIDES, "Bài tập", "Hướng dẫn tập luyện", Icons.Filled.PlayArrow),
    TopLevelDestination(Routes.HISTORY, "Lịch sử", "Lịch sử", Icons.Filled.DateRange),
)

/** App shell: top bar, bottom navigation and the navigation graph. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitnessCompanionApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevel = topLevelDestinations.firstOrNull { it.route == currentRoute }

    // Exercise picked with a quick-start button on the home screen. It is handed to the counter
    // tab as state rather than as a route argument, so the tab keeps its normal saved state.
    var requestedExercise by rememberSaveable { mutableStateOf<ExerciseType?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        topLevel?.title ?: when (currentRoute) {
                            Routes.PLAYER -> "Buổi tập"
                            Routes.PROFILE -> "Hồ sơ và đồng bộ"
                            else -> ""
                        },
                    )
                },
                navigationIcon = {
                    if (topLevel == null && currentRoute != null) {
                        IconButton(onClick = navController::popBackStack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                        }
                    }
                },
                actions = {
                    if (currentRoute == Routes.HOME) {
                        IconButton(onClick = { navController.navigate(Routes.PROFILE) }) {
                            Icon(Icons.Filled.Person, contentDescription = "Hồ sơ và đồng bộ")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (topLevel != null) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == topLevel,
                            onClick = { navController.navigateToTopLevel(destination.route) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    onStartCounter = { exercise ->
                        requestedExercise = exercise
                        navController.navigateToTopLevel(Routes.COUNTER)
                    },
                    onOpenGuides = { navController.navigateToTopLevel(Routes.GUIDES) },
                )
            }
            composable(Routes.COUNTER) {
                CounterScreen(
                    requestedExercise = requestedExercise,
                    onExerciseRequestHandled = { requestedExercise = null },
                )
            }
            composable(Routes.GUIDES) {
                GuidesScreen(onStartWorkout = { navController.navigate(Routes.player(it.id)) })
            }
            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument(Routes.ARG_EXERCISE_ID) { type = NavType.StringType }),
            ) {
                WorkoutPlayerScreen(onBack = navController::popBackStack)
            }
            composable(Routes.HISTORY) { HistoryScreen() }
            composable(Routes.PROFILE) { ProfileScreen() }
        }
    }
}

/**
 * Switches between bottom-bar tabs with a single copy of each on the back stack, saving the tab
 * being left and restoring the one being opened.
 *
 * Every tab switch must go through here: the saved states are keyed per tab, and a switch that
 * skips the save step makes the home tab reopen another tab's screen.
 */
private fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
