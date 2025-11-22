package com.warehouse.kiosk.presentation.password

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable
fun PasswordDialog(
    show: Boolean,
    viewModel: PasswordViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }

    // Добавяме локално състояние, за да блокираме UI преди затваряне
    var isClosing by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    DisposableEffect(Unit) {
        onDispose {
            // Само ако не затваряме успешно, чистим паролата
            if (!isClosing) {
                password = ""
            }
        }
    }

    LaunchedEffect(uiState.isPasswordCorrect) {
        if (uiState.isPasswordCorrect == true) {
            // 1. Маркираме, че започва процес на затваряне
            isClosing = true

            // 2. Скриваме клавиатурата и махаме фокуса
            keyboardController?.hide()
            focusManager.clearFocus(force = true)

            // 3. Важно: Даваме малко повече време (300ms) на системата да обработи
            // загубата на фокус и скриването на клавиатурата, докато диалогът е още видим (но неактивен)
            delay(300)

            viewModel.consumeEvents()

            // 4. Сега вече е безопасно да унищожим прозореца
            onDismiss()
            onLoginSuccess()

            // Ресет на локалния флаг (за всеки случай, ако компонентът се преизползва)
            isClosing = false
        }
    }

    if(show) {
        AlertDialog(
            onDismissRequest = {
                if (!isClosing) {
                    keyboardController?.hide()
                    password = ""
                    onDismiss()
                }
            },
            title = { Text(text = "Admin Access") },
            text = {
                Column {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        // Правим полето неактивно, докато се затваря.
                        // Това помага на InputManager-а да разбере, че връзката е прекъсната.
                        enabled = !isClosing,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                if (!isClosing) viewModel.onPasswordEntered(password)
                            }
                        ),
                        isError = uiState.error != null
                    )
                    uiState.error?.let {
                        Text(text = it)
                    }
                }
            },
            confirmButton = {
                // Блокираме бутона, докато се затваря
                TextButton(
                    onClick = { viewModel.onPasswordEntered(password) },
                    enabled = !isClosing
                ) {
                    Text("Enter")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!isClosing) {
                            keyboardController?.hide()
                            password = ""
                            onDismiss()
                        }
                    },
                    enabled = !isClosing
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}