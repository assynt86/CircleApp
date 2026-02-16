package com.example.circleapp.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import java.util.concurrent.TimeUnit

data class Circle(
    @DocumentId val id: String = "",
    val name: String = "",
    val ownerUid: String = "",
    val inviteCode: String = "",
    val members: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val closeAt: Timestamp? = null,
    val deleteAt: Timestamp? = null,
    val cleanedUp: Boolean = false,
    val status: String = "open",
    @get:Exclude var previewUrl: String? = null
) {
    @get:Exclude
    val isClosed: Boolean
        get() {
            val now = System.currentTimeMillis()
            val closeAtMillis = closeAt?.toDate()?.time ?: (now + TimeUnit.DAYS.toMillis(8))
            return status == "closed" || closeAtMillis < now
        }

    @get:Exclude
    val isExpiringSoon: Boolean
        get() {
            if (isClosed) return false
            val now = System.currentTimeMillis()
            val closeAtMillis = closeAt?.toDate()?.time ?: return false
            return (closeAtMillis - now) < TimeUnit.DAYS.toMillis(1)
        }

    @get:Exclude
    val remainingProgress: Float
        get() {
            val now = System.currentTimeMillis()
            if (isClosed) {
                // Progress towards deletion
                val start = closeAt?.toDate()?.time ?: return 0f
                val end = deleteAt?.toDate()?.time ?: return 0f
                val total = end - start
                if (total <= 0) return 0f
                val remaining = end - now
                return (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            } else {
                // Progress towards closing
                val start = createdAt?.toDate()?.time ?: return 1f
                val end = closeAt?.toDate()?.time ?: return 0f
                val total = end - start
                if (total <= 0) return 0f
                val remaining = end - now
                return (remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            }
        }
}

data class Photo(
    @DocumentId val id: String = "",
    val uploaderUid: String = "",
    val storagePath: String = "",
    val createdAt: Timestamp? = null
)

data class CircleInfo(
    val name: String,
    val inviteCode: String,
    val status: String,
    val closeAt: Timestamp?,
    val deleteAt: Timestamp? = null,
    val ownerUid: String
) {
    val isClosed: Boolean
        get() {
            val now = System.currentTimeMillis()
            val closeAtMillis = closeAt?.toDate()?.time ?: (now + TimeUnit.DAYS.toMillis(8))
            return status == "closed" || closeAtMillis < now
        }

    val isExpiringSoon: Boolean
        get() {
            if (isClosed) return false
            val now = System.currentTimeMillis()
            val closeAtMillis = closeAt?.toDate()?.time ?: return false
            return (closeAtMillis - now) < TimeUnit.DAYS.toMillis(1)
        }
}

data class PhotoItem(
    val id: String,
    val uploaderUid: String,
    val storagePath: String,
    val createdAt: Timestamp?,
    var downloadUrl: String? = null
)

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val username: String = "",
    val phone: String = "",
    val displayName: String = "",
    val createdAt: Timestamp? = null
)
