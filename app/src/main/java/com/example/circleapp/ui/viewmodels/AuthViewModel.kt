package com.example.circleapp.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.example.circleapp.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * AuthUiState:
 * Stores input fields + loading + error message + current mode.
 */
data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val username: String = "",
    val phone: String = "",
    val displayName: String = "",
    val isLoading: Boolean = false,
    val error: String = "",
    val mode: AuthMode = AuthMode.LOGIN
)

enum class AuthMode {
    LOGIN, SIGNUP
}

/**
 * AuthViewModel:
 * Owns auth UI state + triggers login/sign-up actions.
 */
class AuthViewModel(
    private val repo: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    fun isSignedIn(): Boolean = repo.isSignedIn()

    fun updateEmail(v: String) { _uiState.update { it.copy(email = v, error = "") } }
    fun updatePassword(v: String) { _uiState.update { it.copy(password = v, error = "") } }
    fun updateUsername(v: String) { _uiState.update { it.copy(username = v, error = "") } }
    fun updatePhone(v: String) { _uiState.update { it.copy(phone = v, error = "") } }
    fun updateDisplayName(v: String) { _uiState.update { it.copy(displayName = v, error = "") } }

    fun toggleMode() {
        _uiState.update {
            it.copy(mode = if (it.mode == AuthMode.LOGIN) AuthMode.SIGNUP else AuthMode.LOGIN, error = "")
        }
    }

    fun login(onSuccess: () -> Unit) {
        _uiState.update { it.copy(isLoading = true, error = "") }
        repo.loginWithEmail(
            email = _uiState.value.email,
            password = _uiState.value.password,
            onSuccess = {
                _uiState.update { it.copy(isLoading = false) }
                onSuccess()
            },
            onError = { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Login failed") }
            }
        )
    }

    fun signUp(onSuccess: () -> Unit) {
        _uiState.update { it.copy(isLoading = true, error = "") }
        repo.signUpWithEmail(
            email = _uiState.value.email,
            password = _uiState.value.password,
            username = _uiState.value.username,
            phone = _uiState.value.phone,
            displayName = _uiState.value.displayName,
            onSuccess = {
                _uiState.update { it.copy(isLoading = false) }
                onSuccess()
            },
            onError = { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Sign-up failed") }
            }
        )
    }

    fun signOut() = repo.signOut()
}
