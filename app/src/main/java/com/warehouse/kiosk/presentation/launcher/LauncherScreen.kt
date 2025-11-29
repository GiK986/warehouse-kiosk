package com.warehouse.kiosk.presentation.launcher

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.warehouse.kiosk.R
import com.warehouse.kiosk.data.database.AppEntity
import com.warehouse.kiosk.presentation.password.PasswordDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherScreen(
    viewModel: LauncherViewModel = hiltViewModel(),
    onNavigateToAdmin: () -> Unit = {}
) {
    val enabledApps by viewModel.enabledApps.collectAsStateWithLifecycle()
    val showPasswordDialog by viewModel.showPasswordDialog.collectAsState()
    val showEditStaffDialog by viewModel.showEditStaffDialog.collectAsState()
    val staffName by viewModel.staffName.collectAsStateWithLifecycle()
    val locationName by viewModel.locationName.collectAsStateWithLifecycle()

    PasswordDialog(
        show = showPasswordDialog,
        onDismiss = { viewModel.hidePasswordDialog() },
        onLoginSuccess = {
            // PasswordDialog вече cleanup-нал input channels преди да вика този callback
            onNavigateToAdmin()
        }
    )

    EditStaffNameDialog(
        show = showEditStaffDialog,
        currentName = staffName,
        onDismiss = { viewModel.hideEditStaffDialog() },
        onSave = { newName ->
            viewModel.updateStaffName(newName)
            viewModel.hideEditStaffDialog()
        }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Изображението за фон
//        Image(
//            painter = painterResource(id = R.drawable.background_wallpaper), // <-- ЗАМЕНИ с твоето изображение
//            contentDescription = "Background Wallpaper",
//            contentScale = ContentScale.Crop, // Crop или FillBounds, за да запълни екрана
//            modifier = Modifier.fillMaxSize(),
//            alpha = 0.7f // Можеш да добавиш прозрачност, за да направиш текста по-четлив
//        )
//        // 1. Добави Box, който ще служи за градиентен фон
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient( // Може да бъде и horizontalGradient
                        colors = listOf(
                            Color(0xFFE8EAF6),
                            Color(0xFFECEFF1),
                            Color(0xFFF5F5F5)
                        )
                    )
                )
        )

        Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),

                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Logo в ляво
                        Image(
                            painter = painterResource(id = R.drawable.logo_auto_plus),
                            contentDescription = "Auto Plus Logo",
                            modifier = Modifier
                                .height(40.dp)
                                .width(120.dp)
                                .padding(end = 16.dp),
                            contentScale = ContentScale.Fit
                        )
                        // App name до логото
                        Text(text = stringResource(id = R.string.app_name))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showPasswordDialog() }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Admin Settings"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            DeviceCardInfo(
                staffName = staffName,
                locationName = locationName,
                onLongClick = { viewModel.showEditStaffDialog() }
            )

            // Grid с приложенията
            Box(modifier = Modifier.fillMaxSize()) {
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
        } catch (_: PackageManager.NameNotFoundException) {
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

@Composable
fun DeviceCardInfo(
    staffName: String,
    locationName: String,
    onLongClick: () -> Unit = {}
) {
    OutlinedCard(
        // 1. Запазваме фона напълно прозрачен, за да се вижда тапетът
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
        ),
        // 2. Ключова промяна: Рамката вече не е плътно черна, а е базирана на
        // цвета на текста/иконите от темата и е полупрозрачна.
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)),
        modifier = Modifier
            .padding(horizontal = 32.dp, vertical = 8.dp)
            .fillMaxWidth() // Добавяме това, за да заеме цялата ширина
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = LocalIndication.current,
                onClick = { /* No single click action */ },
                onLongClick = onLongClick
            )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(), // И тук, за да се центрира съдържанието правилно
            horizontalAlignment = Alignment.CenterHorizontally, // Центрираме съдържанието
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ){
            // --- РЕД ЗА ЛОКАЦИЯ ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ){
                Icon(
                    imageVector = Icons.Default.Warehouse,
                    contentDescription = "Warehouse",
                    modifier = Modifier.size(18.dp),
                    // 3. Правим иконата да съответства на текста за консистентност
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = locationName,
                    style = MaterialTheme.typography.bodyLarge, // Малко по-голям текст
                    // 4. Правим текста леко прозрачен, за да се слее по-добре
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )
            }
            // --- РАЗДЕЛИТЕЛ ---
            HorizontalDivider(
                thickness = 1.dp,
                // 5. Правим разделителя много фин и почти прозрачен
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )
            // --- РЕД ЗА СЛУЖИТЕЛ ---
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ){
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = "AccountCircle",
                    modifier = Modifier.size(18.dp),
                    // 3. Правим иконата да съответства на текста
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = staffName,
                    style = MaterialTheme.typography.bodyLarge, // Малко по-голям текст
                    // 4. Правим текста леко прозрачен
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun EditStaffNameDialog(
    show: Boolean,
    currentName: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    if (!show) return

    var name by remember(currentName) { mutableStateOf(currentName) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Промяна на служител") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Име на служител") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        if (name.isNotBlank()) {
                            onSave(name.trim())
                        }
                    }
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim())
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Запази")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отказ")
            }
        }
    )
}
