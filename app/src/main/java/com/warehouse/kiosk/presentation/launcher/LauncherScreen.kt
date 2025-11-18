package com.warehouse.kiosk.presentation.launcher

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warehouse.kiosk.R
import com.warehouse.kiosk.data.database.AppEntity
import com.warehouse.kiosk.presentation.password.PasswordDialog
import com.warehouse.kiosk.showPasswordDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel = hiltViewModel(),
    onNavigateToAdmin: () -> Unit
) {
    val enabledApps by viewModel.enabledApps.collectAsStateWithLifecycle()

    if (showPasswordDialog.value) {
        PasswordDialog(
            onDismiss = { showPasswordDialog.value = false },
            onLoginSuccess = {
                showPasswordDialog.value = false
                onNavigateToAdmin()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) },
                actions = {
                    IconButton(onClick = { showPasswordDialog.value = true }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Admin Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (enabledApps.isEmpty()) {
                // Show a message if no apps are enabled
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "No apps enabled.")
                    Text(text = "Click settings to configure.")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 96.dp),
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(enabledApps) { app ->
                        AppGridItem(
                            app = app,
                            onAppClick = { viewModel.launchApp(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppGridItem(
    app: AppEntity,
    onAppClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = { onAppClick() }
            )
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(packageName = app.packageName)
        Text(
            text = app.appName,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    val iconDrawable: Drawable? = remember(packageName) {
        try {
            packageManager.getApplicationIcon(packageName)
        } catch (e: PackageManager.NameNotFoundException) {
            null // Return null if app is not found
        }
    }

    if (iconDrawable != null) {
        Image(
            bitmap = iconDrawable.toBitmap().asImageBitmap(),
            contentDescription = null, // Decorative image
            modifier = modifier.size(64.dp)
        )
    } else {
        // Show a placeholder if icon loading fails
        Box(
            modifier = modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.Gray)
        )
    }
}