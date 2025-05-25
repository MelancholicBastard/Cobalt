package com.melancholicbastard.cobalt.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.melancholicbastard.cobalt.ui.theme.screens.HistoryScreen
import com.melancholicbastard.cobalt.ui.theme.screens.RecordScreen
import com.melancholicbastard.cobalt.ui.theme.screens.SettingsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Record.route,
        modifier = modifier
    ) {
        // Экран записи аудио
        composable(Screen.Record.route) {
            RecordScreen(
                navController = navController
//                onNavigateToHistory = {
//                    navController.navigate(Screen.History.route)
//                }
            )
        }

        // Экран истории записей
        composable(Screen.History.route) {
            HistoryScreen(
                navController = navController
            )
        }

        // Экран настроек
        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}