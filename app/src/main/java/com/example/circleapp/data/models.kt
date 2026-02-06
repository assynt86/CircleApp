package com.example.circleapp.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

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
    val status: String = "open"
)

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
    val closeAt: Timestamp?
)

data class PhotoItem(
    val id: String,
    val uploaderUid: String,
    val storagePath: String,
    val createdAt: Timestamp?,
    var downloadUrl: String? = null
)
