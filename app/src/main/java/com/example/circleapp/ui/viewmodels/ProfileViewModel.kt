package com.example.circleapp.ui.viewmodels

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.circleapp.data.SavedPhotosStore
import com.example.circleapp.data.UserRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UserRepository()
    private val savedPhotosStore = SavedPhotosStore(application)

    val name = mutableStateOf("")
    val username = mutableStateOf("")
    val email = mutableStateOf("")
    val phone = mutableStateOf("")
    val isAutoSaveEnabled = mutableStateOf(false)

    init {
        loadCurrentUser()
        viewModelScope.launch {
            savedPhotosStore.autoSaveFlow.collectLatest { enabled ->
                isAutoSaveEnabled.value = enabled
            }
        }
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            val user = repository.getCurrentUser()
            user?.let {
                name.value = it.displayName
                username.value = it.username
                email.value = it.email
                phone.value = it.phone
            }
        }
    }

    fun toggleAutoSave(enabled: Boolean) {
        viewModelScope.launch {
            savedPhotosStore.setAutoSave(enabled)
        }
    }
}
