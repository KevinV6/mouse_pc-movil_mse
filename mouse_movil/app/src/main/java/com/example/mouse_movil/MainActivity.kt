package com.example.mouse_movil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.mouse_movil.ui.navigation.MseNavGraph
import com.example.mouse_movil.ui.theme.Mouse_movilTheme
import com.example.mouse_movil.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var themeMode by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM) }
            Mouse_movilTheme(themeMode = themeMode) {
                MseNavGraph(
                    themeMode = themeMode,
                    onThemeModeChange = { themeMode = it }
                )
            }
        }
    }
}
