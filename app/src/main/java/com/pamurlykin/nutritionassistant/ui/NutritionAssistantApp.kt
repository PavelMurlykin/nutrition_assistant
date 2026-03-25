package com.pamurlykin.nutritionassistant.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pamurlykin.nutritionassistant.AppContainer
import com.pamurlykin.nutritionassistant.notification.BuildCompat
import com.pamurlykin.nutritionassistant.ui.daily.DailyStatsScreen
import com.pamurlykin.nutritionassistant.ui.daily.DailyStatsViewModel
import com.pamurlykin.nutritionassistant.ui.navigation.TopLevelDestination
import com.pamurlykin.nutritionassistant.ui.schedule.ScheduleScreen
import com.pamurlykin.nutritionassistant.ui.schedule.ScheduleViewModel

@Composable
fun NutritionAssistantApp(
    container: AppContainer,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = {},
    )

    val scheduleViewModel: ScheduleViewModel = viewModel(
        factory = ScheduleViewModel.factory(
            repository = container.repository,
            notificationScheduler = container.notificationScheduler,
        ),
    )
    val dailyStatsViewModel: DailyStatsViewModel = viewModel(
        factory = DailyStatsViewModel.factory(repository = container.repository),
    )

    LaunchedEffect(Unit) {
        if (
            BuildCompat.requiresNotificationPermission() &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

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
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.SCHEDULE.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.SCHEDULE.route) {
                ScheduleScreen(viewModel = scheduleViewModel)
            }
            composable(TopLevelDestination.DAILY_STATS.route) {
                DailyStatsScreen(viewModel = dailyStatsViewModel)
            }
        }
    }
}
