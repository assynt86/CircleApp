package com.crcleapp.crcle.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException

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
    companion object {
        private const val UNIQUE_USER_FIELDS_COLLECTION = "unique_user_fields"
        private const val TYPE_EMAIL = "email"
        private const val TYPE_PHONE = "phone"
        private const val TYPE_USERNAME = "username"
    }

    class DuplicateFieldException(message: String) : IllegalStateException(message)

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
        val trimmedEmail = email.trim()
        val trimmedUsername = username.trim()
        val trimmedPhone = phone.trim()
        val trimmedDisplayName = displayName.trim()

        val normalizedEmail = normalizeEmail(trimmedEmail)
        val normalizedUsername = normalizeUsername(trimmedUsername)
        val normalizedPhone = normalizePhone(trimmedPhone)

        auth.createUserWithEmailAndPassword(trimmedEmail, password)
            .addOnSuccessListener { res ->
                val firebaseUser = res.user
                val uid = firebaseUser?.uid
                    ?: return@addOnSuccessListener onError(IllegalStateException("Sign-up succeeded but uid is null"))

                val now = Timestamp.now()
                val privateProfile = hashMapOf(
                    "uid" to uid,
                    "email" to trimmedEmail,
                    "username" to trimmedUsername,
                    "phone" to trimmedPhone,
                    "displayName" to trimmedDisplayName,
                    "photoUrl" to "",
                    "createdAt" to now,
                    "autoAcceptInvites" to false,
                    "normalizedEmail" to normalizedEmail,
                    "normalizedUsername" to normalizedUsername,
                    "normalizedPhone" to normalizedPhone
                )

                val publicProfile = hashMapOf(
                    "uid" to uid,
                    "username" to trimmedUsername,
                    "displayName" to trimmedDisplayName,
                    "photoUrl" to ""
                )

                val userRef = db.collection("users").document(uid)
                val publicRef = db.collection("user_public").document(uid)
                val emailRef = uniqueFieldRef(TYPE_EMAIL, normalizedEmail)
                val usernameRef = uniqueFieldRef(TYPE_USERNAME, normalizedUsername)
                val phoneRef = uniqueFieldRef(TYPE_PHONE, normalizedPhone)

                val uniqueFieldDocs = listOf(
                    emailRef to uniqueFieldDoc(uid, TYPE_EMAIL, normalizedEmail, now),
                    usernameRef to uniqueFieldDoc(uid, TYPE_USERNAME, normalizedUsername, now),
                    phoneRef to uniqueFieldDoc(uid, TYPE_PHONE, normalizedPhone, now)
                )

                db.runTransaction { transaction ->
                    uniqueFieldDocs.forEach { (ref, doc) ->
                        if (transaction.get(ref).exists()) {
                            throw duplicateFieldException(doc["type"] as String)
                        }
                    }

                    transaction.set(userRef, privateProfile)
                    transaction.set(publicRef, publicProfile)
                    uniqueFieldDocs.forEach { (ref, doc) ->
                        transaction.set(ref, doc)
                    }
                }
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e ->
                        rollbackIncompleteSignup(firebaseUser, mapSignUpException(e), onError)
                    }
            }
            .addOnFailureListener { e -> onError(mapSignUpException(e)) }
    }

    fun login(
        identifier: String,
        password: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val email = identifier.trim()

        // MVP: email/password only (no Firestore lookup before auth)
        loginWithEmail(email, password, onSuccess, onError)
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

    private fun uniqueFieldRef(type: String, normalizedValue: String) =
        db.collection(UNIQUE_USER_FIELDS_COLLECTION).document("${type}_$normalizedValue")

    private fun uniqueFieldDoc(
        uid: String,
        type: String,
        normalizedValue: String,
        createdAt: Timestamp
    ) = hashMapOf(
        "uid" to uid,
        "type" to type,
        "normalizedValue" to normalizedValue,
        "createdAt" to createdAt
    )

    private fun duplicateFieldException(type: String): DuplicateFieldException =
        when (type) {
            TYPE_USERNAME -> DuplicateFieldException("That username is already taken")
            TYPE_PHONE -> DuplicateFieldException("That phone number is already in use")
            TYPE_EMAIL -> DuplicateFieldException("That email is already in use")
            else -> DuplicateFieldException("That account detail is already in use")
        }

    private fun mapSignUpException(error: Exception): Exception {
        findDuplicateFieldException(error)?.let { return it }

        return when (error) {
            is FirebaseAuthUserCollisionException -> DuplicateFieldException("That email is already in use")
            is FirebaseAuthException -> when (error.errorCode) {
                "ERROR_EMAIL_ALREADY_IN_USE" -> DuplicateFieldException("That email is already in use")
                else -> error
            }
            is FirebaseFirestoreException -> error
            else -> error
        }
    }

    private fun findDuplicateFieldException(error: Throwable?): DuplicateFieldException? {
        var current = error
        while (current != null) {
            if (current is DuplicateFieldException) {
                return current
            }
            current = current.cause
        }
        return null
    }

    private fun rollbackIncompleteSignup(
        firebaseUser: FirebaseUser?,
        originalError: Exception,
        onError: (Exception) -> Unit
    ) {
        if (firebaseUser == null) {
            onError(originalError)
            return
        }

        firebaseUser.delete()
            .addOnCompleteListener {
                auth.signOut()
                onError(originalError)
            }
    }

    private fun normalizeEmail(email: String): String = email.trim().lowercase()

    private fun normalizeUsername(username: String): String = username.trim().lowercase()

    private fun normalizePhone(phone: String): String =
        phone.filter { it.isDigit() }
}

