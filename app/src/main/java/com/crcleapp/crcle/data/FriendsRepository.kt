package com.crcleapp.crcle.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Filter
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class FriendsRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val currentUid get() = auth.currentUser?.uid
    private val TAG = "FriendsRepository"

    fun sendFriendRequest(username: String, onSuccess: () -> Unit, onError: (Exception) -> Unit, onNotFound: () -> Unit) {
        val senderUid = currentUid ?: return onError(Exception("Not signed in"))
        Log.d(TAG, "sendFriendRequest: sender=$senderUid, targetUsername=$username")

        db.collection("user_public")
            .whereEqualTo("username", username.trim())
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Log.d(TAG, "sendFriendRequest: target username not found")
                    onNotFound()
                    return@addOnSuccessListener
                }
                val receiverUid = snapshot.documents[0].id
                
                if (receiverUid == senderUid) {
                    onError(Exception("You cannot add yourself"))
                    return@addOnSuccessListener
                }

                // Check for existing pending requests in both directions
                db.collection("friend_requests")
                    .where(Filter.or(
                        Filter.and(
                            Filter.equalTo("senderUid", senderUid),
                            Filter.equalTo("receiverUid", receiverUid)
                        ),
                        Filter.and(
                            Filter.equalTo("senderUid", receiverUid),
                            Filter.equalTo("receiverUid", senderUid)
                        )
                    ))
                    .whereEqualTo("status", "pending")
                    .get()
                    .addOnSuccessListener { requestSnapshot ->
                        if (!requestSnapshot.isEmpty) {
                            val existingRequest = requestSnapshot.documents[0]
                            val requester = existingRequest.getString("senderUid")
                            if (requester == senderUid) {
                                onError(Exception("Friend request already sent"))
                            } else {
                                onError(Exception("User has already sent you a request. Please accept it in your requests."))
                            }
                            return@addOnSuccessListener
                        }

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

                            val requestData = hashMapOf(
                                "senderUid" to senderUid,
                                "receiverUid" to receiverUid,
                                "status" to "pending",
                                "timestamp" to FieldValue.serverTimestamp()
                            )

                            db.collection("friend_requests").add(requestData)
                                .addOnSuccessListener { onSuccess() }
                                .addOnFailureListener { e -> 
                                    if (e.message?.contains("PERMISSION_DENIED") == true) {
                                        onNotFound()
                                    } else {
                                        onError(e)
                                    }
                                }
                        }.addOnFailureListener { onError(it) }
                    }
                    .addOnFailureListener { onError(it) }
            }
            .addOnFailureListener { onError(it) }
    }

    fun listenToFriends(): Flow<List<String>> = callbackFlow {
        val uid = currentUid ?: return@callbackFlow
        val listener = db.collection("users").document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) Log.e(TAG, "listenToFriends error", error)
            if (snapshot != null && snapshot.exists()) {
                val friends = snapshot.get("friends") as? List<String> ?: emptyList()
                trySend(friends)
            }
        }
        awaitClose { listener.remove() }
    }

    fun listenToIncomingRequests(): Flow<List<FriendRequest>> = callbackFlow {
        val uid = currentUid ?: return@callbackFlow
        val listener = db.collection("friend_requests")
            .whereEqualTo("receiverUid", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
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
            .addSnapshotListener { snapshot, error ->
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
        val uid = currentUid ?: return
        Log.d(TAG, "acceptFriendRequest: requestId=${request.id}")
        
        val batch = db.batch()
        val requestRef = db.collection("friend_requests").document(request.id)
        batch.update(requestRef, "status", "accepted")
        
        val userRef = db.collection("users").document(uid)
        batch.update(userRef, "friends", FieldValue.arrayUnion(request.senderUid))
        
        batch.commit()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun declineFriendRequest(requestId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        db.collection("friend_requests").document(requestId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun cancelFriendRequest(requestId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        db.collection("friend_requests").document(requestId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun removeFriend(friendUid: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val uid = currentUid ?: return
        Log.d(TAG, "removeFriend: initiative from $uid to remove $friendUid")
        
        // We only remove the friend from the current user's document.
        // A Cloud Function 'onFriendRemoved' watches the 'users' collection 
        // and automatically removes the current user from the other person's friends list
        // and cleans up any old friend request documents.
        db.collection("users").document(uid)
            .update("friends", FieldValue.arrayRemove(friendUid))
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { 
                Log.e(TAG, "removeFriend: local removal failed", it)
                onError(it) 
            }
    }

    fun blockUser(targetUid: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val uid = currentUid ?: return
        val batch = db.batch()
        val userRef = db.collection("users").document(uid)
        
        batch.update(userRef, "blockedUsers", FieldValue.arrayUnion(targetUid))
        batch.update(userRef, "friends", FieldValue.arrayRemove(targetUid))
        
        db.collection("friend_requests")
            .where(Filter.or(
                Filter.and(
                    Filter.equalTo("senderUid", uid),
                    Filter.equalTo("receiverUid", targetUid)
                ),
                Filter.and(
                    Filter.equalTo("senderUid", targetUid),
                    Filter.equalTo("receiverUid", uid)
                )
            ))
            .get()
            .addOnSuccessListener { snapshot ->
                snapshot.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }
                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onError(it) }
            }
            .addOnFailureListener { onError(it) }
    }

    fun listenToBlockedUsers(): Flow<List<String>> = callbackFlow {
        val uid = currentUid ?: return@callbackFlow
        val listener = db.collection("users").document(uid).addSnapshotListener { snapshot, error ->
            if (snapshot != null && snapshot.exists()) {
                val blocked = snapshot.get("blockedUsers") as? List<String> ?: emptyList()
                trySend(blocked)
            }
        }
        awaitClose { listener.remove() }
    }
    
    fun unblockUser(targetUid: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val uid = currentUid ?: return
        db.collection("users").document(uid)
            .update("blockedUsers", FieldValue.arrayRemove(targetUid))
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun getUser(uid: String, onSuccess: (UserProfile?) -> Unit, onError: (Exception) -> Unit) {
        db.collection("user_public").document(uid).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val user = doc.toObject(UserProfile::class.java)?.copy(uid = doc.id)
                    onSuccess(user)
                } else {
                    onSuccess(null)
                }
            }
            .addOnFailureListener { onError(it) }
    }

    fun getUsers(uids: List<String>, onSuccess: (List<UserProfile>) -> Unit, onError: (Exception) -> Unit) {
        if (uids.isEmpty()) {
            onSuccess(emptyList())
            return
        }
        db.collection("user_public")
            .whereIn(FieldPath.documentId(), uids)
            .get()
            .addOnSuccessListener { snapshot ->
                val users = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(UserProfile::class.java)?.copy(uid = doc.id)
                }
                onSuccess(users)
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
