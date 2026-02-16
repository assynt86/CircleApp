package com.example.circleapp.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/**
 * AuthRepository:
 * - Sign up using Email/Password (Firebase Auth)
 * - Save user profile fields to Firestore: users/{uid}
 * - Login using Email/Password
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun isSignedIn(): Boolean = auth.currentUser != null
    fun currentUid(): String? = auth.currentUser?.uid
    fun signOut() = auth.signOut()

    fun signUpWithEmail(
        email: String,
        password: String,
        username: String,
        phone: String,
        displayName: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { res ->
                val uid = res.user?.uid
                    ?: return@addOnSuccessListener onError(IllegalStateException("Sign-up succeeded but uid is null"))

                // User profile document in Firestore
                val profile = hashMapOf(
                    "uid" to uid,
                    "email" to email.trim(),
                    "username" to username.trim(),
                    "phone" to phone.trim(),
                    "displayName" to displayName.trim(),
                    "photoUrl" to "",
                    "createdAt" to Timestamp.now()
                )

                db.collection("users").document(uid)
                    .set(profile)
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onError(e) }
            }
            .addOnFailureListener { e -> onError(e) }
    }

    fun loginWithEmail(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e) }
    }
}
