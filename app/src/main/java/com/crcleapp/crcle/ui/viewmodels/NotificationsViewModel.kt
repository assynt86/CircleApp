package com.crcleapp.crcle.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.crcleapp.crcle.data.NotificationLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class NotificationItem(
    val log: NotificationLog,
    val imageUrl: String? = null,
    val isVirtual: Boolean = false
)

data class NotificationsUiState(
    val notifications: List<NotificationItem> = emptyList(),
    val isLoading: Boolean = false
)

class NotificationsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val logsFlow = MutableStateFlow<List<NotificationLog>>(emptyList())
    private val requestsFlow = MutableStateFlow<List<NotificationLog>>(emptyList())
    private val invitesFlow = MutableStateFlow<List<NotificationLog>>(emptyList())

    init {
        startListeners()
        observeAllSources()
    }

    private fun startListeners() {
        val uid = auth.currentUser?.uid ?: return
        _uiState.value = _uiState.value.copy(isLoading = true)

        // 1. Historical Logs
        db.collection("users").document(uid).collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    logsFlow.value = snapshot.toObjects(NotificationLog::class.java)
                }
            }

        // 2. Live Pending Friend Requests
        db.collection("friend_requests")
            .whereEqualTo("receiverUid", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    requestsFlow.value = snapshot.documents.map { doc ->
                        NotificationLog(
                            id = doc.id,
                            title = "Friend Request",
                            body = "Pending request",
                            type = "friend_request",
                            senderUid = doc.getString("senderUid"),
                            timestamp = doc.getTimestamp("timestamp")
                        )
                    }
                }
            }

        // 3. Live Pending Circle Invites
        db.collection("circle_invites")
            .whereEqualTo("inviteeUid", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    invitesFlow.value = snapshot.documents.map { doc ->
                        NotificationLog(
                            id = doc.id,
                            title = "Circle Invite",
                            body = "Pending invite",
                            type = "circle_invite",
                            circleId = doc.getString("circleId"),
                            senderUid = doc.getString("inviterUid"),
                            timestamp = doc.getTimestamp("timestamp")
                        )
                    }
                }
            }
    }

    private fun observeAllSources() {
        viewModelScope.launch {
            combine(logsFlow, requestsFlow, invitesFlow) { logs, requests, invites ->
                // To avoid duplicates, we filter out logs that are of type "friend_request" or "circle_invite"
                // and instead rely on the "live" collections for those.
                // This ensures that even if a log is deleted, the live request stays visible.
                val filteredLogs = logs.filter { it.type != "friend_request" && it.type != "circle_invite" }
                
                val combined = (filteredLogs.map { it to false } + 
                                requests.map { it to true } + 
                                invites.map { it to true })
                    .sortedByDescending { it.first.timestamp?.seconds ?: Long.MAX_VALUE }
                
                combined
            }.collect { combinedList ->
                enrichLogs(combinedList)
            }
        }
    }

    private suspend fun enrichLogs(items: List<Pair<NotificationLog, Boolean>>) {
        val enriched = items.map { (log, isVirtual) ->
            var url: String? = null
            var title = log.title
            var body = log.body

            try {
                if (log.type == "friend_request" && log.senderUid != null) {
                    val uDoc = db.collection("user_public").document(log.senderUid).get().await()
                    val username = uDoc.getString("username") ?: "Someone"
                    title = "Friend Request"
                    body = "$username sent you a friend request"
                    url = uDoc.getString("photoUrl")
                } else if (log.type == "circle_invite" && log.circleId != null) {
                    val cDoc = db.collection("circles").document(log.circleId).get().await()
                    val cName = cDoc.getString("name") ?: "Unknown Circle"
                    title = "Circle Invite"
                    body = "Invited to join $cName"
                    url = cDoc.getString("backgroundUrl")
                } else {
                    // Standard enrichment for other types
                    if (log.senderUid != null) {
                        val uDoc = db.collection("user_public").document(log.senderUid).get().await()
                        url = uDoc.getString("photoUrl")
                    } else if (log.circleId != null) {
                        val cDoc = db.collection("circles").document(log.circleId).get().await()
                        url = cDoc.getString("backgroundUrl")
                    }
                }
            } catch (e: Exception) {
                // Ignore errors during enrichment
            }
            NotificationItem(log.copy(title = title, body = body), url, isVirtual)
        }
        _uiState.value = NotificationsUiState(notifications = enriched, isLoading = false)
    }

    fun deleteNotification(notificationId: String) {
        val uid = auth.currentUser?.uid ?: return
        // This will only succeed if the ID is a real log document. 
        // Virtual items (requests/invites) will not be deleted from their source here.
        db.collection("users").document(uid).collection("notifications").document(notificationId)
            .delete()
            .addOnSuccessListener {
                Log.d("NotificationsVM", "Log deleted: $notificationId")
            }
    }
}
