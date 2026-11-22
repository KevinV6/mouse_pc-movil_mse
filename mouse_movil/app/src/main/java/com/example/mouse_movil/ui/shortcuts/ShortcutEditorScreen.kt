package com.example.mouse_movil.ui.shortcuts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mouse_movil.core.network.InputSender
import com.example.mouse_movil.data.ShortcutRepository
import com.example.mouse_movil.data.model.Shortcut
import com.example.mouse_movil.ui.controls.resolveShortcutIcon
import kotlinx.coroutines.launch
import java.util.UUID

object ShortcutValidator {
    val MODIFIERS = setOf("CTRL", "ALT", "SHIFT", "WIN")

    val AVAILABLE_MAIN_KEYS: List<String> = buildList {
        addAll(('A'..'Z').map { it.toString() })
        addAll(('0'..'9').map { it.toString() })
        addAll((1..12).map { "F$it" })
        addAll(
            listOf(
                "ENTER", "ESC", "TAB", "SPACE", "BACKSPACE", "DELETE", "INSERT",
                "UP", "DOWN", "LEFT", "RIGHT", "HOME", "END", "PAGE_UP", "PAGE_DOWN",
                "PRINT_SCREEN", "CAPS_LOCK", "CONTEXT_MENU"
            )
        )
    }

    val AVAILABLE_ICONS = listOf("Bolt", "Terminal", "Screenshot", "Folder", "Desktop", "Lock")

    fun isValid(name: String, keys: List<String>): Boolean {
        if (name.trim().isEmpty()) return false
        if (keys.isEmpty()) return false
        // Valid if at least one non-modifier key OR single WIN key
        val hasMainKey = keys.any { !MODIFIERS.contains(it.uppercase()) }
        return hasMainKey || (keys.size == 1 && keys[0].equals("WIN", ignoreCase = true))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutEditorScreen(
    shortcutRepository: ShortcutRepository,
    inputSender: InputSender,
    onBack: () -> Unit
) {
    val shortcuts by shortcutRepository.shortcutsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var editingShortcut by remember { mutableStateOf<Shortcut?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    if (isCreatingNew || editingShortcut != null) {
        ShortcutEditDialog(
            initial = editingShortcut,
            onDismiss = {
                editingShortcut = null
                isCreatingNew = false
            },
            onTestCombo = { keys -> inputSender.sendCombo(keys) },
            onSave = { saved ->
                scope.launch {
                    shortcutRepository.upsert(saved)
                }
                editingShortcut = null
                isCreatingNew = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Atajos personalizados") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingShortcut = null
                    isCreatingNew = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo atajo")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Crea tus propias combinaciones de teclas para tus programas favoritos y ordénalas a tu gusto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            itemsIndexed(shortcuts, key = { _, item -> item.id }) { index, sc ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = resolveShortcutIcon(sc.iconName),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = sc.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = sc.keys.joinToString(" + "),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(onClick = { inputSender.sendCombo(sc.keys) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Probar atajo")
                        }
                        IconButton(
                            enabled = index > 0,
                            onClick = { scope.launch { shortcutRepository.move(index, index - 1) } }
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Subir")
                        }
                        IconButton(
                            enabled = index < shortcuts.lastIndex,
                            onClick = { scope.launch { shortcutRepository.move(index, index + 1) } }
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Bajar")
                        }
                        IconButton(onClick = { editingShortcut = sc }) {
                            Icon(Icons.Default.Edit, contentDescription = "Editar")
                        }
                        IconButton(
                            onClick = { scope.launch { shortcutRepository.delete(sc.id) } }
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Eliminar")
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortcutEditDialog(
    initial: Shortcut?,
    onDismiss: () -> Unit,
    onTestCombo: (List<String>) -> Unit,
    onSave: (Shortcut) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var iconName by remember { mutableStateOf(initial?.iconName ?: "Bolt") }

    val initialModifiers = remember(initial) {
        initial?.keys?.filter { ShortcutValidator.MODIFIERS.contains(it) }?.toSet() ?: setOf("CTRL")
    }
    val initialMainKey = remember(initial) {
        initial?.keys?.firstOrNull { !ShortcutValidator.MODIFIERS.contains(it) } ?: "C"
    }

    var selectedModifiers by remember { mutableStateOf(initialModifiers) }
    var selectedMainKey by remember { mutableStateOf(initialMainKey) }
    var keyFilter by remember { mutableStateOf("") }

    val builtKeys = remember(selectedModifiers, selectedMainKey) {
        val orderedMods = listOf("CTRL", "ALT", "SHIFT", "WIN").filter { selectedModifiers.contains(it) }
        if (selectedMainKey.isNotBlank()) orderedMods + selectedMainKey else orderedMods
    }

    val isValid = ShortcutValidator.isValid(name, builtKeys)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo atajo" else "Editar atajo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre (ej. Guardar todo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Ícono:",
                    style = MaterialTheme.typography.labelMedium
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (ic in ShortcutValidator.AVAILABLE_ICONS) {
                        FilterChip(
                            selected = iconName == ic,
                            onClick = { iconName = ic },
                            label = { Text(ic) },
                            leadingIcon = {
                                Icon(resolveShortcutIcon(ic), contentDescription = null)
                            }
                        )
                    }
                }

                Text(
                    text = "Modificadores:",
                    style = MaterialTheme.typography.labelMedium
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (mod in listOf("CTRL", "ALT", "SHIFT", "WIN")) {
                        val active = selectedModifiers.contains(mod)
                        FilterChip(
                            selected = active,
                            onClick = {
                                selectedModifiers = if (active) selectedModifiers - mod else selectedModifiers + mod
                            },
                            label = { Text(mod) }
                        )
                    }
                }

                OutlinedTextField(
                    value = keyFilter,
                    onValueChange = { keyFilter = it },
                    label = { Text("Filtrar tecla principal (actual: $selectedMainKey)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val filteredKeys = remember(keyFilter) {
                    val q = keyFilter.trim().uppercase()
                    if (q.isEmpty()) ShortcutValidator.AVAILABLE_MAIN_KEYS.take(18)
                    else ShortcutValidator.AVAILABLE_MAIN_KEYS.filter { it.contains(q) }.take(18)
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (k in filteredKeys) {
                        FilterChip(
                            selected = selectedMainKey == k,
                            onClick = { selectedMainKey = k },
                            label = { Text(k) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Combo: ${builtKeys.joinToString(" + ")}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(
                        enabled = builtKeys.isNotEmpty(),
                        onClick = { onTestCombo(builtKeys) }
                    ) {
                        Text("Probar")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = isValid,
                onClick = {
                    onSave(
                        Shortcut(
                            id = initial?.id ?: "sc-${UUID.randomUUID().toString().take(8)}",
                            name = name.trim(),
                            iconName = iconName,
                            keys = builtKeys
                        )
                    )
                }
            ) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
