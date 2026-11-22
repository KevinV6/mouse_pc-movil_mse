package com.example.mouse_movil.ui.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mouse_movil.core.network.InputSender
import com.example.mouse_movil.data.model.Shortcut

private data class PendingPowerAction(
    val code: String,
    val title: String,
    val description: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaShortcutsPanel(
    inputSender: InputSender,
    customShortcuts: List<Shortcut>,
    onOpenShortcutEditor: () -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingPower by remember { mutableStateOf<PendingPowerAction?>(null) }

    if (pendingPower != null) {
        val action = pendingPower!!
        AlertDialog(
            onDismissRequest = { pendingPower = null },
            title = { Text(action.title) },
            text = { Text(action.description) },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        inputSender.sendSystem(action.code, confirm = true)
                        pendingPower = null
                    }
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingPower = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Multimedia
        Text(
            text = "Control Multimedia",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { inputSender.sendMedia("vol_down") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = "Bajar volumen")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Vol −")
            }

            OutlinedButton(
                onClick = { inputSender.sendMedia("mute") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeOff, contentDescription = "Silenciar")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Mute")
            }

            FilledTonalButton(
                onClick = { inputSender.sendMedia("vol_up") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Subir volumen")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Vol +")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { inputSender.sendMedia("prev") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Anterior")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Ant.")
            }

            Button(
                onClick = { inputSender.sendMedia("play_pause") },
                modifier = Modifier.weight(1.2f)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play / Pausa")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Play / Pausa")
            }

            OutlinedButton(
                onClick = { inputSender.sendMedia("next") },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.SkipNext, contentDescription = "Siguiente")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Sig.")
            }
        }

        // 2. Quick predefined shortcuts
        Text(
            text = "Atajos rápidos de Windows",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickComboChip("Alt + Tab", Icons.Default.SwapHoriz) {
                inputSender.sendCombo(listOf("ALT", "TAB"))
            }
            QuickComboChip("Copiar", Icons.Default.ContentCopy) {
                inputSender.sendCombo(listOf("CTRL", "C"))
            }
            QuickComboChip("Pegar", Icons.Default.ContentPaste) {
                inputSender.sendCombo(listOf("CTRL", "V"))
            }
            QuickComboChip("Cortar", Icons.Default.ContentCut) {
                inputSender.sendCombo(listOf("CTRL", "X"))
            }
            QuickComboChip("Deshacer", Icons.AutoMirrored.Filled.Undo) {
                inputSender.sendCombo(listOf("CTRL", "Z"))
            }
            QuickComboChip("Escritorio", Icons.Default.DesktopWindows) {
                inputSender.sendCombo(listOf("WIN", "D"))
            }
        }

        // 3. Custom shortcuts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Mis atajos personalizados",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = onOpenShortcutEditor) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Gestionar")
            }
        }

        if (customShortcuts.isEmpty()) {
            Text(
                text = "No tienes atajos personalizados aún. Toca «Gestionar» para crear uno.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (sc in customShortcuts) {
                    AssistChip(
                        onClick = { inputSender.sendCombo(sc.keys) },
                        label = { Text("${sc.name} (${sc.keys.joinToString("+")})") },
                        leadingIcon = {
                            Icon(resolveShortcutIcon(sc.iconName), contentDescription = null)
                        }
                    )
                }
            }
        }

        // 4. Power & Lock
        Text(
            text = "Energía y sesión de la PC",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                onClick = { inputSender.sendSystem("lock", confirm = true) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Bloquear")
            }

            OutlinedButton(
                onClick = {
                    pendingPower = PendingPowerAction(
                        code = "sleep",
                        title = "Suspender PC",
                        description = "¿Seguro que deseas poner tu PC Windows en modo suspensión?"
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Nightlight, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Suspender")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    pendingPower = PendingPowerAction(
                        code = "restart",
                        title = "Reiniciar PC",
                        description = "¿Confirmas que deseas reiniciar Windows inmediatamente?"
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reiniciar")
            }

            OutlinedButton(
                onClick = {
                    pendingPower = PendingPowerAction(
                        code = "shutdown",
                        title = "Apagar PC",
                        description = "¿Confirmas que deseas apagar tu PC Windows por completo?"
                    )
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.PowerSettingsNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Apagar", color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun QuickComboChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) }
    )
}

fun resolveShortcutIcon(iconName: String): ImageVector = when (iconName) {
    "Terminal" -> Icons.Default.Terminal
    "Screenshot" -> Icons.Default.Screenshot
    "Folder" -> Icons.Default.Folder
    "Desktop" -> Icons.Default.DesktopWindows
    "Lock" -> Icons.Default.Lock
    else -> Icons.Default.Bolt
}
