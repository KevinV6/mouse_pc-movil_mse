package com.example.mouse_movil.di

import android.content.Context
import com.example.mouse_movil.core.network.ConnectionManager
import com.example.mouse_movil.core.network.DiscoveryClient
import com.example.mouse_movil.core.network.InputSender
import com.example.mouse_movil.data.PairedPcRepository
import com.example.mouse_movil.data.SettingsRepository
import com.example.mouse_movil.data.ShortcutRepository

class AppContainer(val appContext: Context) {
    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(appContext)
    }

    val pairedPcRepository: PairedPcRepository by lazy {
        PairedPcRepository(appContext)
    }

    val shortcutRepository: ShortcutRepository by lazy {
        ShortcutRepository(appContext)
    }

    val discoveryClient: DiscoveryClient by lazy {
        DiscoveryClient(appContext)
    }

    val connectionManager: ConnectionManager by lazy {
        ConnectionManager(pairedPcRepository, discoveryClient)
    }

    val inputSender: InputSender by lazy {
        InputSender(connectionManager)
    }
}
