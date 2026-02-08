package com.example.circleapp.ui.viewmodels

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.circleapp.data.UserRepository
import kotlinx.coroutines.launch

class ProfileViewModel : ViewModel() {
    private val repository = UserRepository()

    val name = mutableStateOf("")
    val username = mutableStateOf("")
    val email = mutableStateOf("")
    val phone = mutableStateOf("")

    init {
        loadCurrentUser()
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
}
