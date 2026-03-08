package com.crcleapp.crcle.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

data class NotificationLog(
    @DocumentId val id: String = "",
    val title: String = "",
    val body: String = "",
    val timestamp: Timestamp? = null,
    val type: String = "",
    val circleId: String? = null,
    val senderUid: String? = null
)
