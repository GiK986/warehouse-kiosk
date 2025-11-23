package com.warehouse.kiosk.presentation.wms_install

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warehouse.kiosk.domain.usecase.DownloadAndInstallApkUseCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WmsInstallScreen(
    viewModel: WmsInstallViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit
) {
    val apkUrl by viewModel.apkUrl.collectAsStateWithLifecycle()
    val installState by viewModel.installState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Install WMS App") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Download и инсталиране на WMS приложение",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            OutlinedTextField(
                value = apkUrl,
                onValueChange = { viewModel.updateApkUrl(it) },
                label = { Text("APK URL") },
                placeholder = { Text("https://example.com/app.apk") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = installState !is DownloadAndInstallApkUseCase.InstallState.Downloading &&
                        installState !is DownloadAndInstallApkUseCase.InstallState.Installing
            )

            Button(
                onClick = { viewModel.installWmsApp() },
                modifier = Modifier.fillMaxWidth(),
                enabled = apkUrl.isNotBlank() &&
                        installState !is DownloadAndInstallApkUseCase.InstallState.Downloading &&
                        installState !is DownloadAndInstallApkUseCase.InstallState.Installing
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Изтегли и инсталирай")
            }

            // Показване на статус
            when (val state = installState) {
                is DownloadAndInstallApkUseCase.InstallState.Downloading -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Изтегляне... ${state.progress}%")
                        }
                    }
                }

                is DownloadAndInstallApkUseCase.InstallState.Installing -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Инсталиране...")
                        }
                    }
                }

                is DownloadAndInstallApkUseCase.InstallState.Success -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "✓",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Инсталацията завърши успешно!")
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.clearState() }) {
                                Text("OK")
                            }
                        }
                    }
                }

                is DownloadAndInstallApkUseCase.InstallState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "✗",
                                style = MaterialTheme.typography.displayMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Грешка: ${state.message}",
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = { viewModel.clearState() }) {
                                Text("OK")
                            }
                        }
                    }
                }

                null -> {
                    // No state yet
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Забележка: Инсталацията е silent (без потребителска интеракция) благодарение на Device Owner режим.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}