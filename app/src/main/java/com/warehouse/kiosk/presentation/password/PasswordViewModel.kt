package com.warehouse.kiosk.presentation.password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.warehouse.kiosk.data.repository.KioskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

// Simple hashing function for demonstration
private fun String.toSha256(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(this.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

data class PasswordUiState(
    val isPasswordCorrect: Boolean? = null,
    val error: String? = null
)

@HiltViewModel
class PasswordViewModel @Inject constructor(
    private val repository: KioskRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PasswordUiState())
    val uiState = _uiState.asStateFlow()

    companion object {
        const val DEFAULT_PASSWORD = "1234"
    }

    fun onPasswordEntered(password: String) {
        viewModelScope.launch {
            val storedHash = repository.passwordHash.first()

            if (storedHash == null) {
                // First time setup: check against default password
                if (password == DEFAULT_PASSWORD) {
                    // Save the default password hash and proceed
                    repository.setPasswordHash(DEFAULT_PASSWORD.toSha256())
                    _uiState.update { it.copy(isPasswordCorrect = true) }
                } else {
                    _uiState.update { it.copy(error = "Incorrect default password") }
                }
            } else {
                // Normal check
                if (password.toSha256() == storedHash) {
                    _uiState.update { it.copy(isPasswordCorrect = true) }
                } else {
                    _uiState.update { it.copy(error = "Incorrect password") }
                }
            }
        }
    }

    fun consumeEvents() {
        _uiState.update { PasswordUiState() } // Reset state after navigation
    }
}