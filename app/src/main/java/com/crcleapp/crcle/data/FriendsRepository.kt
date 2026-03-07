package com.crcleapp.crcle.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FriendsRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val currentUid get() = auth.currentUser?.uid

    fun sendFriendRequest(username: String, onSuccess: () -> Unit, onError: (Exception) -> Unit, onNotFound: () -> Unit) {
        val senderUid = currentUid ?: return onError(Exception("Not signed in"))

        // Lookup user by username in user_public
        db.collection("user_public")
            .whereEqualTo("username", username.trim())
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    onNotFound()
                    return@addOnSuccessListener
                }
                val receiverUid = snapshot.documents[0].id
                if (receiverUid == senderUid) {
                    onError(Exception("You cannot add yourself"))
                    return@addOnSuccessListener
                }

                // Check our own friends/blocked lists first
                db.collection("users").document(senderUid).get().addOnSuccessListener { senderDoc ->
                    val friends = senderDoc.get("friends") as? List<String> ?: emptyList()
                    val blocked = senderDoc.get("blockedUsers") as? List<String> ?: emptyList()
                    
                    if (receiverUid in friends) {
                        onError(Exception("Already friends"))
                        return@addOnSuccessListener
                    }
                    if (receiverUid in blocked) {
                        onError(Exception("You have blocked this user"))
                        return@addOnSuccessListener
                    }

                    // Attempt to create the request. If the receiver has blocked the sender, 
                    // the Firestore security rules should handle the rejection.
                    val requestData = hashMapOf(
                        "senderUid" to senderUid,
                        "receiverUid" to receiverUid,
                        "status" to "pending",
                        "timestamp" to FieldValue.serverTimestamp()
                    )

                    db.collection("friend_requests").add(requestData)
                        .addOnSuccessListener { onSuccess() }
                        .addOnFailureListener { e -> 
                            // If permission denied, it might mean the receiver has blocked us
                            if (e.message?.contains("PERMISSION_DENIED") == true) {
                                // We treat this as not found to avoid leaking block status
                                onNotFound()
                            } else {
                                onError(e)
                            }
                        }
                }.addOnFailureListener { onError(it) }
            }
            .addOnFailureListener { onError(it) }
    }

    fun listenToFriends(): Flow<List<String>> = callbackFlow {
        val uid = currentUid ?: return@callbackFlow
        val listener = db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val friends = snapshot.get("friends") as? List<String> ?: emptyList()
                trySend(friends)
            }
        }
        awaitClose { listener.remove() }
    }

    fun listenToBlockedUsers(): Flow<List<String>> = callbackFlow {
        val uid = currentUid ?: return@callbackFlow
        val listener = db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
            if (snapshot != null && snapshot.exists()) {
                val blocked = snapshot.get("blockedUsers") as? List<String> ?: emptyList()
                trySend(blocked)
            }
        }
        awaitClose { listener.remove() }
    }

    fun listenToIncomingRequests(): Flow<List<FriendRequest>> = callbackFlow {
        val uid = currentUid ?: return@callbackFlow
        val listener = db.collection("friend_requests")
            .whereEqualTo("receiverUid", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val requests = snapshot.documents.mapNotNull { doc ->
                        FriendRequest(
                            id = doc.id,
                            senderUid = doc.getString("senderUid") ?: "",
                            receiverUid = doc.getString("receiverUid") ?: "",
                            status = doc.getString("status") ?: "pending",
                            timestamp = doc.getTimestamp("timestamp")
                        )
                    }
                    trySend(requests)
                }
            }
        awaitClose { listener.remove() }
    }

    fun listenToOutgoingRequests(): Flow<List<FriendRequest>> = callbackFlow {
        val uid = currentUid ?: return@callbackFlow
        val listener = db.collection("friend_requests")
            .whereEqualTo("senderUid", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val requests = snapshot.documents.mapNotNull { doc ->
                        FriendRequest(
                            id = doc.id,
                            senderUid = doc.getString("senderUid") ?: "",
                            receiverUid = doc.getString("receiverUid") ?: "",
                            status = doc.getString("status") ?: "pending",
                            timestamp = doc.getTimestamp("timestamp")
                        )
                    }
                    trySend(requests)
                }
            }
        awaitClose { listener.remove() }
    }

    fun acceptFriendRequest(request: FriendRequest, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val updates = hashMapOf<String, Any>(
            "status" to "accepted",
            "timestamp" to FieldValue.serverTimestamp()
        )
        db.collection("friend_requests").document(request.id)
            .update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun declineFriendRequest(requestId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val updates = hashMapOf<String, Any>(
            "status" to "declined",
            "timestamp" to FieldValue.serverTimestamp()
        )
        db.collection("friend_requests").document(requestId)
            .update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun cancelFriendRequest(requestId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val updates = hashMapOf<String, Any>(
            "status" to "canceled",
            "timestamp" to FieldValue.serverTimestamp()
        )
        db.collection("friend_requests").document(requestId)
            .update(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun removeFriend(friendUid: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val uid = currentUid ?: return
        val batch = db.batch()
        val userRef = db.collection("users").document(uid)
        val friendRef = db.collection("users").document(friendUid)

        batch.update(userRef, "friends", FieldValue.arrayRemove(friendUid))
        batch.update(friendRef, "friends", FieldValue.arrayRemove(uid))

        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun blockUser(targetUid: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val uid = currentUid ?: return
        val batch = db.batch()
        val userRef = db.collection("users").document(uid)
        
        batch.update(userRef, "blockedUsers", FieldValue.arrayUnion(targetUid))
        batch.update(userRef, "friends", FieldValue.arrayRemove(targetUid))
        
        // Also remove from target's friends
        val targetRef = db.collection("users").document(targetUid)
        batch.update(targetRef, "friends", FieldValue.arrayRemove(uid))

        // Find requests to delete
        db.collection("friend_requests")
            .whereIn("senderUid", listOf(uid, targetUid))
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { doc ->
                    val sender = doc.getString("senderUid")
                    val receiver = doc.getString("receiverUid")
                    if ((sender == uid && receiver == targetUid) || (sender == targetUid && receiver == uid)) {
                        batch.delete(doc.reference)
                    }
                }
                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it) }
            }
            .addOnFailureListener { onError(it) }
    }

    fun unblockUser(targetUid: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val uid = currentUid ?: return
        db.collection("users").document(uid)
            .update("blockedUsers", FieldValue.arrayRemove(targetUid))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun getUsers(uids: List<String>, onSuccess: (List<UserProfile>) -> Unit, onError: (Exception) -> Unit) {
        if (uids.isEmpty()) {
            onSuccess(emptyList())
            return
        }
        // Use FieldPath.documentId() to ensure we match based on the document key (UID),
        // regardless of whether the 'uid' field exists inside the document.
        db.collection("user_public")
            .whereIn(FieldPath.documentId(), uids)
            .get()
            .addOnSuccessListener { snapshot ->
                // Note: toObject might fail to set 'uid' if it's missing in the doc body,
                // so we manually ensure the ID is set from the document ID.
                val users = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(UserProfile::class.java)?.copy(uid = doc.id)
                }
                onSuccess(users)
            }
            .addOnFailureListener { onError(it) }
    }

    fun getUser(uid: String, onSuccess: (UserProfile?) -> Unit, onError: (Exception) -> Unit) {
        db.collection("user_public").document(uid).get()
            .addOnSuccessListener { snapshot ->
                onSuccess(snapshot.toObject(UserProfile::class.java)?.copy(uid = snapshot.id))
            }
            .addOnFailureListener { onError(it) }
    }

    fun addFriendToCircle(friendUid: String, circleId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        db.collection("circles").document(circleId)
            .update("members", FieldValue.arrayUnion(friendUid))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }
}
