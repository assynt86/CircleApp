package com.crcleapp.crcle.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.crcleapp.crcle.data.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * AuthUiState:
 * Stores input fields + loading + error message + current mode.
 */
data class AuthUiState(
    val identifier: String = "", // Used for login (email, username, or phone)
    val email: String = "",      // Used for signup
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

    fun updateIdentifier(v: String) { _uiState.update { it.copy(identifier = v, error = "") } }
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

    private fun validateLogin(): Boolean {
        val state = _uiState.value
        if (state.identifier.isBlank()) {
            _uiState.update { it.copy(error = "Please enter your email, username, or phone") }
            return false
        }
        if (state.password.isBlank()) {
            _uiState.update { it.copy(error = "Please enter your password") }
            return false
        }
        return true
    }

    private fun validateSignUp(): Boolean {
        val state = _uiState.value
        if (state.email.isBlank()) {
            _uiState.update { it.copy(error = "Email cannot be empty") }
            return false
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
            _uiState.update { it.copy(error = "Please enter a valid email address") }
            return false
        }
        if (state.password.isBlank()) {
            _uiState.update { it.copy(error = "Password cannot be empty") }
            return false
        }
        if (state.password.length < 6) {
            _uiState.update { it.copy(error = "Password must be at least 6 characters") }
            return false
        }
        if (state.displayName.isBlank()) {
            _uiState.update { it.copy(error = "Name cannot be empty") }
            return false
        }
        if (state.username.isBlank()) {
            _uiState.update { it.copy(error = "Username cannot be empty") }
            return false
        }
        if (state.phone.isBlank()) {
            _uiState.update { it.copy(error = "Phone number cannot be empty") }
            return false
        }
        return true
    }

    fun login(onSuccess: () -> Unit) {
        if (!validateLogin()) return
        
        _uiState.update { it.copy(isLoading = true, error = "") }
        repo.login(
            identifier = _uiState.value.identifier,
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
        if (!validateSignUp()) return

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
