package com.smartcal.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import com.smartcal.app.data.AuthRepository
import com.smartcal.app.data.EventRepository
import com.smartcal.app.data.FinanceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepo: AuthRepository,
    private val eventRepo: EventRepository,
    private val financeRepo: FinanceRepository
) : ViewModel() {

    val user: StateFlow<FirebaseUser?> = authRepo.authState
        .stateIn(viewModelScope, SharingStarted.Eagerly, authRepo.currentUser)

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Wypełnij wszystkie pola") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepo.login(email.trim(), password)
                .onSuccess { syncAfterLogin() }
                .onFailure { e -> _uiState.update { it.copy(error = mapFirebaseError(e.message)) } }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun register(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Wypełnij wszystkie pola") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(error = "Hasło musi mieć co najmniej 6 znaków") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepo.register(email.trim(), password)
                .onFailure { e -> _uiState.update { it.copy(error = mapFirebaseError(e.message)) } }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            eventRepo.clearLocalData()
            financeRepo.clearLocalData()
            authRepo.logout()
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private suspend fun syncAfterLogin() {
        eventRepo.syncFromCloud()
        financeRepo.syncFromCloud()
    }

    private fun mapFirebaseError(msg: String?): String = when {
        msg == null -> "Nieznany błąd"
        "no user record" in msg || "user-not-found" in msg -> "Nie znaleziono konta"
        "password is invalid" in msg || "wrong-password" in msg -> "Nieprawidłowe hasło"
        "email address is already" in msg || "email-already-in-use" in msg -> "Ten email jest już zajęty"
        "badly formatted" in msg || "invalid-email" in msg -> "Nieprawidłowy adres email"
        "network" in msg -> "Brak połączenia z internetem"
        else -> "Błąd logowania"
    }
}
