package com.example.circleapp.ui.viewmodels

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class CircleViewModelFactory(private val application: Application, private val circleId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CircleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CircleViewModel(application, circleId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}