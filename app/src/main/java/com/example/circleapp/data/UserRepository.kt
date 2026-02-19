package com.example.circleapp.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    suspend fun getCurrentUser(): UserProfile? {
        val firebaseUser = auth.currentUser ?: return null
        val document = db.collection("users").document(firebaseUser.uid).get().await()
        return document.toObject(UserProfile::class.java)
    }

    suspend fun updateProfilePicture(uid: String, photoUrl: String) {
        db.collection("users").document(uid).update("photoUrl", photoUrl).await()
    }

    suspend fun updateDisplayName(uid: String, displayName: String) {
        db.collection("users").document(uid).update("displayName", displayName).await()
    }

    suspend fun setAutoAcceptInvites(uid: String, enabled: Boolean) {
        db.collection("users").document(uid).update("autoAcceptInvites", enabled).await()
    }
}