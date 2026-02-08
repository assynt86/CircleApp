package com.example.circleapp.ui.viewmodels

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class ProfileViewModel : ViewModel() {
    val name = mutableStateOf("John Doe")
    val username = mutableStateOf("johndoe")
    val email = mutableStateOf("john.doe@example.com")
    val phone = mutableStateOf("+1 123 456 7890")
}
