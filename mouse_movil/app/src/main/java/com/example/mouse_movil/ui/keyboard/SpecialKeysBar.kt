package com.example.mouse_movil.ui.keyboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SpecialKeysBar(
    stickyModifiers: Set<String>,
    onToggleModifier: (String) -> Unit,
    onSpecialKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showFunctionKeys by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val modifiers = listOf("CTRL", "ALT", "SHIFT", "WIN")
            for (mod in modifiers) {
                val active = stickyModifiers.contains(mod)
                FilterChip(
                    selected = active,
                    onClick = { onToggleModifier(mod) },
                    label = { Text(mod) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }

            val primaryKeys = listOf(
                "ESC" to "Esc",
                "TAB" to "Tab",
                "LEFT" to "←",
                "UP" to "↑",
                "DOWN" to "↓",
                "RIGHT" to "→",
                "DELETE" to "Supr",
                "HOME" to "Inicio",
                "END" to "Fin",
                "PAGE_UP" to "RePág",
                "PAGE_DOWN" to "AvPág",
                "ENTER" to "Enter"
            )

            for ((code, label) in primaryKeys) {
                OutlinedButton(onClick = { onSpecialKeyPress(code) }) {
                    Text(label)
                }
            }

            FilterChip(
                selected = showFunctionKeys,
                onClick = { showFunctionKeys = !showFunctionKeys },
                label = { Text("F1–F12") }
            )
        }

        if (showFunctionKeys) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (i in 1..12) {
                    val code = "F$i"
                    OutlinedButton(onClick = { onSpecialKeyPress(code) }) {
                        Text(code)
                    }
                }
            }
        }
    }
}
