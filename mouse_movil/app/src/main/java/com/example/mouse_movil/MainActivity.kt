package com.example.mouse_movil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mouse_movil.data.model.AppSettings
import com.example.mouse_movil.ui.navigation.MseNavGraph
import com.example.mouse_movil.ui.navigation.Routes
import com.example.mouse_movil.ui.theme.Mouse_movilTheme
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as MouseApp).container

        setContent {
            val settings by container.settingsRepository.settingsFlow.collectAsStateWithLifecycle(
                initialValue = AppSettings()
            )
            var startDestination by remember { mutableStateOf<String?>(null) }

            LaunchedEffect(Unit) {
                val currentSettings = container.settingsRepository.settingsFlow.first()
                val savedPc = container.pairedPcRepository.getLastOrDefault()
                startDestination = when {
                    !currentSettings.firstLaunchGuideSeen -> Routes.GUIDE
                    savedPc != null -> {
                        container.connectionManager.connectSaved(savedPc)
                        Routes.TRACKPAD
                    }
                    else -> Routes.CONNECT
                }
            }

            Mouse_movilTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor
            ) {
                val dest = startDestination
                if (dest != null) {
                    MseNavGraph(
                        container = container,
                        startDestination = dest
                    )
                }
            }
        }
    }
}
