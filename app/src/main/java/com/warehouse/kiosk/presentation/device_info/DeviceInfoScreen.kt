package com.warehouse.kiosk.presentation.device_info

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    viewModel: DeviceInfoViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit
) {
    val staffName by viewModel.staffName.collectAsStateWithLifecycle()
    val locationName by viewModel.locationName.collectAsStateWithLifecycle()

    var staffNameInput by remember(staffName) { mutableStateOf(staffName) }
    var locationNameInput by remember(locationName) { mutableStateOf(locationName) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Информация за устройство") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = locationNameInput,
                onValueChange = { locationNameInput = it },
                label = { Text("Локация / Склад") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = staffNameInput,
                onValueChange = { staffNameInput = it },
                label = { Text("Име на служител") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                    }
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    keyboardController?.hide()

                    // Update both fields
                    if (locationNameInput.isNotBlank()) {
                        viewModel.updateLocationName(locationNameInput.trim())
                    }
                    if (staffNameInput.isNotBlank()) {
                        viewModel.updateStaffName(staffNameInput.trim())
                    }

                    // Show confirmation
                    scope.launch {
                        snackbarHostState.showSnackbar("Информацията е запазена успешно")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = locationNameInput.isNotBlank() && staffNameInput.isNotBlank()
            ) {
                Text("Запази промените")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Забележка: Името на служителя ще се показва на заключения екран",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}