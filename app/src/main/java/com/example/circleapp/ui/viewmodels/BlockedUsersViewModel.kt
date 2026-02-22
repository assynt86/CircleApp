package com.example.circleapp.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.circleapp.data.FriendsRepository
import com.example.circleapp.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BlockedUsersUiState(
    val blockedUsers: List<UserProfile> = emptyList(),
    val message: String? = null
)

class BlockedUsersViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(BlockedUsersUiState())
    val uiState: StateFlow<BlockedUsersUiState> = _uiState.asStateFlow()

    private val friendsRepository = FriendsRepository()

    init {
        loadBlockedUsers()
    }

    private fun loadBlockedUsers() {
        viewModelScope.launch {
            friendsRepository.listenToBlockedUsers().collectLatest { blockedUids ->
                friendsRepository.getUsers(blockedUids,
                    onSuccess = { blocked ->
                        _uiState.update { it.copy(blockedUsers = blocked) }
                    },
                    onError = {
                        _uiState.update { it.copy(message = "Failed to load blocked users.") }
                    }
                )
            }
        }
    }

    fun unblockUser(uid: String) {
        friendsRepository.unblockUser(uid, 
            onSuccess = { 
                _uiState.update { it.copy(message = "User unblocked") } 
            },
            onError = { 
                _uiState.update { it.copy(message = "Failed to unblock user") } 
            }
        )
    }
    
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
