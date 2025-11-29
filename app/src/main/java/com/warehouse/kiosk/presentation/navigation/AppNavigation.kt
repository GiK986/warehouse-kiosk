package com.warehouse.kiosk.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.warehouse.kiosk.presentation.admin.AdminScreen
import com.warehouse.kiosk.presentation.app_selection.AppSelectionScreen
import com.warehouse.kiosk.presentation.auto_start.AutoStartScreen
import com.warehouse.kiosk.presentation.device_info.DeviceInfoScreen
import com.warehouse.kiosk.presentation.kiosk_settings.KioskSettingsScreen
import com.warehouse.kiosk.presentation.launcher.LauncherScreen
import com.warehouse.kiosk.presentation.wms_install.WmsInstallScreen

object AppDestinations {
    const val LAUNCHER_ROUTE = "launcher"
    const val ADMIN_ROUTE = "admin"
    const val APP_SELECTION_ROUTE = "app_selection"
    const val KIOSK_SETTINGS_ROUTE = "kiosk_settings"
    const val AUTO_START_ROUTE = "auto_start"
    const val WMS_INSTALL_ROUTE = "wms_install"
    const val DEVICE_INFO_ROUTE = "device_info"
}

@Composable
fun AppNavigation(
    shouldResetToLauncher: Boolean = false,
    onResetHandled: () -> Unit = {}
) {
    val navController = rememberNavController()

    // Observe Home button press and navigate back to launcher
    LaunchedEffect(shouldResetToLauncher) {
        if (shouldResetToLauncher) {
            navController.popBackStack(AppDestinations.LAUNCHER_ROUTE, inclusive = false)
            onResetHandled()
        }
    }

    NavHost(navController = navController, startDestination = AppDestinations.LAUNCHER_ROUTE) {
        composable(AppDestinations.LAUNCHER_ROUTE) {
            LauncherScreen(
                onNavigateToAdmin = { navController.navigate(AppDestinations.ADMIN_ROUTE) }
            )
        }

        composable(AppDestinations.ADMIN_ROUTE) {
            AdminScreen(
                onNavigateUp = {
                    navController.popBackStack(AppDestinations.LAUNCHER_ROUTE, inclusive = false)
                },
                onNavigateToAppSelection = { navController.navigate(AppDestinations.APP_SELECTION_ROUTE) },
                onNavigateToKioskSettings = { navController.navigate(AppDestinations.KIOSK_SETTINGS_ROUTE) },
                onNavigateToAutoStart = { navController.navigate(AppDestinations.AUTO_START_ROUTE) },
                onNavigateToWmsInstall = { navController.navigate(AppDestinations.WMS_INSTALL_ROUTE) },
                onNavigateToDeviceInfo = { navController.navigate(AppDestinations.DEVICE_INFO_ROUTE) }
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

        composable(AppDestinations.WMS_INSTALL_ROUTE) {
            WmsInstallScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }

        composable(AppDestinations.DEVICE_INFO_ROUTE) {
            DeviceInfoScreen(
                onNavigateUp = { navController.navigateUp() }
            )
        }
    }
}