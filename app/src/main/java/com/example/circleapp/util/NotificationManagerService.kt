package com.example.circleapp.util

import android.content.Context
import com.example.circleapp.data.NotificationPreferencesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotificationManagerService(private val context: Context) {
    private val notificationHelper = NotificationHelper(context)
    private val preferences = NotificationPreferencesStore(context)
    private val scope = CoroutineScope(Dispatchers.IO)

    fun notifyMemberJoined(circleName: String, username: String) {
        sendNotification(
            NotificationMessages.getMemberJoinedTitle(circleName),
            NotificationMessages.getMemberJoinedMessage(username),
            (circleName + username).hashCode()
        )
    }

    fun notifyMassUpload(username: String) {
        sendNotification(
            NotificationMessages.getMassUploadTitle(),
            NotificationMessages.getMassUploadMessage(username),
            username.hashCode()
        )
    }

    fun notifyCircleInvite(circleName: String) {
        sendNotification(
            NotificationMessages.getCircleInviteTitle(),
            NotificationMessages.getCircleInviteMessage(circleName),
            circleName.hashCode()
        )
    }

    fun notifyFriendRequest(username: String) {
        sendNotification(
            NotificationMessages.getFriendRequestTitle(),
            NotificationMessages.getFriendRequestMessage(username),
            username.hashCode()
        )
    }

    fun notifyCircleClosing(circleName: String) {
        sendNotification(
            NotificationMessages.getCircleClosingTitle(circleName),
            NotificationMessages.getCircleClosingMessage(),
            (circleName + "closing").hashCode()
        )
    }

    fun notifyCircleDeleting(circleName: String) {
        sendNotification(
            NotificationMessages.getCircleDeletingTitle(circleName),
            NotificationMessages.getCircleDeletingMessage(),
            (circleName + "deleting").hashCode()
        )
    }

    private fun sendNotification(title: String, message: String, id: Int) {
        scope.launch {
            if (preferences.notificationsEnabledFlow.first()) {
                notificationHelper.showNotification(title, message, id)
            }
        }
    }
}
