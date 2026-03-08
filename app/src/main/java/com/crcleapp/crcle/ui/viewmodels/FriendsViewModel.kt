package com.crcleapp.crcle.ui.viewmodels

import android.util.Log
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
    private val TAG = "FriendsViewModel"

    init {
        viewModelScope.launch {
            repository.listenToFriends().collectLatest { friendUids ->
                Log.d(TAG, "listenToFriends: received ${friendUids.size} uids")
                repository.getUsers(friendUids, { friends ->
                    _uiState.update { it.copy(friends = friends) }
                }, {
                    Log.e(TAG, "Error fetching friends profiles", it)
                })
            }
        }

        viewModelScope.launch {
            repository.listenToIncomingRequests().collectLatest { requests ->
                Log.d(TAG, "listenToIncomingRequests: received ${requests.size} requests")
                val senderUids = requests.map { it.senderUid }.filter { it.isNotEmpty() }
                
                if (senderUids.isEmpty()) {
                    _uiState.update { it.copy(incomingRequests = emptyList()) }
                    return@collectLatest
                }

                repository.getUsers(senderUids, { senders ->
                    Log.d(TAG, "Incoming mapping: found ${senders.size} profiles for ${senderUids.size} uids")
                    val combined = requests.map { req ->
                        val user = senders.find { it.uid == req.senderUid } 
                            ?: UserProfile(uid = req.senderUid, username = "Unknown User", displayName = "Unknown User")
                        FriendRequestWithUser(req, user)
                    }
                    _uiState.update { it.copy(incomingRequests = combined) }
                }, {
                    Log.e(TAG, "Error fetching incoming request profiles", it)
                })
            }
        }

        viewModelScope.launch {
            repository.listenToOutgoingRequests().collectLatest { requests ->
                Log.d(TAG, "listenToOutgoingRequests: received ${requests.size} requests")
                val receiverUids = requests.map { it.receiverUid }.filter { it.isNotEmpty() }

                if (receiverUids.isEmpty()) {
                    _uiState.update { it.copy(outgoingRequests = emptyList()) }
                    return@collectLatest
                }

                repository.getUsers(receiverUids, { receivers ->
                    Log.d(TAG, "Outgoing mapping: found ${receivers.size} profiles for ${receiverUids.size} uids")
                    val combined = requests.map { req ->
                        val user = receivers.find { it.uid == req.receiverUid }
                            ?: UserProfile(uid = req.receiverUid, username = "Unknown User", displayName = "Unknown User")
                        FriendRequestWithUser(req, user)
                    }
                    _uiState.update { it.copy(outgoingRequests = combined) }
                }, {
                    Log.e(TAG, "Error fetching outgoing request profiles", it)
                })
            }
        }

        viewModelScope.launch {
            circleRepository.listenToCircleInvites().collectLatest { invites ->
                _uiState.update { it.copy(circleInvites = invites) }
            }
        }

        circleRepository.getUserCircles(
            onSuccess = { circles -> _uiState.update { it.copy(userCircles = circles) } },
            onError = { Log.e(TAG, "Error fetching user circles", it) }
        )
    }

    fun onSearchTextChange(text: String) {
        _uiState.update { it.copy(searchText = text) }
    }

    fun sendFriendRequest() {
        val username = _uiState.value.searchText
        if (username.isBlank()) return
        Log.d(TAG, "sendFriendRequest triggered for username: $username")
        _uiState.update { it.copy(isLoading = true) }
        repository.sendFriendRequest(username, {
            Log.d(TAG, "sendFriendRequest success")
            _uiState.update { it.copy(isLoading = false, message = "Successfully sent friend request", searchText = "") }
        }, { e ->
            Log.e(TAG, "sendFriendRequest error", e)
            _uiState.update { it.copy(isLoading = false, message = e.message) }
        }, {
            Log.d(TAG, "sendFriendRequest: user not found")
            _uiState.update { it.copy(isLoading = false, message = "No user found") }
        })
    }

    fun acceptRequest(req: FriendRequest) {
        Log.d(TAG, "acceptRequest: ${req.id}")
        repository.acceptFriendRequest(req, {
            Log.d(TAG, "acceptRequest success")
        }, {
            Log.e(TAG, "acceptRequest failure", it)
        })
    }

    fun declineRequest(reqId: String) {
        repository.declineFriendRequest(reqId, {}, {})
    }

    fun cancelRequest(reqId: String) {
        repository.cancelFriendRequest(reqId, {}, {})
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
            onError = { e: Exception ->
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
