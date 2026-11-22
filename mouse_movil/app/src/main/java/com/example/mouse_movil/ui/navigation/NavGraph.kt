package com.example.mouse_movil.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mouse_movil.ui.theme.ThemeMode

@Composable
fun MseNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.CONNECT,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.CONNECT) {
            PlaceholderScreen(
                title = "Conectar a PC",
                subtitle = "Búsqueda automática UDP, escaneo QR e IP manual",
                icon = Icons.Default.Phonelink,
                primaryActionLabel = "Ir a Trackpad",
                onPrimaryAction = { navController.navigate(Routes.TRACKPAD) },
                onNavigateGuide = { navController.navigate(Routes.GUIDE) },
                onNavigateSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.TRACKPAD) {
            PlaceholderScreen(
                title = "Trackpad MSE",
                subtitle = "Superficie táctil multi-gesto, teclado y controles",
                icon = Icons.Default.TouchApp,
                primaryActionLabel = "Editor de Atajos",
                onPrimaryAction = { navController.navigate(Routes.SHORTCUT_EDITOR) },
                onNavigateGuide = { navController.navigate(Routes.GUIDE) },
                onNavigateSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.SETTINGS) {
            PlaceholderScreen(
                title = "Configuración",
                subtitle = "Tema actual: ${themeMode.name}",
                icon = Icons.Default.Settings,
                primaryActionLabel = "Alternar Tema (Sistema / Claro / Oscuro)",
                onPrimaryAction = {
                    val next = when (themeMode) {
                        ThemeMode.SYSTEM -> ThemeMode.LIGHT
                        ThemeMode.LIGHT -> ThemeMode.DARK
                        ThemeMode.DARK -> ThemeMode.SYSTEM
                    }
                    onThemeModeChange(next)
                },
                secondaryActionLabel = "Volver",
                onSecondaryAction = { navController.popBackStack() },
                onNavigateGuide = { navController.navigate(Routes.GUIDE) }
            )
        }
        composable(Routes.GUIDE) {
            PlaceholderScreen(
                title = "Guía de Uso",
                subtitle = "Gestos de 1, 2 y 3 dedos, emparejamiento QR/PIN y atajos",
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                primaryActionLabel = "Volver",
                onPrimaryAction = { navController.popBackStack() }
            )
        }
        composable(Routes.SHORTCUT_EDITOR) {
            PlaceholderScreen(
                title = "Atajos Personalizados",
                subtitle = "Crea y ordena combinaciones de teclas para Windows",
                icon = Icons.Default.Keyboard,
                primaryActionLabel = "Volver",
                onPrimaryAction = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceholderScreen(
    title: String,
    subtitle: String,
    icon: ImageVector,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    onNavigateGuide: (() -> Unit)? = null,
    onNavigateSettings: (() -> Unit)? = null
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                actions = {
                    if (onNavigateGuide != null) {
                        IconButton(onClick = onNavigateGuide) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = "Guía de uso"
                            )
                        }
                    }
                    if (onNavigateSettings != null) {
                        IconButton(onClick = onNavigateSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Ajustes"
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = onPrimaryAction) {
                            Text(text = primaryActionLabel)
                        }
                        if (secondaryActionLabel != null && onSecondaryAction != null) {
                            FilledTonalButton(onClick = onSecondaryAction) {
                                Text(text = secondaryActionLabel)
                            }
                        }
                    }
                }
            }
        }
    }
}
