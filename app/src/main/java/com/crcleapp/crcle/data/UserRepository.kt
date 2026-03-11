package com.crcleapp.crcle.data

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class UserRepository {

    companion object {
        private const val UNIQUE_USER_FIELDS_COLLECTION = "unique_user_fields"
        private const val TYPE_EMAIL = "email"
        private const val TYPE_PHONE = "phone"
        private const val TYPE_USERNAME = "username"
    }

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    suspend fun getCurrentUser(): UserProfile? {
        val firebaseUser = auth.currentUser ?: return null
        val document = db.collection("users").document(firebaseUser.uid).get().await()
        return document.toObject(UserProfile::class.java)
    }

    suspend fun updateProfilePicture(uid: String, photoUrl: String) {
        val batch = db.batch()
        val userRef = db.collection("users").document(uid)
        val publicRef = db.collection("user_public").document(uid)

        batch.update(userRef, "photoUrl", photoUrl)
        batch.update(publicRef, "photoUrl", photoUrl)

        batch.commit().await()
    }

    suspend fun updateDisplayName(uid: String, displayName: String) {
        val batch = db.batch()
        val userRef = db.collection("users").document(uid)
        val publicRef = db.collection("user_public").document(uid)

        batch.update(userRef, "displayName", displayName)
        batch.update(publicRef, "displayName", displayName)

        batch.commit().await()
    }

    suspend fun setAutoAcceptInvites(uid: String, enabled: Boolean) {
        db.collection("users").document(uid).update("autoAcceptInvites", enabled).await()
    }

    suspend fun updateFcmToken(uid: String, token: String) {
        db.collection("users").document(uid).update("fcmToken", token).await()
    }

    suspend fun reportBug(description: String) {
        val uid = auth.currentUser?.uid ?: "anonymous"
        val device = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (API ${android.os.Build.VERSION.SDK_INT})"
        val report = hashMapOf(
            "userId" to uid,
            "device" to device,
            "description" to description,
            "timestamp" to FieldValue.serverTimestamp()
        )
        db.collection("bugs").add(report).await()
    }

    suspend fun deleteAccount(password: String) {
        val user = auth.currentUser ?: throw Exception("User not signed in")
        val email = user.email ?: throw Exception("User email not found")

        // 1. Re-authenticate
        val credential = EmailAuthProvider.getCredential(email, password)
        user.reauthenticate(credential).await()

        val uid = user.uid
        val userDoc = db.collection("users").document(uid).get().await()
        val normalizedEmail = userDoc.getString("normalizedEmail") ?: normalizeEmail(email)
        val normalizedUsername = userDoc.getString("normalizedUsername") ?: normalizeUsername(userDoc.getString("username").orEmpty())
        val normalizedPhone = userDoc.getString("normalizedPhone") ?: normalizePhone(userDoc.getString("phone").orEmpty())

        // 2. Cleanup User Data
        val batch = db.batch()
        batch.delete(db.collection("users").document(uid))
        batch.delete(db.collection("user_public").document(uid))
        if (normalizedEmail.isNotBlank()) batch.delete(uniqueFieldRef(TYPE_EMAIL, normalizedEmail))
        if (normalizedUsername.isNotBlank()) batch.delete(uniqueFieldRef(TYPE_USERNAME, normalizedUsername))
        if (normalizedPhone.isNotBlank()) batch.delete(uniqueFieldRef(TYPE_PHONE, normalizedPhone))
        batch.commit().await()

        // 3. Delete Auth Account
        user.delete().await()
    }

    private fun uniqueFieldRef(type: String, normalizedValue: String) =
        db.collection(UNIQUE_USER_FIELDS_COLLECTION).document("${type}_$normalizedValue")

    private fun normalizeEmail(email: String): String = email.trim().lowercase()

    private fun normalizeUsername(username: String): String = username.trim().lowercase()

    private fun normalizePhone(phone: String): String = phone.filter { it.isDigit() }
}
