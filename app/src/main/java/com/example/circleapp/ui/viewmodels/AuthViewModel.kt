package com.example.circleapp.ui.viewmodels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.circleapp.data.AuthRepository

/**
 * AuthUiState:
 * Stores input fields + loading + error message.
 */
data class AuthUiState(
    val email: String = "",
    val password: String = "",
    val username: String = "",
    val phone: String = "",
    val displayName: String = "",
    val isLoading: Boolean = false,
    val error: String = ""
)

/**
 * AuthViewModel:
 * Owns auth UI state + triggers login/sign-up actions.
 */
class AuthViewModel(
    private val repo: AuthRepository = AuthRepository()
) : ViewModel() {

    var state by mutableStateOf(AuthUiState())
        private set

    fun isSignedIn(): Boolean = repo.isSignedIn()

    fun updateEmail(v: String) { state = state.copy(email = v, error = "") }
    fun updatePassword(v: String) { state = state.copy(password = v, error = "") }
    fun updateUsername(v: String) { state = state.copy(username = v, error = "") }
    fun updatePhone(v: String) { state = state.copy(phone = v, error = "") }
    fun updateDisplayName(v: String) { state = state.copy(displayName = v, error = "") }

    fun login(onSuccess: () -> Unit) {
        state = state.copy(isLoading = true, error = "")
        repo.loginWithEmail(
            email = state.email,
            password = state.password,
            onSuccess = {
                state = state.copy(isLoading = false)
                onSuccess()
            },
            onError = { e ->
                state = state.copy(isLoading = false, error = e.message ?: "Login failed")
            }
        )
    }

    fun signUp(onSuccess: () -> Unit) {
        state = state.copy(isLoading = true, error = "")
        repo.signUpWithEmail(
            email = state.email,
            password = state.password,
            username = state.username,
            phone = state.phone,
            displayName = state.displayName,
            onSuccess = {
                state = state.copy(isLoading = false)
                onSuccess()
            },
            onError = { e ->
                state = state.copy(isLoading = false, error = e.message ?: "Sign-up failed")
            }
        )
    }

    fun signOut() = repo.signOut()
}
