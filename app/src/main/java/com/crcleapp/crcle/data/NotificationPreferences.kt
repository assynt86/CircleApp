package com.crcleapp.crcle.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.notificationDataStore by preferencesDataStore(name = "notification_settings")

class NotificationPreferencesStore(private val context: Context) {
    private val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
    private val FRIEND_REQUESTS_ENABLED_KEY = booleanPreferencesKey("friend_requests_enabled")
    private val FRIEND_REQUEST_ACCEPTED_ENABLED_KEY = booleanPreferencesKey("friend_request_accepted_enabled")
    private val CIRCLE_INVITES_ENABLED_KEY = booleanPreferencesKey("circle_invites_enabled")
    private val NEW_PHOTOS_ENABLED_KEY = booleanPreferencesKey("new_photos_enabled")

    val notificationsEnabledFlow: Flow<Boolean> = context.notificationDataStore.data
        .map { preferences ->
            preferences[NOTIFICATIONS_ENABLED_KEY] ?: true
        }

    val friendRequestsEnabledFlow: Flow<Boolean> = context.notificationDataStore.data
        .map { preferences ->
            preferences[FRIEND_REQUESTS_ENABLED_KEY] ?: true
        }

    val friendRequestAcceptedEnabledFlow: Flow<Boolean> = context.notificationDataStore.data
        .map { preferences ->
            preferences[FRIEND_REQUEST_ACCEPTED_ENABLED_KEY] ?: true
        }

    val circleInvitesEnabledFlow: Flow<Boolean> = context.notificationDataStore.data
        .map { preferences ->
            preferences[CIRCLE_INVITES_ENABLED_KEY] ?: true
        }

    val newPhotosEnabledFlow: Flow<Boolean> = context.notificationDataStore.data
        .map { preferences ->
            preferences[NEW_PHOTOS_ENABLED_KEY] ?: true
        }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED_KEY] = enabled
        }
    }

    suspend fun setFriendRequestsEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { preferences ->
            preferences[FRIEND_REQUESTS_ENABLED_KEY] = enabled
        }
    }

    suspend fun setFriendRequestAcceptedEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { preferences ->
            preferences[FRIEND_REQUEST_ACCEPTED_ENABLED_KEY] = enabled
        }
    }

    suspend fun setCircleInvitesEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { preferences ->
            preferences[CIRCLE_INVITES_ENABLED_KEY] = enabled
        }
    }

    suspend fun setNewPhotosEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { preferences ->
            preferences[NEW_PHOTOS_ENABLED_KEY] = enabled
        }
    }
}
