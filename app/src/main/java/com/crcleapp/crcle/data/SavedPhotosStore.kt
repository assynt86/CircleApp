package com.crcleapp.crcle.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// This creates a DataStore file named "circle_prefs"
private val Context.dataStore by preferencesDataStore(name = "circle_prefs")

class SavedPhotosStore(private val context: Context) {

    // We store a set of strings like: "circleId:photoId"
    private val SAVED_SET_KEY: Preferences.Key<Set<String>> =
        stringSetPreferencesKey("saved_photo_ids")

    private val AUTO_SAVE_KEY = booleanPreferencesKey("auto_save_enabled")

    /**
     * Returns a Flow of the auto-save preference (default false).
     */
    val autoSaveFlow: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_SAVE_KEY] ?: false
        }

    /**
     * Updates the auto-save preference.
     */
    suspend fun setAutoSave(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_SAVE_KEY] = enabled
        }
    }

    /**
     * Returns true if this photo was already saved for this user (on this device).
     */
    suspend fun isSaved(circleId: String, photoId: String): Boolean {
        val key = "$circleId:$photoId"
        val prefs = context.dataStore.data.first()
        val saved = prefs[SAVED_SET_KEY] ?: emptySet()
        return saved.contains(key)
    }

    /**
     * Marks this photo as saved so we don't save it again later.
     */
    suspend fun markSaved(circleId: String, photoId: String) {
        val key = "$circleId:$photoId"
        context.dataStore.edit { prefs ->
            val current = prefs[SAVED_SET_KEY] ?: emptySet()
            prefs[SAVED_SET_KEY] = current + key
        }
    }
}
