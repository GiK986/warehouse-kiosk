package com.warehouse.kiosk.presentation.auto_start

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warehouse.kiosk.presentation.launcher.AppIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoStartScreen(
    viewModel: AutoStartViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit
) {
    val enabledApps by viewModel.enabledApps.collectAsStateWithLifecycle()
    val selectedApp by viewModel.selectedAutoStartApp.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto-Start App") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // "None" option
            item {
                RadioButtonItem(
                    text = "None (no app will start on boot)",
                    packageName = null, // No icon for the "None" option
                    selected = selectedApp == null,
                    onClick = { viewModel.onAutoStartAppSelected(null) }
                )
            }
            items(enabledApps) { app ->
                RadioButtonItem(
                    text = app.appName,
                    packageName = app.packageName, // Pass the package name for the icon
                    selected = app.packageName == selectedApp,
                    onClick = { viewModel.onAutoStartAppSelected(app.packageName) }
                )
            }
        }
    }
}

@Composable
private fun RadioButtonItem(
    text: String,
    packageName: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        if (packageName != null) {
            AppIcon(packageName = packageName, modifier = Modifier.padding(start = 16.dp, end = 16.dp))
        }
        Text(text = text, modifier = Modifier.padding(start = 16.dp))
    }
}