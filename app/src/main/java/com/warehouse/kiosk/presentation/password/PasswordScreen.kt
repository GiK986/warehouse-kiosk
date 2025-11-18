package com.warehouse.kiosk.presentation.password

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun PasswordScreen(
    viewModel: PasswordViewModel = hiltViewModel(),
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

    Scaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(it)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Enter Admin Password")
            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { viewModel.onPasswordEntered(password) }) {
                Text(text = "Enter")
            }
        }
    }
}