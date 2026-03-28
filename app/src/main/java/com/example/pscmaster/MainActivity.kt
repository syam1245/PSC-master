package com.example.pscmaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.pscmaster.ui.screens.*
import com.example.pscmaster.ui.theme.PSCMasterTheme
import com.example.pscmaster.ui.viewmodel.InputViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PSCMasterTheme {
                MainNavigation()
            }
        }
    }
}

@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val inputViewModel: InputViewModel = hiltViewModel()

    val bottomNavRoutes = listOf("home", "input", "quiz", "analytics")

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (currentRoute in bottomNavRoutes) {
                NavigationBar {
                    NavigationBarItem(
                        icon = { Icon(Icons.Rounded.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                        selected = currentRoute == "home",
                        onClick = {
                            if (currentRoute != "home") {
                                navController.navigate("home") {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Rounded.AddCircleOutline, contentDescription = "Input") },
                        label = { Text("Input") },
                        selected = currentRoute == "input",
                        onClick = {
                            if (currentRoute != "input") {
                                navController.navigate("input") {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Rounded.PlayCircleOutline, contentDescription = "Quiz Setup") },
                        label = { Text("Practice") },
                        selected = currentRoute == "quiz",
                        onClick = {
                            if (currentRoute != "quiz") {
                                navController.navigate("quiz") {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Rounded.BarChart, contentDescription = "Analytics") },
                        label = { Text("Analytics") },
                        selected = currentRoute == "analytics",
                        onClick = {
                            if (currentRoute != "analytics") {
                                navController.navigate("analytics") {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(300)) + slideInHorizontally(
                    initialOffsetX = { it / 4 },
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(200))
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(300)) + slideInHorizontally(
                    initialOffsetX = { -it / 4 },
                    animationSpec = tween(300)
                )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(200)) + slideOutHorizontally(
                    targetOffsetX = { it / 4 },
                    animationSpec = tween(200)
                )
            }
        ) {
            composable("home") {
                HomeScreen(
                    viewModel = inputViewModel,
                    onNavigateToInput = { navController.navigate("input") },
                    onNavigateToQuiz = { navController.navigate("quiz") },
                    onNavigateToAnalytics = { navController.navigate("analytics") }
                )
            }
            composable("input") {
                InputScreen(
                    viewModel = inputViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToManage = { navController.navigate("manage") }
                )
            }
            composable("manage") {
                ManageQuestionsScreen(
                    viewModel = inputViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("quiz") {
                QuizScreen(
                    viewModel = hiltViewModel(),
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("analytics") {
                AnalyticsScreen(
                    viewModel = hiltViewModel(),
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToMistakes = { navController.navigate("mistake_notebook") },
                    onNavigateToQuiz = { navController.navigate("quiz") }
                )
            }
            composable("mistake_notebook") {
                MistakeNotebookScreen(
                    viewModel = hiltViewModel(),
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
