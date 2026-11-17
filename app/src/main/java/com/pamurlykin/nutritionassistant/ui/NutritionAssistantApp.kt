package com.pamurlykin.nutritionassistant.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pamurlykin.nutritionassistant.AppContainer
import com.pamurlykin.nutritionassistant.ui.daily.DailyStatsScreen
import com.pamurlykin.nutritionassistant.ui.catalog.CatalogScreen
import com.pamurlykin.nutritionassistant.ui.daily.DailyStatsViewModel
import com.pamurlykin.nutritionassistant.ui.navigation.TopLevelDestination
import com.pamurlykin.nutritionassistant.ui.schedule.ScheduleScreen
import com.pamurlykin.nutritionassistant.ui.schedule.ScheduleViewModel
import com.pamurlykin.nutritionassistant.ui.backup.BackupScreen
import com.pamurlykin.nutritionassistant.ui.backup.BackupViewModel
import com.pamurlykin.nutritionassistant.ui.components.AppDialog

@Composable
fun NutritionAssistantApp(
    container: AppContainer,
) {
    val navController = rememberNavController()

    val scheduleViewModel: ScheduleViewModel = viewModel(
        factory = ScheduleViewModel.factory(
            repository = container.repository,
            notificationScheduler = container.notificationScheduler,
        ),
    )
    val dailyStatsViewModel: DailyStatsViewModel = viewModel(
        factory = DailyStatsViewModel.factory(repository = container.repository),
    )
    val backupViewModel: BackupViewModel = viewModel(factory = BackupViewModel.factory(container))

    val error by dailyStatsViewModel.error.collectAsStateWithLifecycle()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.CALENDAR.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.SCHEDULE.route) {
                ScheduleScreen(viewModel = scheduleViewModel)
            }
            composable(TopLevelDestination.CALENDAR.route) {
                DailyStatsScreen(viewModel = dailyStatsViewModel)
            }
            composable(TopLevelDestination.CATALOG.route) {
                CatalogScreen(viewModel = dailyStatsViewModel)
            }
            composable(TopLevelDestination.DATA.route) {
                BackupScreen(viewModel = backupViewModel)
            }
        }
    }
    error?.let { message -> AppDialog(onDismissRequest = { dailyStatsViewModel.error.value = null },
        title = { Text("Не удалось выполнить действие") }, text = { Text(message) }, dismissButton = null,
        confirmButton = { TextButton(onClick = { dailyStatsViewModel.error.value = null }) { Text("Понятно") } }) }
}
