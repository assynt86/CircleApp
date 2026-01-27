package com.example.circleapp.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage


class CircleRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Creates a circle with:
     * - closeAt = now + closeDurationMinutes
     * - deleteAt = closeAt + 48 hours
     * - cleanedUp = false (for your scheduled Cloud Function)
     */
    fun createCircle(
        circleName: String,
        closeDurationMinutes: Long = 30, // For testing. Later you can make this user-chosen.
        onSuccess: (circleId: String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: run {
            onError(IllegalStateException("User not signed in"))
            return
        }

        val nowMillis = System.currentTimeMillis()
        val closeAtMillis = nowMillis + closeDurationMinutes * 60_000
        val deleteAtMillis = closeAtMillis + 48L * 60L * 60L * 1000L // +48 hours

        val inviteCode = generateInviteCode()

        val circleData = hashMapOf(
            "name" to circleName,
            "ownerUid" to uid,
            "inviteCode" to inviteCode,
            "members" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp(),
            "closeAt" to Timestamp(closeAtMillis / 1000, 0),
            "deleteAt" to Timestamp(deleteAtMillis / 1000, 0),
            "cleanedUp" to false,
            "status" to "open"
        )

        db.collection("circles")
            .add(circleData)
            .addOnSuccessListener { docRef ->
                onSuccess(docRef.id)
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

    /**
     * Join a circle by invite code.
     * This finds the circle and adds your uid to members[].
     */
    fun joinCircleByInviteCode(
        inviteCode: String,
        onSuccess: (circleId: String) -> Unit,
        onNotFound: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: run {
            onError(IllegalStateException("User not signed in"))
            return
        }

        db.collection("circles")
            .whereEqualTo("inviteCode", inviteCode.trim())
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    onNotFound()
                    return@addOnSuccessListener
                }

                val circleDoc = snap.documents.first()
                val circleId = circleDoc.id

                // Add uid to members array (only adds if not already present)
                db.collection("circles")
                    .document(circleId)
                    .update("members", FieldValue.arrayUnion(uid))
                    .addOnSuccessListener {
                        onSuccess(circleId)
                    }
                    .addOnFailureListener { e -> onError(e) }
            }
            .addOnFailureListener { e -> onError(e) }
    }

    /**
     * Uploads a photo (Uri) to Firebase Storage, then writes metadata to Firestore.
     *
     * Storage path: circles/{circleId}/{photoId}.jpg
     * Firestore path: circles/{circleId}/photos/{photoId}
     */
    fun uploadPhotoToCircle(
        circleId: String,
        photoUri: Uri,
        onSuccess: (photoId: String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: run {
            onError(IllegalStateException("User not signed in"))
            return
        }

        val photoId = db.collection("tmp").document().id  // quick way to generate unique id
        val storagePath = "circles/$circleId/$photoId.jpg"

        val storageRef = FirebaseStorage.getInstance().reference.child(storagePath)

        // 1) Upload file
        storageRef.putFile(photoUri)
            .addOnSuccessListener {
                // 2) Write Firestore metadata
                val photoData = hashMapOf(
                    "uploaderUid" to uid,
                    "storagePath" to storagePath,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                db.collection("circles")
                    .document(circleId)
                    .collection("photos")
                    .document(photoId)
                    .set(photoData)
                    .addOnSuccessListener {
                        onSuccess(photoId)
                    }
                    .addOnFailureListener { e -> onError(e) }
            }
            .addOnFailureListener { e -> onError(e) }
    }


    private fun generateInviteCode(length: Int = 6): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // avoids confusing I/1/O/0
        return (1..length)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }
}
