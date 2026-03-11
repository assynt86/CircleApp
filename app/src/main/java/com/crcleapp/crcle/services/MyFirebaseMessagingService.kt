package com.crcleapp.crcle.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.crcleapp.crcle.MainActivity
import com.crcleapp.crcle.R
import com.crcleapp.crcle.data.NotificationPreferencesStore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d("FCM", "Message received: data=${remoteMessage.data}, notification=${remoteMessage.notification?.title}")

        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "Circle Notification"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: ""
        val type = remoteMessage.data["type"]

        serviceScope.launch {
            val prefs = NotificationPreferencesStore(applicationContext)
            val notificationsEnabled = prefs.notificationsEnabledFlow.first()
            
            if (!notificationsEnabled) return@launch

            val friendRequestsEnabled = prefs.friendRequestsEnabledFlow.first()
            val friendRequestAcceptedEnabled = prefs.friendRequestAcceptedEnabledFlow.first()
            val circleInvitesEnabled = prefs.circleInvitesEnabledFlow.first()
            val newPhotosEnabled = prefs.newPhotosEnabledFlow.first()

            val shouldShow = when (type) {
                "friend_request" -> friendRequestsEnabled
                "friend_request_accepted" -> friendRequestAcceptedEnabled
                "circle_invite" -> circleInvitesEnabled
                "new_photo" -> newPhotosEnabled
                else -> true // default to true for unknown types or test_pings
            }

            if (shouldShow) {
                sendNotification(title, body)
            }
        }
    }

    override fun onNewToken(token: String) {
        Log.d("FCM", "New token generated: $token")
    }

    private fun sendNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = (title + body).hashCode()
        val channelId = "circle_silent_updates"
        
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Silent, no heads-up
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Circle Silent Updates",
                NotificationManager.IMPORTANCE_LOW // Silent
            ).apply {
                description = "Silent notifications for requests and invites"
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(notificationId, notificationBuilder.build())
    }
}
