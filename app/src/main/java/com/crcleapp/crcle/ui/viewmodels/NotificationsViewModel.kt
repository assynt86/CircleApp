package com.crcleapp.crcle.ui.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import com.crcleapp.crcle.data.NotificationLog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date

data class NotificationsUiState(
    val notifications: List<NotificationLog> = emptyList(),
    val isLoading: Boolean = false
)

class NotificationsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    init {
        listenToNotifications()
    }

    private fun listenToNotifications() {
        val uid = auth.currentUser?.uid ?: return
        _uiState.value = _uiState.value.copy(isLoading = true)

        // Path: users/{uid}/notifications
        db.collection("users").document(uid).collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("NotificationsVM", "Listen failed.", error)
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    Log.d("NotificationsVM", "Received ${snapshot.size()} notifications")
                    val logs = snapshot.toObjects(NotificationLog::class.java)
                    _uiState.value = NotificationsUiState(notifications = logs, isLoading = false)
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                }
            }
    }

    fun clearAllNotifications() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("notifications")
            .get()
            .addOnSuccessListener { snapshot ->
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit()
            }
    }

    fun sendManualTestPing() {
        val uid = auth.currentUser?.uid ?: return
        val logData = hashMapOf(
            "title" to "Manual Test Ping",
            "body" to "Triggered from the app at ${Date()}",
            "timestamp" to FieldValue.serverTimestamp(),
            "type" to "test_ping"
        )
        
        db.collection("users").document(uid).collection("notifications")
            .add(logData)
            .addOnSuccessListener {
                Log.d("NotificationsVM", "Manual ping added successfully")
            }
            .addOnFailureListener {
                Log.e("NotificationsVM", "Failed to add manual ping", it)
            }
    }
}
