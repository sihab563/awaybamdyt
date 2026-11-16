package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.nexusPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nexus_preferences"
)

class ThemePreferences(context: Context) {
    private val appContext = context.applicationContext

    val isDarkTheme: Flow<Boolean> = appContext.nexusPrefsDataStore.data.map { prefs ->
        prefs[KEY_DARK_THEME] ?: true
    }

    val isSoundEnabled: Flow<Boolean> = appContext.nexusPrefsDataStore.data.map { prefs ->
        prefs[KEY_SOUND_ENABLED] ?: true
    }

    val isHapticsEnabled: Flow<Boolean> = appContext.nexusPrefsDataStore.data.map { prefs ->
        prefs[KEY_HAPTICS_ENABLED] ?: true
    }

    val isNotificationsEnabled: Flow<Boolean> = appContext.nexusPrefsDataStore.data.map { prefs ->
        prefs[KEY_NOTIFICATIONS_ENABLED] ?: true
    }

    val isAutoPlayVoiceEnabled: Flow<Boolean> = appContext.nexusPrefsDataStore.data.map { prefs ->
        prefs[KEY_AUTOPLAY_VOICE] ?: true
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        appContext.nexusPrefsDataStore.edit { prefs -> prefs[KEY_DARK_THEME] = isDark }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        appContext.nexusPrefsDataStore.edit { prefs -> prefs[KEY_SOUND_ENABLED] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        appContext.nexusPrefsDataStore.edit { prefs -> prefs[KEY_HAPTICS_ENABLED] = enabled }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        appContext.nexusPrefsDataStore.edit { prefs -> prefs[KEY_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setAutoPlayVoiceEnabled(enabled: Boolean) {
        appContext.nexusPrefsDataStore.edit { prefs -> prefs[KEY_AUTOPLAY_VOICE] = enabled }
    }

    private companion object {
        val KEY_DARK_THEME = booleanPreferencesKey("dark_theme_enabled")
        val KEY_SOUND_ENABLED = booleanPreferencesKey("sfx_sound_enabled")
        val KEY_HAPTICS_ENABLED = booleanPreferencesKey("haptics_feedback_enabled")
        val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val KEY_AUTOPLAY_VOICE = booleanPreferencesKey("autoplay_voice_enabled")
    }
}
