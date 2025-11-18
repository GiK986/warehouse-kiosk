package com.warehouse.kiosk.presentation.app_selection

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warehouse.kiosk.data.database.AppEntity
import com.warehouse.kiosk.presentation.launcher.AppIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionScreen(
    viewModel: AppSelectionViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit
) {
    val appList by viewModel.appListState.collectAsStateWithLifecycle()
    val hasUnsavedChanges by viewModel.hasUnsavedChanges.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }

    // Handle back press
    BackHandler(enabled = hasUnsavedChanges) {
        showUnsavedChangesDialog = true
    }

    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            title = { Text("Unsaved Changes") },
            text = { Text("Do you want to save your changes?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.saveChanges()
                    showUnsavedChangesDialog = false
                    onNavigateUp()
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUnsavedChangesDialog = false
                    onNavigateUp() // Discard changes and navigate up
                }) {
                    Text("Discard")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Selection") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (hasUnsavedChanges) {
                            showUnsavedChangesDialog = true
                        } else {
                            onNavigateUp()
                        }
                    }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    Button(onClick = {
                        viewModel.saveChanges()
                        onNavigateUp()
                    }) {
                        Text("Save")
                    }
                }
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    viewModel.refreshAppList()
                    isRefreshing = false
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(appList) { app ->
                    AppListItem(app = app, onCheckedChange = {
                        viewModel.onAppEnableChanged(app, it)
                    })
                }
            }
        }
    }
}

@Composable
fun AppListItem(
    app: AppEntity,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = { onCheckedChange(!app.isEnabled) }
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(packageName = app.packageName, modifier = Modifier.padding(end = 16.dp))
        Text(
            text = app.appName,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = app.isEnabled,
            onCheckedChange = onCheckedChange
        )
    }
}