package com.warehouse.kiosk.presentation.wms_install

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warehouse.kiosk.domain.model.SavedApkUrl
import com.warehouse.kiosk.domain.usecase.DownloadAndInstallApkUseCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WmsInstallScreen(
    viewModel: WmsInstallViewModel = hiltViewModel(),
    onNavigateUp: () -> Unit
) {
    val apkUrl by viewModel.apkUrl.collectAsStateWithLifecycle()
    val installState by viewModel.installState.collectAsStateWithLifecycle()
    val saveUrlEnabled by viewModel.saveUrlEnabled.collectAsStateWithLifecycle()
    val savedApkUrls by viewModel.savedApkUrls.collectAsStateWithLifecycle()

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Download и инсталиране на WMS приложение",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }

            item {
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
            }

            // Toggle за запазване на URL
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Запази URL адреса за по-късно",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = saveUrlEnabled,
                        onCheckedChange = { viewModel.toggleSaveUrl(it) }
                    )
                }
            }

            item {
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
            }

            // Показване на статус
            item {
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
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                // Първи ред: Икона и съобщение
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = "Успешна инсталация!",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Приложението беше инсталирано успешно.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (state.packageName != null) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Пакет: ${state.packageName}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }

                                // Втори ред: OK бутон в центъра
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { viewModel.clearState() },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) {
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
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Грешка при инсталиране",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    textAlign = TextAlign.Center
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
            }

            // Запазени URL-и
            if (savedApkUrls.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Запазени URL адреси",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                items(savedApkUrls) { savedUrl ->
                    SavedUrlCard(
                        savedUrl = savedUrl,
                        onSelect = { viewModel.selectSavedUrl(savedUrl) },
                        onRemove = { viewModel.removeSavedUrl(savedUrl.packageName) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Забележка: Инсталацията е silent (без потребителска интеракция) благодарение на Device Owner режим.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SavedUrlCard(
    savedUrl: SavedApkUrl,
    onSelect: () -> Unit,
    onRemove: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = savedUrl.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = savedUrl.url,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = savedUrl.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Премахни",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}