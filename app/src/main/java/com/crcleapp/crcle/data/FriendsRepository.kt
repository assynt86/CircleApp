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
                Log.d(TAG, "sendFriendRequest: found receiverUid=$receiverUid")
                
                if (receiverUid == senderUid) {
                    Log.d(TAG, "sendFriendRequest: cannot add self")
                    onError(Exception("You cannot add yourself"))
                    return@addOnSuccessListener
                }

                // Check for existing pending requests in both directions using Filter to avoid PERMISSION_DENIED
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
                                Log.d(TAG, "sendFriendRequest: request already exists (outgoing)")
                                onError(Exception("Friend request already sent"))
                            } else {
                                Log.d(TAG, "sendFriendRequest: request already exists (incoming)")
                                onError(Exception("User has already sent you a request. Please accept it in your requests."))
                            }
                            return@addOnSuccessListener
                        }

                        db.collection("users").document(senderUid).get().addOnSuccessListener { senderDoc ->
                            val friends = senderDoc.get("friends") as? List<String> ?: emptyList()
                            val blocked = senderDoc.get("blockedUsers") as? List<String> ?: emptyList()
                            
                            if (receiverUid in friends) {
                                Log.d(TAG, "sendFriendRequest: already friends")
                                onError(Exception("Already friends"))
                                return@addOnSuccessListener
                            }
                            if (receiverUid in blocked) {
                                Log.d(TAG, "sendFriendRequest: user is blocked by sender")
                                onError(Exception("You have blocked this user"))
                                return@addOnSuccessListener
                            }

                            val requestData = hashMapOf(
                                "senderUid" to senderUid,
                                "receiverUid" to receiverUid,
                                "status" to "pending",
                                "timestamp" to FieldValue.serverTimestamp()
                            )

                            Log.d(TAG, "sendFriendRequest: attempting to add to friend_requests collection")
                            db.collection("friend_requests").add(requestData)
                                .addOnSuccessListener { 
                                    Log.d(TAG, "sendFriendRequest: successfully added request document")
                                    onSuccess() 
                                }
                                .addOnFailureListener { e -> 
                                    Log.w(TAG, "sendFriendRequest: failed to add document", e)
                                    if (e.message?.contains("PERMISSION_DENIED") == true) {
                                        Log.d(TAG, "sendFriendRequest: PERMISSION_DENIED (possibly blocked by receiver)")
                                        onNotFound()
                                    } else {
                                        onError(e)
                                    }
                                }
                        }.addOnFailureListener { 
                            Log.e(TAG, "sendFriendRequest: failed to fetch sender user doc", it)
                            onError(it) 
                        }
                    }
                    .addOnFailureListener {
                        Log.e(TAG, "sendFriendRequest: failed to check existing requests", it)
                        onError(it)
                    }
            }
            .addOnFailureListener { 
                Log.e(TAG, "sendFriendRequest: failed to fetch user_public doc", it)
                onError(it) 
            }
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

    fun listenToBlockedUsers(): Flow<List<String>> = callbackFlow {
        val uid = currentUid ?: return@callbackFlow
        val listener = db.collection("users").document(uid).addSnapshotListener { snapshot, error ->
            if (error != null) Log.e(TAG, "listenToBlockedUsers error", error)
            if (snapshot != null && snapshot.exists()) {
                val blocked = snapshot.get("blockedUsers") as? List<String> ?: emptyList()
                trySend(blocked)
            }
        }
        awaitClose { listener.remove() }
    }

    fun listenToIncomingRequests(): Flow<List<FriendRequest>> = callbackFlow {
        val uid = currentUid ?: return@callbackFlow
        Log.d(TAG, "listenToIncomingRequests: starting for $uid")
        val listener = db.collection("friend_requests")
            .whereEqualTo("receiverUid", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "listenToIncomingRequests error", error)
                }
                if (snapshot != null) {
                    Log.d(TAG, "listenToIncomingRequests: found ${snapshot.size()} docs")
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
        Log.d(TAG, "listenToOutgoingRequests: starting for $uid")
        val listener = db.collection("friend_requests")
            .whereEqualTo("senderUid", uid)
            .whereEqualTo("status", "pending")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "listenToOutgoingRequests error", error)
                }
                if (snapshot != null) {
                    Log.d(TAG, "listenToOutgoingRequests: found ${snapshot.size()} docs")
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
        Log.d(TAG, "acceptFriendRequest: requestId=${request.id}")
        val updates = hashMapOf<String, Any>(
            "status" to "accepted",
            "timestamp" to FieldValue.serverTimestamp()
        )
        db.collection("friend_requests").document(request.id)
            .update(updates)
            .addOnSuccessListener { 
                Log.d(TAG, "acceptFriendRequest: success")
                onSuccess() 
            }
            .addOnFailureListener { 
                Log.w(TAG, "acceptFriendRequest: failed", it)
                onError(it) 
            }
    }

    fun declineFriendRequest(requestId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        Log.d(TAG, "declineFriendRequest: requestId=$requestId")
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
        Log.d(TAG, "cancelFriendRequest: requestId=$requestId")
        db.collection("friend_requests").document(requestId)
            .delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    fun removeFriend(friendUid: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val uid = currentUid ?: return
        Log.d(TAG, "removeFriend: uid=$uid, friendUid=$friendUid")
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
        Log.d(TAG, "blockUser: uid=$uid, targetUid=$targetUid")
        val batch = db.batch()
        val userRef = db.collection("users").document(uid)
        
        batch.update(userRef, "blockedUsers", FieldValue.arrayUnion(targetUid))
        batch.update(userRef, "friends", FieldValue.arrayRemove(targetUid))
        
        val targetRef = db.collection("users").document(targetUid)
        batch.update(targetRef, "friends", FieldValue.arrayRemove(uid))

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

    fun unblockUser(targetUid: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val uid = currentUid ?: return
        Log.d(TAG, "unblockUser: uid=$uid, targetUid=$targetUid")
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
        Log.d(TAG, "getUsers: fetching profiles for $uids")
        db.collection("user_public")
            .whereIn(FieldPath.documentId(), uids)
            .get()
            .addOnSuccessListener { snapshot ->
                Log.d(TAG, "getUsers: found ${snapshot.size()} profiles")
                val users = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(UserProfile::class.java)?.copy(uid = doc.id)
                }
                onSuccess(users)
            }
            .addOnFailureListener { 
                Log.e(TAG, "getUsers: failed to fetch profiles", it)
                onError(it) 
            }
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
