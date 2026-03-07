package com.crcleapp.crcle.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicLong
import com.google.firebase.firestore.AggregateSource

class CircleRepository {

    companion object {
        const val STORAGE_LIMIT_BYTES = 1_073_741_824L // 1 GB
    }

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    fun getCurrentUserUid(): String? = auth.currentUser?.uid

    /**
     * Creates a circle with:
     * - storageBytes = 0
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
            "status" to "open",
            "storageBytes" to 0L
        )

        db.collection("circles")
            .add(circleData)
            .addOnSuccessListener { docRef ->
                val circleId = docRef.id

                val codeData = hashMapOf(
                    "circleId" to circleId,
                    "circleName" to circleName,
                    "circleBackgroundUrl" to null,
                    "status" to "open"
                )

                db.collection("circle_codes").document(inviteCode).set(codeData)
                    .addOnSuccessListener {
                        onSuccess(circleId)
                    }
                    .addOnFailureListener { e ->
                        onError(e)
                    }
            }
            .addOnFailureListener { e ->
                onError(e)
            }
    }

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

        db.collection("circle_codes")
            .document(inviteCode.trim())
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onNotFound()
                    return@addOnSuccessListener
                }

                val circleId = doc.getString("circleId") ?: ""
                if (circleId.isEmpty()) {
                    onNotFound()
                    return@addOnSuccessListener
                }

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

    fun getCircleByInviteCode(
        inviteCode: String,
        onSuccess: (Circle) -> Unit,
        onNotFound: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection("circle_codes")
            .document(inviteCode.trim())
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onNotFound()
                } else {
                    val circle = Circle(
                        id = doc.getString("circleId") ?: "",
                        name = doc.getString("circleName") ?: "",
                        backgroundUrl = doc.getString("circleBackgroundUrl"),
                        status = doc.getString("status") ?: "open",
                        inviteCode = doc.id
                    )
                    onSuccess(circle)
                }
            }
            .addOnFailureListener { onError(it) }
    }

    /**
     * Uploads a photo (Uri) to Firebase Storage, then writes metadata to Firestore.
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

        // 1. Check storage usage using Aggregation Query (cheap & fast)
        // Does not require write permission on the circle doc.
        db.collection("circles").document(circleId).collection("photos").count().get(AggregateSource.SERVER).addOnSuccessListener { countSnapshot ->
             // If we want exact bytes, we need sum("sizeBytes"), but that requires an index.
             // For now, let's try to rely on the 'storageBytes' field if possible, OR fallback to a robust check.
             // Given the permission error on writing to circle doc, we will skip writing to circle doc.
             // But we still want to block uploads if capacity is full.
             // Since we can't trust the circle doc's storageBytes field (as we can't write to it),
             // and summing all docs is expensive without an aggregation index...
             // We will assume that if the user can't write to circle doc, we can't enforce a strict byte limit easily server-side without Cloud Functions.
             // However, the user insists on blocking.
             
             // Let's try to read the current storageBytes. Even if we can't write, we can read.
             // But since we can't write, it will stay 0.
             
             // ALTERNATIVE: Just fetch all photos and sum them up client side. 
             // This is read-heavy but guaranteed to work with current permissions if we have read access.
             db.collection("circles").document(circleId).collection("photos").get().addOnSuccessListener { photosSnap ->
                 var currentTotalBytes = 0L
                 photosSnap.documents.forEach { doc ->
                     currentTotalBytes += (doc.getLong("sizeBytes") ?: 0L)
                 }
                 
                 if (currentTotalBytes >= STORAGE_LIMIT_BYTES) {
                     onError(Exception("Circle storage limit reached"))
                     return@addOnSuccessListener
                 }

                 // If we have space, proceed with upload.
                 val photoId = db.collection("tmp").document().id
                 val storagePath = "circles/$circleId/$photoId.jpg"
                 val storageRef = FirebaseStorage.getInstance().reference.child(storagePath)

                 storageRef.putFile(photoUri).addOnSuccessListener { taskSnapshot ->
                     val sizeBytes = taskSnapshot.metadata?.sizeBytes ?: 0L
                     
                     // Double check (optimistic concurrency not strictly possible without transaction on a single doc, 
                     // but we re-check total)
                     // Since we can't write to circle doc, we just write the photo doc.
                     
                     val photoData = hashMapOf(
                        "uploaderUid" to uid,
                        "storagePath" to storagePath,
                        "sizeBytes" to sizeBytes,
                        "createdAt" to FieldValue.serverTimestamp()
                     )
                     
                     db.collection("circles").document(circleId).collection("photos").document(photoId)
                        .set(photoData)
                        .addOnSuccessListener {
                            onSuccess(photoId)
                        }
                        .addOnFailureListener { e ->
                            // Rollback storage
                            storageRef.delete()
                            onError(e)
                        }
                 }.addOnFailureListener { e -> onError(e) }
             }.addOnFailureListener { e -> onError(e) }
        }.addOnFailureListener { e -> onError(e) }
    }

    fun addPhotoToCircles(
        photoUri: Uri,
        circleIds: List<String>,
        onResult: (isSuccess: Boolean, errors: Map<String, String>) -> Unit
    ) {
        if (circleIds.isEmpty()) {
            onResult(false, emptyMap())
            return
        }

        var completedCount = 0
        var successCount = 0
        val errors = mutableMapOf<String, String>()
        val totalCircles = circleIds.size

        circleIds.forEach { circleId ->
            uploadPhotoToCircle(circleId, photoUri,
                onSuccess = {
                    completedCount++
                    successCount++
                    if (completedCount == totalCircles) {
                        onResult(successCount == totalCircles, errors)
                    }
                },
                onError = { e ->
                    completedCount++
                    errors[circleId] = e.message ?: "Unknown error"
                    if (completedCount == totalCircles) {
                        onResult(successCount == totalCircles, errors)
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
                    val storageBytes = snap.getLong("storageBytes") ?: 0L
                    onSuccess(CircleInfo(name, invite, status, closeAt, deleteAt, ownerUid, backgroundUrl, storageBytes))
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
                            sizeBytes = doc.getLong("sizeBytes") ?: 0L,
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
                        sizeBytes = doc.getLong("sizeBytes") ?: 0L,
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
        db.collection("circles").document(circleId).collection("photos").document(photoId)
            .get()
            .addOnSuccessListener { doc ->
                val sizeBytes = doc.getLong("sizeBytes") ?: 0L
                
                FirebaseStorage.getInstance().reference.child(storagePath).delete()
                    .addOnCompleteListener { _ ->
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
            .addOnFailureListener {
                onResult(false)
            }
    }

    fun getCircleMembers(
        memberUids: List<String>,
        onSuccess: (List<UserProfile>) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (memberUids.isEmpty()) {
            onSuccess(emptyList())
            return
        }

        db.collection("user_public")
            .whereIn("uid", memberUids)
            .get()
            .addOnSuccessListener { snapshot ->
                val users = snapshot.toObjects(UserProfile::class.java)
                onSuccess(users)
            }
            .addOnFailureListener { onError(it) }
    }

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

    fun addMemberByUid(
        circleId: String,
        uid: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        db.collection("circles")
            .document(circleId)
            .update("members", FieldValue.arrayUnion(uid))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun addMembersByUids(
        circleId: String,
        uids: List<String>,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (uids.isEmpty()) {
            onSuccess()
            return
        }
        db.collection("circles")
            .document(circleId)
            .update("members", FieldValue.arrayUnion(*uids.toTypedArray()))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun sendCircleInvite(
        circleId: String,
        circleName: String,
        circleBackgroundUrl: String?,
        inviterUid: String,
        inviterName: String,
        targetUser: UserProfile,
        isFriend: Boolean,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (isFriend || targetUser.autoAcceptInvites) {
            addMemberByUid(circleId, targetUser.uid, onSuccess, onError)
        } else {
            val inviteData = hashMapOf(
                "circleId" to circleId,
                "circleName" to circleName,
                "circleBackgroundUrl" to circleBackgroundUrl,
                "inviteeUid" to targetUser.uid,
                "inviterUid" to inviterUid,
                "inviterName" to inviterName,
                "status" to "pending",
                "timestamp" to FieldValue.serverTimestamp()
            )
            db.collection("circle_invites").add(inviteData)
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it) }
        }
    }

    fun listenToCircleInvites(): Flow<List<CircleInvite>> = callbackFlow {
        val uid = auth.currentUser?.uid ?: return@callbackFlow
        val listener = db.collection("circle_invites")
            .whereEqualTo("inviteeUid", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val invites = snapshot.toObjects(CircleInvite::class.java)
                    trySend(invites)
                }
            }
        awaitClose { listener.remove() }
    }

    fun respondToCircleInvite(
        inviteId: String,
        circleId: String,
        inviteeUid: String,
        accept: Boolean,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (accept) {
            val batch = db.batch()
            batch.update(db.collection("circle_invites").document(inviteId), "status", "accepted")
            batch.update(db.collection("circles").document(circleId), "members", FieldValue.arrayUnion(inviteeUid))
            batch.commit()
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it) }
        } else {
            db.collection("circle_invites").document(inviteId).update("status", "declined")
                .addOnSuccessListener { onSuccess() }
                .addOnFailureListener { onError(it) }
        }
    }

    fun updateCircleName(
        circleId: String,
        inviteCode: String,
        newName: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val batch = db.batch()
        batch.update(db.collection("circles").document(circleId), "name", newName.trim())
        if (inviteCode.isNotEmpty()) {
            batch.update(db.collection("circle_codes").document(inviteCode), "circleName", newName.trim())
        }

        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun updateCircleBackground(
        circleId: String,
        inviteCode: String,
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
                    val batch = db.batch()
                    batch.update(db.collection("circles").document(circleId), "backgroundUrl", url)
                    if (inviteCode.isNotEmpty()) {
                        batch.update(db.collection("circle_codes").document(inviteCode), "circleBackgroundUrl", url)
                    }

                    batch.commit()
                        .addOnSuccessListener { onSuccess(url) }
                        .addOnFailureListener { onError(it) }
                }.addOnFailureListener { onError(it) }
            }
            .addOnFailureListener { onError(it) }
    }

    fun deleteCircle(
        circleId: String,
        inviteCode: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val batch = db.batch()
        batch.delete(db.collection("circles").document(circleId))
        if (inviteCode.isNotEmpty()) {
            batch.delete(db.collection("circle_codes").document(inviteCode))
        }

        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun reportUser(
        reporterUid: String,
        reportedUid: String,
        reason: String,
        circleId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val reportData = hashMapOf(
            "reporterUid" to reporterUid,
            "reportedUid" to reportedUid,
            "reason" to reason,
            "circleId" to circleId,
            "timestamp" to FieldValue.serverTimestamp()
        )

        db.collection("reports")
            .add(reportData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    /**
     * Recalculates the total storage bytes for a circle.
     */
    fun syncCircleStorage(circleId: String, onComplete: () -> Unit = {}) {
        db.collection("circles").document(circleId).collection("photos").get()
            .addOnSuccessListener { snapshot ->
                val docs = snapshot.documents
                if (docs.isEmpty()) {
                    updateCircleStorageBytes(circleId, 0L)
                    onComplete()
                    return@addOnSuccessListener
                }

                val totalBytes = AtomicLong(0L)
                var processedCount = 0

                fun checkCompletion() {
                    processedCount++
                    if (processedCount == docs.size) {
                        updateCircleStorageBytes(circleId, totalBytes.get())
                        onComplete()
                    }
                }

                docs.forEach { doc ->
                    val size = doc.getLong("sizeBytes")
                    if (size != null && size > 0) {
                        totalBytes.addAndGet(size)
                        checkCompletion()
                    } else {
                        val path = doc.getString("storagePath") ?: ""
                        if (path.isNotEmpty()) {
                            FirebaseStorage.getInstance().reference.child(path).metadata
                                .addOnSuccessListener { metadata ->
                                    val s = metadata.sizeBytes
                                    totalBytes.addAndGet(s)
                                    doc.reference.update("sizeBytes", s)
                                    checkCompletion()
                                }
                                .addOnFailureListener { checkCompletion() }
                        } else {
                            checkCompletion()
                        }
                    }
                }
            }
            .addOnFailureListener { onComplete() }
    }

    fun updateCircleStorageBytes(circleId: String, totalBytes: Long) {
        db.collection("circles").document(circleId).update("storageBytes", totalBytes)
    }

    private fun generateInviteCode(length: Int = 6): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..length)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }
}