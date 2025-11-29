package com.warehouse.kiosk.presentation.password

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PasswordDialog(
    show: Boolean,
    viewModel: PasswordViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }

    // Local state to block UI before closing and prevent InputManager warning
    var isClosing by remember { mutableStateOf(false) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    DisposableEffect(Unit) {
        onDispose {
            // Clean up password only if not closing successfully
            if (!isClosing) {
                password = ""
            }
        }
    }

    // Handle successful login
    LaunchedEffect(uiState.isPasswordCorrect) {
        if (uiState.isPasswordCorrect == true) {
            isClosing = true

            // Hide keyboard and clear focus
            keyboardController?.hide()
            focusManager.clearFocus(force = true)

            // Wait for InputConnection to be properly removed (prevents InputManager warning)
            delay(300)

            viewModel.consumeEvents()

            // Safe to dismiss dialog now
            onDismiss()
            onLoginSuccess()

            isClosing = false
        }
    }

    if (show) {
        PasswordDialogContent(
            password = password,
            onPasswordChange = { password = it },
            isClosing = isClosing,
            error = uiState.error,
            onDismiss = {
                if (!isClosing) {
                    // Start closing process
                    isClosing = true

                    // Hide keyboard and clear focus first
                    keyboardController?.hide()
                    focusManager.clearFocus(force = true)

                    // Schedule actual dismiss after InputConnection cleanup
                    kotlinx.coroutines.MainScope().launch {
                        delay(300) // Wait for InputManager to release connection
                        password = ""
                        onDismiss()
                        isClosing = false
                    }
                }
            },
            onConfirm = { viewModel.onPasswordEntered(password) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordDialogContent(
    password: String,
    onPasswordChange: (String) -> Unit,
    isClosing: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Title
                Text(
                    text = "Admin Access",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Content
                Column {
                    OutlinedTextField(
                        value = password,
                        onValueChange = onPasswordChange,
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !isClosing,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = { if (!isClosing) onConfirm() }
                        ),
                        isError = error != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp)
                ) {
                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(
                        onClick = onDismiss,
                        enabled = !isClosing
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = onConfirm,
                        enabled = !isClosing
                    ) {
                        Text("Enter")
                    }
                }
            }
        }
    }
}