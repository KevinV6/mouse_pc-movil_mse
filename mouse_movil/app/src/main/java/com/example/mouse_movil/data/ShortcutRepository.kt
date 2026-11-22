package com.example.mouse_movil.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mouse_movil.data.model.Shortcut
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.shortcutsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mse_shortcuts"
)

class ShortcutRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listKey = stringPreferencesKey("custom_shortcuts_json")

    val shortcutsFlow: Flow<List<Shortcut>> = context.shortcutsDataStore.data.map { prefs ->
        val raw = prefs[listKey]
        if (raw == null) {
            defaultShortcuts()
        } else {
            try {
                json.decodeFromString(ListSerializer(Shortcut.serializer()), raw)
            } catch (_: Exception) {
                defaultShortcuts()
            }
        }
    }

    suspend fun upsert(shortcut: Shortcut) {
        context.shortcutsDataStore.edit { prefs ->
            val current = decode(prefs[listKey]).toMutableList()
            val index = current.indexOfFirst { it.id == shortcut.id }
            if (index >= 0) {
                current[index] = shortcut
            } else {
                current.add(shortcut)
            }
            prefs[listKey] = encode(current)
        }
    }

    suspend fun delete(id: String) {
        context.shortcutsDataStore.edit { prefs ->
            val current = decode(prefs[listKey]).filterNot { it.id == id }
            prefs[listKey] = encode(current)
        }
    }

    suspend fun move(fromIndex: Int, toIndex: Int) {
        context.shortcutsDataStore.edit { prefs ->
            val current = decode(prefs[listKey]).toMutableList()
            if (fromIndex in current.indices && toIndex in current.indices) {
                val item = current.removeAt(fromIndex)
                current.add(toIndex, item)
                prefs[listKey] = encode(current)
            }
        }
    }

    private fun decode(raw: String?): List<Shortcut> {
        if (raw == null) return defaultShortcuts()
        return try {
            json.decodeFromString(ListSerializer(Shortcut.serializer()), raw)
        } catch (_: Exception) {
            defaultShortcuts()
        }
    }

    private fun encode(list: List<Shortcut>): String =
        json.encodeToString(ListSerializer(Shortcut.serializer()), list)

    companion object {
        fun defaultShortcuts(): List<Shortcut> = listOf(
            Shortcut(
                id = "sc-taskmgr",
                name = "Administrador de tareas",
                iconName = "Terminal",
                keys = listOf("CTRL", "SHIFT", "ESC")
            ),
            Shortcut(
                id = "sc-snip",
                name = "Recorte de pantalla",
                iconName = "Screenshot",
                keys = listOf("WIN", "SHIFT", "S")
            ),
            Shortcut(
                id = "sc-explorer",
                name = "Explorador de archivos",
                iconName = "Folder",
                keys = listOf("WIN", "E")
            )
        )
    }
}
