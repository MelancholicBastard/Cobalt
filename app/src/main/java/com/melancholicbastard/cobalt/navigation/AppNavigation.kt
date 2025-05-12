package com.melancholicbastard.cobalt.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavigation() {                                                               // управляет NavController
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController)                                      // кастомная нижняя панель
        }
    ) { padding ->
        NavGraph(                                                                   // описывает граф переходов
            navController = navController,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        NavigationBarItem(
            icon = { Icon(Icons.Default.Add, "Запись") },
            label = { Text("Запись") },
            selected = currentRoute == Screen.Record.route,
            onClick = {
                navController.navigate(Screen.Record.route) {
                    popUpTo(navController.graph.findStartDestination().id) {        // Всего одно нажатие кнопки "Назад"
                        saveState = true                                            // Сохранить состояние удалённых экранов
                    }
                    launchSingleTop = true                                          // Избежать дублирования экрана
                }
            }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.DateRange, "История") },
            label = { Text("История") },
            selected = currentRoute == Screen.History.route,
            onClick = {
                navController.navigate(Screen.History.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                }
            }
        )
        NavigationBarItem(
            icon = { Icon(Icons.Default.Settings, "Настройки") },
            label = { Text("Настройки") },
            selected = currentRoute == Screen.Settings.route,
            onClick = {
                navController.navigate(Screen.Settings.route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                }
            }
        )
    }
}

sealed class Screen(val route: String) {                                            // enum маршрутов
    object Record : Screen("record")
    object History : Screen("history")
    object Settings : Screen("settings")
}