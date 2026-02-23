package com.crcleapp.crcle.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crcleapp.crcle.data.Circle
import com.crcleapp.crcle.data.CircleInvite
import com.crcleapp.crcle.data.CircleRepository
import com.crcleapp.crcle.data.FriendRequest
import com.crcleapp.crcle.data.FriendsRepository
import com.crcleapp.crcle.data.UserProfile
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FriendsUiState(
    val searchText: String = "",
    val friends: List<UserProfile> = emptyList(),
    val incomingRequests: List<FriendRequestWithUser> = emptyList(),
    val outgoingRequests: List<FriendRequestWithUser> = emptyList(),
    val circleInvites: List<CircleInvite> = emptyList(),
    val userCircles: List<Circle> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
    val selectedTab: Int = 0, // 0: Friends, 1: Pending, 2: Invites
    val showCircleSelector: UserProfile? = null,
    val showRemoveConfirmation: UserProfile? = null,
    val showBlockConfirmation: UserProfile? = null
)

data class FriendRequestWithUser(
    val request: FriendRequest,
    val user: UserProfile
)

class FriendsViewModel : ViewModel() {
    private val repository = FriendsRepository()
    private val circleRepository = CircleRepository()
    private val _uiState = MutableStateFlow(FriendsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.listenToFriends().collectLatest { friendUids ->
                repository.getUsers(friendUids, { friends ->
                    _uiState.update { it.copy(friends = friends) }
                }, {})
            }
        }

        viewModelScope.launch {
            repository.listenToIncomingRequests().collectLatest { requests ->
                val senderUids = requests.map { it.senderUid }
                repository.getUsers(senderUids, { senders ->
                    val combined = requests.mapNotNull { req ->
                        val user = senders.find { it.uid == req.senderUid } ?: return@mapNotNull null
                        FriendRequestWithUser(req, user)
                    }
                    _uiState.update { it.copy(incomingRequests = combined) }
                }, {})
            }
        }

        viewModelScope.launch {
            repository.listenToOutgoingRequests().collectLatest { requests ->
                val receiverUids = requests.map { it.receiverUid }
                repository.getUsers(receiverUids, { receivers ->
                    val combined = requests.mapNotNull { req ->
                        val user = receivers.find { it.uid == req.receiverUid } ?: return@mapNotNull null
                        FriendRequestWithUser(req, user)
                    }
                    _uiState.update { it.copy(outgoingRequests = combined) }
                }, {})
            }
        }

        viewModelScope.launch {
            circleRepository.listenToCircleInvites().collectLatest { invites ->
                _uiState.update { it.copy(circleInvites = invites) }
            }
        }

        circleRepository.getUserCircles(
            onSuccess = { circles -> _uiState.update { it.copy(userCircles = circles) } },
            onError = {}
        )
    }

    fun onSearchTextChange(text: String) {
        _uiState.update { it.copy(searchText = text) }
    }

    fun sendFriendRequest() {
        val username = _uiState.value.searchText
        if (username.isBlank()) return
        _uiState.update { it.copy(isLoading = true) }
        repository.sendFriendRequest(username, {
            _uiState.update { it.copy(isLoading = false, message = "Successfully sent friend request", searchText = "") }
        }, { e ->
            _uiState.update { it.copy(isLoading = false, message = e.message) }
        }, {
            _uiState.update { it.copy(isLoading = false, message = "No user found") }
        })
    }

    fun acceptRequest(req: FriendRequest) {
        repository.acceptFriendRequest(req, {}, {})
    }

    fun declineRequest(reqId: String) {
        repository.declineFriendRequest(reqId, {}, {})
    }

    fun removeFriend(uid: String) {
        repository.removeFriend(uid, {
            _uiState.update { it.copy(showRemoveConfirmation = null) }
        }, {})
    }

    fun blockUser(uid: String) {
        repository.blockUser(uid, {
            _uiState.update { it.copy(showBlockConfirmation = null, message = "User blocked") }
        }, {})
    }

    fun addFriendToCircle(friendUid: String, circleId: String) {
        repository.addFriendToCircle(friendUid, circleId, {
            _uiState.update { it.copy(showCircleSelector = null, message = "Added to circle") }
        }, { e ->
            _uiState.update { it.copy(message = e.message) }
        })
    }

    fun respondToCircleInvite(invite: CircleInvite, accept: Boolean) {
        circleRepository.respondToCircleInvite(
            inviteId = invite.id,
            circleId = invite.circleId,
            inviteeUid = invite.inviteeUid,
            accept = accept,
            onSuccess = {
                _uiState.update { it.copy(message = if (accept) "Joined circle" else "Invite declined") }
            },
            onError = { e ->
                _uiState.update { it.copy(message = e.message) }
            }
        )
    }

    fun setSelectedTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun setShowCircleSelector(user: UserProfile?) {
        _uiState.update { it.copy(showCircleSelector = user) }
    }

    fun setShowRemoveConfirmation(user: UserProfile?) {
        _uiState.update { it.copy(showRemoveConfirmation = user) }
    }

    fun setShowBlockConfirmation(user: UserProfile?) {
        _uiState.update { it.copy(showBlockConfirmation = user) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}