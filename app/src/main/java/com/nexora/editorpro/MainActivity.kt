package com.nexora.editorpro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nexora.editorpro.theme.NexoraEditorProTheme
import com.nexora.editorpro.ui.navigation.Screen
import com.nexora.editorpro.ui.screens.EditorScreen
import com.nexora.editorpro.ui.screens.HomeScreen
import com.nexora.editorpro.ui.screens.SettingsScreen
import com.nexora.editorpro.viewmodel.EditorViewModel
import com.nexora.editorpro.viewmodel.HomeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NexoraEditorProTheme {
                val navController = rememberNavController()
                val homeViewModel: HomeViewModel = viewModel()
                val editorViewModel: EditorViewModel = viewModel()

                NavHost(
                    navController = navController,
                    startDestination = Screen.Home.route
                ) {
                    composable(Screen.Home.route) {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onNavigateToEditor = { projectId ->
                                navController.navigate(Screen.Editor.createRoute(projectId))
                            },
                            onNavigateToSettings = {
                                navController.navigate(Screen.Settings.route)
                            }
                        )
                    }

                    composable(
                        route = Screen.Editor.route,
                        arguments = listOf(
                            navArgument("projectId") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
                        EditorScreen(
                            projectId = projectId,
                            viewModel = editorViewModel,
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }

                    composable(Screen.Settings.route) {
                        SettingsScreen(
                            onNavigateBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}