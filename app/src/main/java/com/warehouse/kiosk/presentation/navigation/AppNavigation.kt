package com.warehouse.kiosk.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.warehouse.kiosk.presentation.admin.AdminScreen
import com.warehouse.kiosk.presentation.app_selection.AppSelectionScreen
import com.warehouse.kiosk.presentation.auto_start.AutoStartScreen
import com.warehouse.kiosk.presentation.kiosk_settings.KioskSettingsScreen
import com.warehouse.kiosk.presentation.launcher.LauncherScreen

object AppDestinations {
    const val LAUNCHER_ROUTE = "launcher"
    const val ADMIN_ROUTE = "admin" // The new hub screen
    const val APP_SELECTION_ROUTE = "app_selection"
    const val KIOSK_SETTINGS_ROUTE = "kiosk_settings"
    const val AUTO_START_ROUTE = "auto_start"
}

@Composable
fun AppNavigation(
    onExitKiosk: () -> Unit // New callback
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = AppDestinations.LAUNCHER_ROUTE) {
        composable(AppDestinations.LAUNCHER_ROUTE) {
            LauncherScreen(
                onNavigateToAdmin = { navController.navigate(AppDestinations.ADMIN_ROUTE) } // Navigate to the new hub
            )
        }

        composable(AppDestinations.ADMIN_ROUTE) {
            AdminScreen(
                onNavigateUp = { navController.navigateUp() },
                onNavigateToAppSelection = { navController.navigate(AppDestinations.APP_SELECTION_ROUTE) },
                onNavigateToKioskSettings = { navController.navigate(AppDestinations.KIOSK_SETTINGS_ROUTE) },
                onNavigateToAutoStart = { navController.navigate(AppDestinations.AUTO_START_ROUTE) },
                onExitKiosk = onExitKiosk // Pass the callback down
            )
        }

        composable(AppDestinations.APP_SELECTION_ROUTE) {
            AppSelectionScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }

        composable(AppDestinations.KIOSK_SETTINGS_ROUTE) {
            KioskSettingsScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }

        composable(AppDestinations.AUTO_START_ROUTE) {
            AutoStartScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }
    }
}