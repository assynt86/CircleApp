package com.example.circleapp.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.notificationDataStore by preferencesDataStore(name = "notification_prefs")

class NotificationPreferencesStore(private val context: Context) {
    private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")

    val notificationsEnabledFlow: Flow<Boolean> = context.notificationDataStore.data
        .map { preferences ->
            preferences[NOTIFICATIONS_ENABLED] ?: true
        }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.notificationDataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED] = enabled
        }
    }
}
