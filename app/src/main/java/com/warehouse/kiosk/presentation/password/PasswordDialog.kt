package com.warehouse.kiosk.presentation.password

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PasswordDialog(
    viewModel: PasswordViewModel = hiltViewModel(),
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf("") }

    // Handle navigation as a side effect
    LaunchedEffect(uiState.isPasswordCorrect) {
        if (uiState.isPasswordCorrect == true) {
            viewModel.consumeEvents() // Reset state before navigating
            onLoginSuccess()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Admin Access") },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = { viewModel.onPasswordEntered(password) }
                    ),
                    isError = uiState.error != null
                )
                uiState.error?.let {
                    Text(text = it)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.onPasswordEntered(password) }) {
                Text("Enter")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}