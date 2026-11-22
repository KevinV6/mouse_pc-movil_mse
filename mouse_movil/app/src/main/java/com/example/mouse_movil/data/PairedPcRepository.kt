package com.example.mouse_movil.data

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.mouse_movil.core.security.KeystoreCipher
import com.example.mouse_movil.data.model.PairedPc
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

private val Context.pairedPcsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "mse_paired_pcs"
)

class PairedPcRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val PAIRED_PCS_JSON = stringPreferencesKey("paired_pcs_json")
        val DEFAULT_SERVER_ID = stringPreferencesKey("default_server_id")
    }

    val pairedPcsFlow: Flow<List<PairedPc>> = context.pairedPcsDataStore.data.map { prefs ->
        val raw = prefs[Keys.PAIRED_PCS_JSON] ?: return@map emptyList()
        try {
            json.decodeFromString(ListSerializer(PairedPc.serializer()), raw)
                .sortedByDescending { it.lastConnected }
        } catch (_: Exception) {
            emptyList()
        }
    }

    val defaultServerIdFlow: Flow<String?> = context.pairedPcsDataStore.data.map { prefs ->
        prefs[Keys.DEFAULT_SERVER_ID]
    }

    suspend fun getOrCreateDeviceId(): String {
        val prefs = context.pairedPcsDataStore.data.first()
        val existing = prefs[Keys.DEVICE_ID]
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val created = UUID.randomUUID().toString()
        context.pairedPcsDataStore.edit { it[Keys.DEVICE_ID] = created }
        return created
    }

    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model.ifBlank { "Android" }
        } else {
            "$manufacturer $model".trim().ifBlank { "Android" }
        }
    }

    suspend fun getAll(): List<PairedPc> = pairedPcsFlow.first()

    suspend fun getLastOrDefault(): PairedPc? {
        val all = getAll()
        if (all.isEmpty()) return null
        val defId = defaultServerIdFlow.first()
        return all.firstOrNull { it.serverId == defId } ?: all.maxByOrNull { it.lastConnected }
    }

    suspend fun savePairedPc(
        serverId: String,
        name: String,
        host: String,
        port: Int,
        fp: String,
        rawToken: String
    ) {
        val encryptedToken = try {
            KeystoreCipher.encryptString(rawToken)
        } catch (_: Exception) {
            rawToken
        }

        val now = System.currentTimeMillis()
        context.pairedPcsDataStore.edit { prefs ->
            val current = decodeList(prefs[Keys.PAIRED_PCS_JSON]).toMutableList()
            current.removeAll { it.serverId == serverId }
            current.add(
                PairedPc(
                    serverId = serverId,
                    name = name,
                    host = host,
                    port = port,
                    fp = fp,
                    tokenEnc = encryptedToken,
                    lastConnected = now
                )
            )
            prefs[Keys.PAIRED_PCS_JSON] = json.encodeToString(ListSerializer(PairedPc.serializer()), current)
            prefs[Keys.DEFAULT_SERVER_ID] = serverId
        }
    }

    fun decryptToken(pc: PairedPc): String? {
        return try {
            KeystoreCipher.decryptString(pc.tokenEnc)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun updateHostAndTimestamp(serverId: String, newHost: String, newPort: Int = -1) {
        val now = System.currentTimeMillis()
        context.pairedPcsDataStore.edit { prefs ->
            val current = decodeList(prefs[Keys.PAIRED_PCS_JSON]).map { pc ->
                if (pc.serverId == serverId) {
                    pc.copy(
                        host = newHost,
                        port = if (newPort > 0) newPort else pc.port,
                        lastConnected = now
                    )
                } else {
                    pc
                }
            }
            prefs[Keys.PAIRED_PCS_JSON] = json.encodeToString(ListSerializer(PairedPc.serializer()), current)
        }
    }

    suspend fun setDefault(serverId: String) {
        context.pairedPcsDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_SERVER_ID] = serverId
        }
    }

    suspend fun forget(serverId: String) {
        context.pairedPcsDataStore.edit { prefs ->
            val current = decodeList(prefs[Keys.PAIRED_PCS_JSON]).filterNot { it.serverId == serverId }
            prefs[Keys.PAIRED_PCS_JSON] = json.encodeToString(ListSerializer(PairedPc.serializer()), current)
            if (prefs[Keys.DEFAULT_SERVER_ID] == serverId) {
                val next = current.maxByOrNull { it.lastConnected }?.serverId
                if (next != null) prefs[Keys.DEFAULT_SERVER_ID] = next
                else prefs.remove(Keys.DEFAULT_SERVER_ID)
            }
        }
    }

    private fun decodeList(raw: String?): List<PairedPc> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(PairedPc.serializer()), raw)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
