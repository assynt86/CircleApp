package com.example.circleapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.circleapp.data.Circle
import com.example.circleapp.data.CircleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val _circles = MutableStateFlow<List<Circle>>(emptyList())
    val circles: StateFlow<List<Circle>> = _circles

    private val repository = CircleRepository()

    init {
        loadUserCircles()
    }

    private fun loadUserCircles() {
        viewModelScope.launch {
            repository.getUserCircles(
                onSuccess = { circleList ->
                    _circles.value = circleList
                },
                onError = { /* Handle error */ }
            )
        }
    }
}