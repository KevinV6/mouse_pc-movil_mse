package com.example.mouse_movil.ui.trackpad

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mouse_movil.core.network.ConnectionManager
import com.example.mouse_movil.core.network.ConnectionState
import com.example.mouse_movil.core.network.InputSender
import com.example.mouse_movil.data.SettingsRepository
import com.example.mouse_movil.data.ShortcutRepository
import com.example.mouse_movil.data.model.AppSettings
import com.example.mouse_movil.ui.controls.MediaShortcutsPanel
import com.example.mouse_movil.ui.keyboard.KeyboardPanel
import com.example.mouse_movil.ui.theme.MseStatusConnected
import com.example.mouse_movil.ui.theme.MseStatusError
import com.example.mouse_movil.ui.theme.MseStatusWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackpadScreen(
    connectionManager: ConnectionManager,
    inputSender: InputSender,
    settingsRepository: SettingsRepository,
    shortcutRepository: ShortcutRepository,
    onNavigateConnect: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateShortcutEditor: () -> Unit
) {
    val connectionState by connectionManager.state.collectAsStateWithLifecycle()
    val settings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val shortcuts by shortcutRepository.shortcutsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val accumulator = remember { SubPixelAccumulator() }

    var showKeyboardSheet by rememberSaveable { mutableStateOf(false) }
    var showControlsSheet by rememberSaveable { mutableStateOf(false) }
    var isDraggingActive by remember { mutableStateOf(false) }

    // Keep screen on while using trackpad if enabled
    DisposableEffect(settings.keepScreenOn) {
        val window = (context as? Activity)?.window
        if (settings.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val callbacks = remember(settings, inputSender) {
        TrackpadGestureCallbacks(
            onMove = { rawDx, rawDy, dtMs ->
                val (scaledX, scaledY) = GestureMath.applyPointerAcceleration(
                    rawDx = rawDx,
                    rawDy = rawDy,
                    dtMs = dtMs,
                    sensitivity = settings.sensitivity,
                    acceleration = settings.acceleration
                )
                val (intDx, intDy) = accumulator.accumulate(scaledX, scaledY)
                inputSender.queueMove(intDx, intDy)
            },
            onClick = { button, count ->
                if (settings.haptics) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                inputSender.sendClick(button, count)
            },
            onScroll = { panDx, panDy ->
                val (wheelX, wheelY) = GestureMath.computeScrollWheelUnits(
                    panDxPx = panDx,
                    panDyPx = panDy,
                    scrollSpeed = settings.scrollSpeed,
                    naturalScroll = settings.naturalScroll
                )
                inputSender.queueScroll(wheelX, wheelY)
            },
            onZoom = { steps ->
                if (settings.haptics) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                inputSender.sendZoom(steps)
            },
            onDragStart = {
                isDraggingActive = true
                if (settings.haptics) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                inputSender.sendButton("left", "down")
            },
            onDragEnd = {
                isDraggingActive = false
                inputSender.sendButton("left", "up")
            }
        )
    }

    if (showKeyboardSheet) {
        ModalBottomSheet(
            onDismissRequest = { showKeyboardSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            KeyboardPanel(inputSender = inputSender)
        }
    }

    if (showControlsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showControlsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            MediaShortcutsPanel(
                inputSender = inputSender,
                customShortcuts = shortcuts,
                onOpenShortcutEditor = {
                    showControlsSheet = false
                    onNavigateShortcutEditor()
                }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Top status chip
        ConnectionTopBar(
            state = connectionState,
            isDragging = isDraggingActive,
            onTapStatus = onNavigateConnect
        )

        // Main Trackpad surface
        val dotColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.14f)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .trackpadGestures(callbacks)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val step = 28.dp.toPx()
                val radius = 1.6.dp.toPx()
                var x = step
                while (x < size.width - step * 0.5f) {
                    var y = step
                    while (y < size.height - step * 0.5f) {
                        drawCircle(color = dotColor, radius = radius, center = Offset(x, y))
                        y += step
                    }
                    x += step
                }
            }

            // Subtle watermark
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isDraggingActive) "ARRASTRANDO…" else "TRACKPAD MULTIGESTO",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
                )
            }

            // Reconnecting / Disconnected overlay banner
            if (connectionState !is ConnectionState.Connected) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        val msg = when (val st = connectionState) {
                            is ConnectionState.Reconnecting -> "Reconectando con ${st.pcName} (#${st.attempt})…"
                            is ConnectionState.Connecting -> "Conectando con ${st.targetName}…"
                            is ConnectionState.Error -> st.reason
                            else -> "Sin conexión activa con la PC"
                        }
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Button(onClick = onNavigateConnect) {
                            Text("Conectar")
                        }
                    }
                }
            }
        }

        // Optional dedicated mouse buttons
        if (settings.showMouseButtons) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = {
                        if (settings.haptics) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        inputSender.sendClick("left", 1)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Clic Izquierdo")
                }

                FilledTonalButton(
                    onClick = {
                        if (settings.haptics) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        inputSender.sendClick("right", 1)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Clic Derecho")
                }
            }
        }

        // Discrete bottom bar
        BottomBar(
            onOpenKeyboard = { showKeyboardSheet = true },
            onOpenControls = { showControlsSheet = true },
            onOpenSettings = onNavigateSettings
        )
    }
}

@Composable
private fun ConnectionTopBar(
    state: ConnectionState,
    isDragging: Boolean,
    onTapStatus: () -> Unit
) {
    val (dotColor, title, badge) = when (state) {
        is ConnectionState.Connected -> Triple(
            MseStatusConnected,
            state.pcName,
            if (state.rttMs > 0) "${state.rttMs} ms" else "TLS Activo"
        )
        is ConnectionState.Connecting -> Triple(
            MseStatusWarning,
            "Conectando a ${state.targetName}…",
            "TLS"
        )
        is ConnectionState.Reconnecting -> Triple(
            MseStatusWarning,
            "Reconectando (${state.pcName})",
            "#${state.attempt}"
        )
        is ConnectionState.Error -> Triple(
            MseStatusError,
            "Desconectado",
            "Toca para vincular"
        )
        else -> Triple(
            MseStatusWarning,
            "Sin PC conectada",
            "Toca para vincular"
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onTapStatus),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (isDragging) {
                Text(
                    text = "DRAG",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Text(
                text = badge,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
