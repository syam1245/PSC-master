package com.example.pscmaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
    val inputViewModel: InputViewModel = hiltViewModel()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
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
