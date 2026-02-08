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
}