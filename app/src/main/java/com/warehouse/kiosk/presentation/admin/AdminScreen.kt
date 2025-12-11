package com.warehouse.kiosk.presentation.admin

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: AdminViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit,
    onNavigateToAppSelection: () -> Unit,
    onNavigateToKioskSettings: () -> Unit,
    onNavigateToAutoStart: () -> Unit,
    onNavigateToWmsInstall: () -> Unit,
    onNavigateToDeviceInfo: () -> Unit,
    onNavigateToAppUpdates: () -> Unit
) {
    val context = LocalContext.current
    val isKioskModeActive by viewModel.isKioskModeActive.collectAsStateWithLifecycle()
    val autoStartAppName by viewModel.autoStartAppName.collectAsStateWithLifecycle()
    val wallpaperState by viewModel.wallpaperState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    // Наблюдаваме wallpaperState и показваме Snackbar при промени
    LaunchedEffect(wallpaperState) {
        when (val state = wallpaperState) {
            is WallpaperState.Success -> {
                snackbarHostState.showSnackbar("Тапетът е зададен успешно")
                viewModel.resetWallpaperState()
            }
            is WallpaperState.Error -> {
                snackbarHostState.showSnackbar("Грешка: ${state.message}")
                viewModel.resetWallpaperState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
        ) {
            item {
                SettingsItem(
                    title = "System Settings",
                    subtitle = "Open device settings",
                    icon = Icons.Default.Settings,
                    onClick = {
                        val intent = Intent(Settings.ACTION_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                        }
                        context.startActivity(intent)
                    }
                )
            }
            item {
                SettingsItem(
                    title = "Device Information",
                    subtitle = "Configure staff name and location",
                    icon = Icons.Default.Info,
                    onClick = onNavigateToDeviceInfo
                )
            }
            item {
                SettingsItem(
                    title = "Allowed Apps",
                    subtitle = "Choose which apps to show on launcher",
                    icon = Icons.Default.Star,
                    onClick = onNavigateToAppSelection
                )
            }
            item {
                SettingsItem(
                    title = "Kiosk Settings",
                    subtitle = if (isKioskModeActive) "Kiosk mode enabled" else "Kiosk mode disabled",
                    icon = Icons.Default.Lock,
                    onClick = onNavigateToKioskSettings
                )
            }
            item {
                SettingsItem(
                    title = "Auto-Start App",
                    subtitle = autoStartAppName,
                    icon = Icons.Default.PlayArrow,
                    onClick = onNavigateToAutoStart
                )
            }
            item {
                SettingsItem(
                    title = "Install WMS App",
                    subtitle = "Download and install WMS application",
                    icon = Icons.Default.Download,
                    onClick = onNavigateToWmsInstall
                )
            }
            item {
                SettingsItem(
                    title = "App Updates",
                    subtitle = "Check for and install app updates",
                    icon = Icons.Default.SystemUpdate,
                    onClick = onNavigateToAppUpdates
                )
            }
            item {
                SettingsItem(
                    title = "Задай тапет",
                    subtitle = "Задай wallpaper.jpg като тапет на устройството",
                    icon = Icons.Default.Wallpaper,
                    onClick = { viewModel.setWallpaper() }
                )
            }
        }
    }
}

@Composable
fun SettingsItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = { onClick() }
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = title, modifier = Modifier.padding(end = 16.dp))
        Column {
            Text(text = title)
            Text(text = subtitle)
        }
    }
}