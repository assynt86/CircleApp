package com.example.circleapp.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random
import android.net.Uri
import com.example.circleapp.data.CircleInfo
import com.example.circleapp.data.PhotoItem
import com.google.firebase.storage.FirebaseStorage

class CircleRepository {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun getCurrentUserUid(): String? = auth.currentUser?.uid

    /**
     * Creates a circle with:
     * - closeAt = now + durationDays
     * - deleteAt = closeAt + 48 hours
     * - cleanedUp = false (for your scheduled Cloud Function)
     */
    fun createCircle(
        circleName: String,
        durationDays: Int,
        onSuccess: (circleId: String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: run {
            onError(IllegalStateException("User not signed in"))
            return
        }

        val nowMillis = System.currentTimeMillis()
        val closeAtMillis = nowMillis + durationDays * 24L * 60L * 60L * 1000L
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

    fun addPhotoToCircle(
        photoUri: Uri,
        circleId: String,
        onResult: (isSuccess: Boolean) -> Unit
    ) {
        uploadPhotoToCircle(circleId, photoUri,
            onSuccess = { onResult(true) },
            onError = { onResult(false) }
        )
    }

    fun addPhotoToCircles(
        photoUri: Uri,
        circleIds: List<String>,
        onResult: (isSuccess: Boolean) -> Unit
    ) {
        if (circleIds.isEmpty()) {
            onResult(false)
            return
        }

        var completedCount = 0
        var successCount = 0
        val totalCircles = circleIds.size

        circleIds.forEach { circleId ->
            uploadPhotoToCircle(circleId, photoUri,
                onSuccess = {
                    completedCount++
                    successCount++
                    if (completedCount == totalCircles) {
                        onResult(successCount == totalCircles)
                    }
                },
                onError = {
                    completedCount++
                    if (completedCount == totalCircles) {
                        onResult(successCount == totalCircles)
                    }
                }
            )
        }
    }

    fun getUserCircles(
        onSuccess: (circles: List<Circle>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: run {
            onError(IllegalStateException("User not signed in"))
            return
        }

        // Removed .whereEqualTo("status", "open") to allow viewing closed circles
        db.collection("circles")
            .whereArrayContains("members", uid)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }

                if (snapshot != null) {
                    val circles = snapshot.toObjects(Circle::class.java)
                    onSuccess(circles)
                }
            }
    }

    fun listenToCircle(
        circleId: String,
        onSuccess: (CircleInfo) -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection("circles").document(circleId)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }
                if (snap != null && snap.exists()) {
                    val name = snap.getString("name") ?: ""
                    val invite = snap.getString("inviteCode") ?: ""
                    val status = snap.getString("status") ?: "open"
                    val closeAt = snap.getTimestamp("closeAt")
                    val deleteAt = snap.getTimestamp("deleteAt")
                    val ownerUid = snap.getString("ownerUid") ?: ""
                    val backgroundUrl = snap.getString("backgroundUrl")
                    onSuccess(CircleInfo(name, invite, status, closeAt, deleteAt, ownerUid, backgroundUrl))
                } else {
                    onError(Exception("Circle not found"))
                }
            }
    }

    fun listenToPhotos(
        circleId: String,
        onSuccess: (List<PhotoItem>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection("circles").document(circleId).collection("photos")
            .orderBy("createdAt")
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    onError(e)
                    return@addSnapshotListener
                }
                if (snap != null) {
                    val photoList = snap.documents.map { doc ->
                        PhotoItem(
                            id = doc.id,
                            uploaderUid = doc.getString("uploaderUid") ?: "",
                            storagePath = doc.getString("storagePath") ?: "",
                            createdAt = doc.getTimestamp("createdAt"),
                            downloadUrl = null
                        )
                    }
                    onSuccess(photoList)
                }
            }
    }

    fun getFirstPhoto(
        circleId: String,
        onSuccess: (PhotoItem?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection("circles").document(circleId).collection("photos")
            .orderBy("createdAt")
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    onSuccess(null)
                } else {
                    val doc = snap.documents.first()
                    onSuccess(PhotoItem(
                        id = doc.id,
                        uploaderUid = doc.getString("uploaderUid") ?: "",
                        storagePath = doc.getString("storagePath") ?: "",
                        createdAt = doc.getTimestamp("createdAt"),
                        downloadUrl = null
                    ))
                }
            }
            .addOnFailureListener { onError(it) }
    }

    fun getDownloadUrl(storagePath: String, onResult: (String?) -> Unit) {
        if (storagePath.isBlank()) {
            onResult(null)
            return
        }
        FirebaseStorage.getInstance().reference.child(storagePath).downloadUrl
            .addOnSuccessListener { uri -> onResult(uri.toString()) }
            .addOnFailureListener { onResult(null) }
    }

    fun deletePhoto(
        circleId: String,
        photoId: String,
        storagePath: String,
        onResult: (Boolean) -> Unit
    ) {
        // 1) Delete from Storage
        FirebaseStorage.getInstance().reference.child(storagePath).delete()
            .addOnCompleteListener { storageTask ->
                // Even if storage fails (e.g. file already gone), try to delete Firestore doc
                // 2) Delete from Firestore
                db.collection("circles")
                    .document(circleId)
                    .collection("photos")
                    .document(photoId)
                    .delete()
                    .addOnCompleteListener { firestoreTask ->
                        onResult(firestoreTask.isSuccessful)
                    }
            }
    }

    /**
     * Get details of all members of a circle.
     */
    fun getCircleMembers(
        memberUids: List<String>,
        onSuccess: (List<UserProfile>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (memberUids.isEmpty()) {
            onSuccess(emptyList())
            return
        }

        db.collection("users")
            .whereIn("uid", memberUids)
            .get()
            .addOnSuccessListener { snapshot ->
                val users = snapshot.toObjects(UserProfile::class.java)
                onSuccess(users)
            }
            .addOnFailureListener { onError(it) }
    }

    /**
     * Kick a member from the circle.
     */
    fun kickMember(
        circleId: String,
        memberUid: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection("circles")
            .document(circleId)
            .update("members", FieldValue.arrayRemove(memberUid))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    /**
     * Add a member to the circle by username.
     */
    fun addMemberByUsername(
        circleId: String,
        username: String,
        onSuccess: () -> Unit,
        onNotFound: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection("users")
            .whereEqualTo("username", username.trim())
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    onNotFound()
                    return@addOnSuccessListener
                }

                val userUid = snap.documents.first().id
                db.collection("circles")
                    .document(circleId)
                    .update("members", FieldValue.arrayUnion(userUid))
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it) }
            }
            .addOnFailureListener { onError(it) }
    }

    /**
     * Update circle name.
     */
    fun updateCircleName(
        circleId: String,
        newName: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection("circles")
            .document(circleId)
            .update("name", newName.trim())
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    /**
     * Update circle background photo.
     */
    fun updateCircleBackground(
        circleId: String,
        photoUri: Uri,
        onSuccess: (String) -> Unit,
        onError: (Exception) -> Unit
    ) {
        val storagePath = "circles/$circleId/background.jpg"
        val storageRef = FirebaseStorage.getInstance().reference.child(storagePath)

        storageRef.putFile(photoUri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    val url = uri.toString()
                    db.collection("circles")
                        .document(circleId)
                        .update("backgroundUrl", url)
                        .addOnSuccessListener { onSuccess(url) }
                        .addOnFailureListener { onError(it) }
                }.addOnFailureListener { onError(it) }
            }
            .addOnFailureListener { onError(it) }
    }

    /**
     * Delete circle and all its data.
     */
    fun deleteCircle(
        circleId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        // In a real app, you should also delete all photos in storage and subcollections.
        // For simplicity, we just delete the circle document here.
        db.collection("circles")
            .document(circleId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }


    private fun generateInviteCode(length: Int = 6): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // avoids confusing I/1/O/0
        return (1..length)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }
}
