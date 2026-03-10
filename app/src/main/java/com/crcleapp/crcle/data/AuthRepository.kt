package com.crcleapp.crcle.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * AuthRepository:
 * - Sign up using Email/Password (Firebase Auth)
 * - Save user profile fields to Firestore: users/{uid} and user_public/{uid}
 * - Login using Email, Username or Phone
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    fun isSignedIn(): Boolean = auth.currentUser != null
    fun currentUid(): String? = auth.currentUser?.uid
    fun signOut() = auth.signOut()

    suspend fun isEmailTaken(email: String): Boolean {
        val snapshot = db.collection("users")
            .whereEqualTo("email", email.trim())
            .limit(1)
            .get()
            .await()
        return !snapshot.isEmpty
    }

    suspend fun isUsernameTaken(username: String): Boolean {
        val snapshot = db.collection("users")
            .whereEqualTo("username", username.trim())
            .limit(1)
            .get()
            .await()
        return !snapshot.isEmpty
    }

    suspend fun isPhoneTaken(phone: String): Boolean {
        val snapshot = db.collection("users")
            .whereEqualTo("phone", phone.trim())
            .limit(1)
            .get()
            .await()
        return !snapshot.isEmpty
    }

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

                val batch = db.batch()

                // Private user profile document
                val privateProfile = hashMapOf(
                    "uid" to uid,
                    "email" to email.trim(),
                    "username" to username.trim(),
                    "phone" to phone.trim(),
                    "displayName" to displayName.trim(),
                    "photoUrl" to "",
                    "createdAt" to Timestamp.now(),
                    "autoAcceptInvites" to false
                )

                // Public user profile document
                val publicProfile = hashMapOf(
                    "uid" to uid,
                    "username" to username.trim(),
                    "displayName" to displayName.trim(),
                    "photoUrl" to ""
                )

                val userRef = db.collection("users").document(uid)
                val publicRef = db.collection("user_public").document(uid)

                batch.set(userRef, privateProfile)
                batch.set(publicRef, publicProfile)

                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onError(e) }
            }
            .addOnFailureListener { e -> onError(e) }
    }

    fun login(
        identifier: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val email = identifier.trim()

        // Check if identifier is email, if not find email by username/phone
        if (android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            loginWithEmail(email, password, onSuccess, onError)
        } else {
            // Try as username
            findEmailAndLogin("username", email, password, onSuccess) {
                // If username fails, try as phone
                findEmailAndLogin("phone", email, password, onSuccess, onError)
            }
        }
    }

    private fun loginWithEmail(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { e -> onError(e) }
    }

    private fun findEmailAndLogin(
        field: String,
        value: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection("users")
            .whereEqualTo(field, value)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val email = documents.documents[0].getString("email")
                    if (email != null) {
                        loginWithEmail(email, password, onSuccess, onError)
                    } else {
                        onError(Exception("User found but email is missing"))
                    }
                } else {
                    onError(Exception("No account found with this $field"))
                }
            }
            .addOnFailureListener { e -> onError(e) }
    }
}
