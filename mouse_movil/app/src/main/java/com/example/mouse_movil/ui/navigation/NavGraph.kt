package com.example.mouse_movil.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mouse_movil.di.AppContainer
import com.example.mouse_movil.ui.connect.ConnectScreen
import com.example.mouse_movil.ui.connect.QrScannerScreen
import com.example.mouse_movil.ui.guide.GuideScreen
import com.example.mouse_movil.ui.settings.PairedPcsScreen
import com.example.mouse_movil.ui.settings.SettingsScreen
import com.example.mouse_movil.ui.shortcuts.ShortcutEditorScreen
import com.example.mouse_movil.ui.trackpad.TrackpadScreen

@Composable
fun MseNavGraph(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.CONNECT
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.CONNECT) {
            ConnectScreen(
                connectionManager = container.connectionManager,
                pairedPcRepository = container.pairedPcRepository,
                onConnectedNavigateToTrackpad = {
                    navController.navigate(Routes.TRACKPAD) {
                        popUpTo(Routes.CONNECT) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onOpenQrScanner = { navController.navigate(Routes.QR_SCANNER) },
                onOpenGuide = { navController.navigate(Routes.GUIDE) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }

        composable(Routes.QR_SCANNER) {
            QrScannerScreen(
                onQrDetected = { rawUri ->
                    navController.popBackStack()
                    container.connectionManager.pairWithQr(rawUri)
                },
                onClose = { navController.popBackStack() }
            )
        }

        composable(Routes.TRACKPAD) {
            TrackpadScreen(
                connectionManager = container.connectionManager,
                inputSender = container.inputSender,
                settingsRepository = container.settingsRepository,
                shortcutRepository = container.shortcutRepository,
                onNavigateConnect = {
                    navController.navigate(Routes.CONNECT) {
                        launchSingleTop = true
                    }
                },
                onNavigateSettings = { navController.navigate(Routes.SETTINGS) },
                onNavigateShortcutEditor = { navController.navigate(Routes.SHORTCUT_EDITOR) }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                settingsRepository = container.settingsRepository,
                pairedPcRepository = container.pairedPcRepository,
                onOpenPairedPcs = { navController.navigate(Routes.PAIRED_PCS) },
                onOpenShortcutEditor = { navController.navigate(Routes.SHORTCUT_EDITOR) },
                onOpenGuide = { navController.navigate(Routes.GUIDE) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PAIRED_PCS) {
            PairedPcsScreen(
                pairedPcRepository = container.pairedPcRepository,
                connectionManager = container.connectionManager,
                onNavigateConnect = {
                    navController.navigate(Routes.CONNECT) {
                        launchSingleTop = true
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.GUIDE) {
            GuideScreen(
                settingsRepository = container.settingsRepository,
                onClose = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.CONNECT) {
                            popUpTo(Routes.GUIDE) { inclusive = true }
                        }
                    }
                }
            )
        }

        composable(Routes.SHORTCUT_EDITOR) {
            ShortcutEditorScreen(
                shortcutRepository = container.shortcutRepository,
                inputSender = container.inputSender,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
