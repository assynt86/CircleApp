package com.crcleapp.crcle.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.circleDataStore by preferencesDataStore(name = "circle_settings")

class CirclePreferencesStore(private val context: Context) {
    private val SELECTED_CIRCLES_KEY = stringSetPreferencesKey("selected_circle_ids")
    private val OPEN_ON_HOME_KEY = booleanPreferencesKey("open_on_home")

    val selectedCircleIdsFlow: Flow<Set<String>> = context.circleDataStore.data
        .map { preferences ->
            preferences[SELECTED_CIRCLES_KEY] ?: emptySet()
        }

    val openOnHomeFlow: Flow<Boolean> = context.circleDataStore.data
        .map { preferences ->
            preferences[OPEN_ON_HOME_KEY] ?: false
        }

    suspend fun saveSelectedCircleIds(ids: Set<String>) {
        context.circleDataStore.edit { preferences ->
            preferences[SELECTED_CIRCLES_KEY] = ids
        }
    }

    suspend fun setOpenOnHome(enabled: Boolean) {
        context.circleDataStore.edit { preferences ->
            preferences[OPEN_ON_HOME_KEY] = enabled
        }
    }
}
