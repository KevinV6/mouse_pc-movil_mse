package com.example.mouse_movil.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mouse_movil.data.model.AppSettings
import com.example.mouse_movil.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mse_settings"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val SENSITIVITY = floatPreferencesKey("sensitivity")
        val ACCELERATION = floatPreferencesKey("acceleration")
        val SCROLL_SPEED = floatPreferencesKey("scroll_speed")
        val NATURAL_SCROLL = booleanPreferencesKey("natural_scroll")
        val HAPTICS = booleanPreferencesKey("haptics")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val SHOW_MOUSE_BUTTONS = booleanPreferencesKey("show_mouse_buttons")
        val FIRST_LAUNCH_GUIDE_SEEN = booleanPreferencesKey("first_launch_guide_seen")
    }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val mode = try {
            ThemeMode.valueOf(themeStr)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
        AppSettings(
            themeMode = mode,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            sensitivity = prefs[Keys.SENSITIVITY] ?: 1.2f,
            acceleration = prefs[Keys.ACCELERATION] ?: 0.35f,
            scrollSpeed = prefs[Keys.SCROLL_SPEED] ?: 1.2f,
            naturalScroll = prefs[Keys.NATURAL_SCROLL] ?: true,
            haptics = prefs[Keys.HAPTICS] ?: true,
            keepScreenOn = prefs[Keys.KEEP_SCREEN_ON] ?: true,
            showMouseButtons = prefs[Keys.SHOW_MOUSE_BUTTONS] ?: true,
            firstLaunchGuideSeen = prefs[Keys.FIRST_LAUNCH_GUIDE_SEEN] ?: false
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setSensitivity(value: Float) {
        context.settingsDataStore.edit { it[Keys.SENSITIVITY] = value.coerceIn(0.4f, 3.0f) }
    }

    suspend fun setAcceleration(value: Float) {
        context.settingsDataStore.edit { it[Keys.ACCELERATION] = value.coerceIn(0.0f, 1.0f) }
    }

    suspend fun setScrollSpeed(value: Float) {
        context.settingsDataStore.edit { it[Keys.SCROLL_SPEED] = value.coerceIn(0.4f, 3.0f) }
    }

    suspend fun setNaturalScroll(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.NATURAL_SCROLL] = enabled }
    }

    suspend fun setHaptics(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.HAPTICS] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setShowMouseButtons(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_MOUSE_BUTTONS] = enabled }
    }

    suspend fun markFirstLaunchGuideSeen() {
        context.settingsDataStore.edit { it[Keys.FIRST_LAUNCH_GUIDE_SEEN] = true }
    }
}
