package com.example.mouse_movil.ui.settings

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mouse_movil.data.PairedPcRepository
import com.example.mouse_movil.data.SettingsRepository
import com.example.mouse_movil.data.model.AppSettings
import com.example.mouse_movil.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    pairedPcRepository: PairedPcRepository,
    onOpenPairedPcs: () -> Unit,
    onOpenShortcutEditor: () -> Unit,
    onOpenGuide: () -> Unit,
    onBack: () -> Unit
) {
    val settings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(initialValue = AppSettings())
    val pairedPcs by pairedPcRepository.pairedPcsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var deviceIdShort by remember { mutableStateOf("...") }

    LaunchedEffect(Unit) {
        val fullId = pairedPcRepository.getOrCreateDeviceId()
        deviceIdShort = fullId.take(12)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Configuración") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Apariencia
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Apariencia y Tema",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val modes = listOf(
                            ThemeMode.SYSTEM to "Sistema",
                            ThemeMode.LIGHT to "Modo Claro",
                            ThemeMode.DARK to "Modo Oscuro"
                        )
                        for ((mode, label) in modes) {
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { scope.launch { settingsRepository.setThemeMode(mode) } },
                                label = { Text(label) }
                            )
                        }
                    }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        SwitchSettingRow(
                            title = "Colores dinámicos (Material You)",
                            subtitle = "Usar la paleta del fondo de pantalla de Android 12+",
                            checked = settings.dynamicColor,
                            onCheckedChange = { scope.launch { settingsRepository.setDynamicColor(it) } }
                        )
                    }
                }
            }

            // 2. Trackpad
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Trackpad y Cursor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    SliderSettingRow(
                        title = "Sensibilidad del cursor",
                        valueText = String.format(Locale.US, "%.1fx", settings.sensitivity),
                        value = settings.sensitivity,
                        valueRange = 0.4f..3.0f,
                        onValueChange = { scope.launch { settingsRepository.setSensitivity(it) } }
                    )

                    SliderSettingRow(
                        title = "Aceleración dinámica",
                        valueText = "${(settings.acceleration * 100).toInt()} %",
                        value = settings.acceleration,
                        valueRange = 0.0f..1.0f,
                        onValueChange = { scope.launch { settingsRepository.setAcceleration(it) } }
                    )

                    SwitchSettingRow(
                        title = "Mostrar botones Izquierdo / Derecho",
                        subtitle = "Barra dedicada debajo del área táctil",
                        checked = settings.showMouseButtons,
                        onCheckedChange = { scope.launch { settingsRepository.setShowMouseButtons(it) } }
                    )

                    SwitchSettingRow(
                        title = "Respuesta háptica (vibración)",
                        subtitle = "Vibrar al hacer clic, zoom o iniciar arrastre",
                        checked = settings.haptics,
                        onCheckedChange = { scope.launch { settingsRepository.setHaptics(it) } }
                    )

                    SwitchSettingRow(
                        title = "Mantener pantalla encendida",
                        subtitle = "Evitar bloqueo mientras usas el trackpad",
                        checked = settings.keepScreenOn,
                        onCheckedChange = { scope.launch { settingsRepository.setKeepScreenOn(it) } }
                    )
                }
            }

            // 3. Scroll
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Desplazamiento (Scroll a 2 dedos)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    SliderSettingRow(
                        title = "Velocidad de scroll",
                        valueText = String.format(Locale.US, "%.1fx", settings.scrollSpeed),
                        value = settings.scrollSpeed,
                        valueRange = 0.4f..3.0f,
                        onValueChange = { scope.launch { settingsRepository.setScrollSpeed(it) } }
                    )

                    SwitchSettingRow(
                        title = "Dirección natural",
                        subtitle = "El contenido sigue el movimiento de tus dedos",
                        checked = settings.naturalScroll,
                        onCheckedChange = { scope.launch { settingsRepository.setNaturalScroll(it) } }
                    )
                }
            }

            // 4. Navigation items (PCs, Shortcuts, Guide)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPairedPcs)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("PCs vinculadas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${pairedPcs.size} dispositivo(s) de confianza guardado(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenShortcutEditor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Editor de atajos personalizados", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Crear, editar y ordenar tus combinaciones de teclas",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenGuide)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Guía de Uso Visual", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "Aprende todos los gestos de 1, 2 y 3 dedos, QR/PIN y atajos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }

            // 5. About
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Acerca de MSE Mouse", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Versión 1.0 · Protocolo v1 (TLS 1.3 + TCP_NODELAY)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "ID de dispositivo: $deviceIdShort…",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SliderSettingRow(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(text = valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

@Composable
private fun SwitchSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
