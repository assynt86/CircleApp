package com.example.circleapp.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.circleapp.data.CircleInfo
import com.example.circleapp.data.CircleRepository
import com.example.circleapp.data.FriendsRepository
import com.example.circleapp.data.UserProfile
import com.example.circleapp.data.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CircleSettingsUiState(
    val circleInfo: CircleInfo? = null,
    val members: List<UserProfile> = emptyList(),
    val friends: List<UserProfile> = emptyList(),
    val selectedFriendUids: Set<String> = emptySet(),
    val isAdmin: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val showDeleteConfirmation: Boolean = false,
    val showLeaveConfirmation: Boolean = false,
    val currentUserName: String = "",
    val selectedMember: UserProfile? = null,
    val showUserActionDialog: Boolean = false,
    val showReportDialog: Boolean = false
)

class CircleSettingsViewModel(
    application: Application,
    private val circleId: String
) : AndroidViewModel(application) {

    private val repository = CircleRepository()
    private val friendsRepository = FriendsRepository()
    private val userRepository = UserRepository()
    private val _uiState = MutableStateFlow(CircleSettingsUiState())
    val uiState: StateFlow<CircleSettingsUiState> = _uiState.asStateFlow()

    init {
        loadCircleData()
        loadFriends()
        loadCurrentUser()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            _uiState.update { it.copy(currentUserName = user?.displayName ?: user?.username ?: "Someone") }
        }
    }

    private fun loadCircleData() {
        _uiState.update { it.copy(isLoading = true) }
        
        repository.listenToCircle(circleId,
            onSuccess = { info ->
                val currentUserUid = repository.getCurrentUserUid()
                _uiState.update { it.copy(
                    circleInfo = info,
                    isAdmin = info.ownerUid == currentUserUid
                ) }
                loadMembers()
            },
            onError = { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        )
    }

    private fun loadMembers() {
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("circles").document(circleId)
            .get()
            .addOnSuccessListener { doc ->
                val memberUids = doc.get("members") as? List<String> ?: emptyList()
                repository.getCircleMembers(memberUids,
                    onSuccess = { members ->
                        _uiState.update { it.copy(isLoading = false, members = members) }
                    },
                    onError = { e ->
                        _uiState.update { it.copy(isLoading = false, error = e.message) }
                    }
                )
            }
            .addOnFailureListener { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
    }

    private fun loadFriends() {
        viewModelScope.launch {
            friendsRepository.listenToFriends().collectLatest { friendUids ->
                friendsRepository.getUsers(friendUids, { friends ->
                    _uiState.update { it.copy(friends = friends) }
                }, {})
            }
        }
    }

    fun onMemberSelected(member: UserProfile) {
        _uiState.update { it.copy(selectedMember = member, showUserActionDialog = true) }
    }

    fun dismissUserActionDialog() {
        _uiState.update { it.copy(selectedMember = null, showUserActionDialog = false) }
    }

    fun onReportSelected() {
        _uiState.update { it.copy(showUserActionDialog = false, showReportDialog = true) }
    }

    fun dismissReportDialog() {
        _uiState.update { it.copy(showReportDialog = false, selectedMember = null) }
    }

    fun reportUser(reason: String) {
        viewModelScope.launch {
            val reporter = userRepository.getCurrentUser() ?: return@launch
            val reported = _uiState.value.selectedMember ?: return@launch

            repository.reportUser(reporter.uid, reported.uid, reason, circleId,
                onSuccess = {
                    _uiState.update { it.copy(successMessage = "User reported", showReportDialog = false, selectedMember = null) }
                },
                onError = { e ->
                    _uiState.update { it.copy(error = e.message, showReportDialog = false, selectedMember = null) }
                }
            )
        }
    }

    fun blockSelectedUser() {
        val selectedUid = _uiState.value.selectedMember?.uid ?: return
        friendsRepository.blockUser(selectedUid, 
            onSuccess = { 
                 _uiState.update { it.copy(successMessage = "User blocked", showUserActionDialog = false, selectedMember = null) }
            },
            onError = { e -> 
                _uiState.update { it.copy(error = e.message, showUserActionDialog = false, selectedMember = null) }
            }
        )
    }

    fun sendFriendRequestToSelectedUser() {
        val username = _uiState.value.selectedMember?.username ?: return
        friendsRepository.sendFriendRequest(username, 
            onSuccess = {
                _uiState.update { it.copy(successMessage = "Friend request sent", showUserActionDialog = false, selectedMember = null) }
            },
            onError = { e ->
                _uiState.update { it.copy(error = e.message, showUserActionDialog = false, selectedMember = null) }
            },
            onNotFound = {
                _uiState.update { it.copy(error = "User not found", showUserActionDialog = false, selectedMember = null) }
            }
        )
    }

    fun toggleFriendSelection(uid: String) {
        _uiState.update { state ->
            val newSelection = if (state.selectedFriendUids.contains(uid)) {
                state.selectedFriendUids - uid
            } else {
                state.selectedFriendUids + uid
            }
            state.copy(selectedFriendUids = newSelection)
        }
    }

    fun updateCircleName(newName: String) {
        if (newName.isBlank()) return
        _uiState.update { it.copy(isLoading = true) }
        repository.updateCircleName(circleId, newName,
            onSuccess = {
                _uiState.update { it.copy(isLoading = false, successMessage = "Name updated") }
            },
            onError = { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        )
    }

    fun updateBackground(uri: Uri) {
        _uiState.update { it.copy(isLoading = true) }
        repository.updateCircleBackground(circleId, uri,
            onSuccess = { url ->
                _uiState.update { it.copy(isLoading = false, successMessage = "Background updated") }
            },
            onError = { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        )
    }

    fun addMembers(typedUsername: String) {
        val selectedUids = _uiState.value.selectedFriendUids.toList()
        if (typedUsername.isBlank() && selectedUids.isEmpty()) return

        _uiState.update { it.copy(isLoading = true) }
        
        viewModelScope.launch {
            val currentUid = repository.getCurrentUserUid() ?: return@launch
            val circleInfo = _uiState.value.circleInfo ?: return@launch

            // 1. Invite selected friends (Direct add because they are friends)
            if (selectedUids.isNotEmpty()) {
                repository.addMembersByUids(circleId, selectedUids,
                    onSuccess = {
                        // Friends added, now handle typed username
                        handleTypedUsername(typedUsername, currentUid, circleInfo)
                    },
                    onError = { e ->
                        finalizeAdd(true, e.message)
                    }
                )
            } else {
                handleTypedUsername(typedUsername, currentUid, circleInfo)
            }
        }
    }

    private fun handleTypedUsername(typedUsername: String, currentUid: String, circleInfo: CircleInfo) {
        if (typedUsername.isNotBlank()) {
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users")
                .whereEqualTo("username", typedUsername.trim())
                .limit(1)
                .get()
                .addOnSuccessListener { snap ->
                    if (snap.isEmpty) {
                        finalizeAdd(true, "User '$typedUsername' not found")
                        return@addOnSuccessListener
                    }

                    val targetUser = snap.documents.first().toObject(UserProfile::class.java)
                    if (targetUser == null) {
                        finalizeAdd(true, "Error loading user")
                        return@addOnSuccessListener
                    }

                    val isFriend = _uiState.value.friends.any { it.uid == targetUser.uid }
                    
                    repository.sendCircleInvite(
                        circleId = circleId,
                        circleName = circleInfo.name,
                        circleBackgroundUrl = circleInfo.backgroundUrl,
                        inviterUid = currentUid,
                        inviterName = _uiState.value.currentUserName,
                        targetUser = targetUser,
                        isFriend = isFriend,
                        onSuccess = { finalizeAdd(false, null) },
                        onError = { e -> finalizeAdd(true, e.message) }
                    )
                }
                .addOnFailureListener { e -> finalizeAdd(true, e.message) }
        } else {
            finalizeAdd(false, null)
        }
    }

    private fun finalizeAdd(error: Boolean, message: String?) {
        _uiState.update { it.copy(
            isLoading = false, 
            error = if (error) message else null,
            successMessage = if (!error) "Invitations sent / Members added" else null,
            selectedFriendUids = emptySet()
        ) }
        loadMembers()
    }

    fun kickMember(memberUid: String) {
        _uiState.update { it.copy(isLoading = true) }
        repository.kickMember(circleId, memberUid,
            onSuccess = {
                _uiState.update { it.copy(isLoading = false, successMessage = "Member removed") }
                loadMembers()
            },
            onError = { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        )
    }

    fun leaveCircle(onSuccess: () -> Unit) {
        val currentUid = repository.getCurrentUserUid() ?: return
        _uiState.update { it.copy(isLoading = true) }
        repository.kickMember(circleId, currentUid,
            onSuccess = {
                _uiState.update { it.copy(isLoading = false) }
                onSuccess()
            },
            onError = { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        )
    }

    fun deleteCircle(onDeleted: () -> Unit) {
        _uiState.update { it.copy(isLoading = true) }
        repository.deleteCircle(circleId,
            onSuccess = {
                onDeleted()
            },
            onError = { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        )
    }

    fun setShowDeleteConfirmation(show: Boolean) {
        _uiState.update { it.copy(showDeleteConfirmation = show) }
    }

    fun setShowLeaveConfirmation(show: Boolean) {
        _uiState.update { it.copy(showLeaveConfirmation = show) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}

class CircleSettingsViewModelFactory(
    private val application: Application,
    private val circleId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CircleSettingsViewModel(application, circleId) as T
    }
}
