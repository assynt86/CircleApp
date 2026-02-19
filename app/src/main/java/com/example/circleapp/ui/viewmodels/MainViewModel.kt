package com.example.circleapp.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.circleapp.data.*
import com.example.circleapp.util.NotificationManagerService
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val themePreferences = ThemePreferences(application)
    private val repository = CircleRepository()
    private val friendsRepository = FriendsRepository()
    private val notificationService = NotificationManagerService(application)
    private val auth = FirebaseAuth.getInstance()
    
    val useSystemTheme: Flow<Boolean> = themePreferences.useSystemThemeFlow
    val isDarkMode: Flow<Boolean> = themePreferences.isDarkModeFlow

    private val monitoredCircles = mutableSetOf<String>()
    private val circleMembersMap = mutableMapOf<String, List<String>>()
    private val notifiedMassUploads = mutableSetOf<String>()
    private val notifiedInvites = mutableSetOf<String>()
    private val notifiedFriendRequests = mutableSetOf<String>()
    private val notifiedDeadlines = mutableSetOf<String>()

    init {
        observeAuthAndStartListening()
    }

    private fun observeAuthAndStartListening() {
        auth.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                startGlobalListeners()
            } else {
                monitoredCircles.clear()
                circleMembersMap.clear()
                notifiedMassUploads.clear()
                notifiedInvites.clear()
                notifiedFriendRequests.clear()
                notifiedDeadlines.clear()
            }
        }
    }

    private fun startGlobalListeners() {
        val currentUid = auth.currentUser?.uid ?: return

        // 1. Listen for Circle Invites
        viewModelScope.launch {
            repository.listenToCircleInvites().collectLatest { invites ->
                invites.forEach { invite ->
                    if (!notifiedInvites.contains(invite.id)) {
                        notifiedInvites.add(invite.id)
                        notificationService.notifyCircleInvite(invite.circleName)
                    }
                }
            }
        }

        // 2. Listen for Friend Requests
        viewModelScope.launch {
            friendsRepository.listenToIncomingRequests().collectLatest { requests ->
                requests.forEach { request ->
                    if (!notifiedFriendRequests.contains(request.id)) {
                        notifiedFriendRequests.add(request.id)
                        friendsRepository.getUsers(listOf(request.senderUid), { users ->
                            users.firstOrNull()?.let {
                                notificationService.notifyFriendRequest(it.username)
                            }
                        }, {})
                    }
                }
            }
        }

        // 3. Listen to all joined circles
        viewModelScope.launch {
            repository.getUserCircles({ circles ->
                circles.forEach { circle ->
                    // New member check
                    val oldMembers = circleMembersMap[circle.id]
                    if (oldMembers != null && circle.members.size > oldMembers.size) {
                        val newMemberUid = circle.members.filter { it !in oldMembers }.firstOrNull()
                        if (newMemberUid != null && newMemberUid != currentUid) {
                            friendsRepository.getUsers(listOf(newMemberUid), { users ->
                                users.firstOrNull()?.let {
                                    notificationService.notifyMemberJoined(circle.name, it.username)
                                }
                            }, {})
                        }
                    }
                    circleMembersMap[circle.id] = circle.members

                    // Start detailed monitoring if not already doing so
                    if (!monitoredCircles.contains(circle.id)) {
                        monitoredCircles.add(circle.id)
                        listenToCirclePhotos(circle)
                        checkCircleDeadlines(circle)
                    }
                }
            }, {})
        }
    }

    private fun listenToCirclePhotos(circle: Circle) {
        repository.listenToPhotos(circle.id, { photos ->
            detectMassUpload(circle.id, photos)
        }, {})
    }

    private fun detectMassUpload(circleId: String, photos: List<PhotoItem>) {
        val currentUid = auth.currentUser?.uid ?: return
        val now = System.currentTimeMillis()
        val tenMinutesAgo = now - TimeUnit.MINUTES.toMillis(10)
        
        val recentPhotos = photos.filter { (it.createdAt?.toDate()?.time ?: 0) > tenMinutesAgo }
        val uploadsByUser = recentPhotos.groupBy { it.uploaderUid }
        
        uploadsByUser.forEach { (uid, userPhotos) ->
            if (uid != currentUid && userPhotos.size >= 20) {
                val burstId = "${circleId}_${uid}_${userPhotos.size / 20}"
                if (!notifiedMassUploads.contains(burstId)) {
                    notifiedMassUploads.add(burstId)
                    friendsRepository.getUsers(listOf(uid), { users ->
                        users.firstOrNull()?.let {
                            notificationService.notifyMassUpload(it.username)
                        }
                    }, {})
                }
            }
        }
    }

    private fun checkCircleDeadlines(circle: Circle) {
        val now = System.currentTimeMillis()
        val twentyFourHours = TimeUnit.HOURS.toMillis(24)
        
        circle.closeAt?.toDate()?.time?.let { closeAt ->
            val diff = closeAt - now
            if (diff in 0..twentyFourHours) {
                val deadlineId = "${circle.id}_closing"
                if (!notifiedDeadlines.contains(deadlineId)) {
                    notifiedDeadlines.add(deadlineId)
                    notificationService.notifyCircleClosing(circle.name)
                }
            }
        }
        
        circle.deleteAt?.toDate()?.time?.let { deleteAt ->
            val diff = deleteAt - now
            if (diff in 0..twentyFourHours) {
                val deadlineId = "${circle.id}_deleting"
                if (!notifiedDeadlines.contains(deadlineId)) {
                    notifiedDeadlines.add(deadlineId)
                    notificationService.notifyCircleDeleting(circle.name)
                }
            }
        }
    }
}
