package com.example.mouse_movil.ui.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.mouse_movil.core.network.InputSender

@Composable
fun KeyboardPanel(
    inputSender: InputSender,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    var stickyModifiers by remember { mutableStateOf(setOf<String>()) }
    var fieldValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = RealtimeKeyboardDiffer.SENTINEL,
                selection = TextRange(RealtimeKeyboardDiffer.SENTINEL.length)
            )
        )
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
        }
    }

    fun dispatchKeyOrCombo(mainKey: String) {
        if (stickyModifiers.isNotEmpty()) {
            val combo = stickyModifiers.toList() + mainKey
            inputSender.sendCombo(combo)
            stickyModifiers = emptySet()
        } else {
            inputSender.sendKey(mainKey, "press")
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Teclado en tiempo real",
            style = MaterialTheme.typography.titleMedium
        )

        SpecialKeysBar(
            stickyModifiers = stickyModifiers,
            onToggleModifier = { mod ->
                stickyModifiers = if (stickyModifiers.contains(mod)) {
                    stickyModifiers - mod
                } else {
                    stickyModifiers + mod
                }
            },
            onSpecialKeyPress = { key -> dispatchKeyOrCombo(key) }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = fieldValue,
                onValueChange = { newValue ->
                    val actions = RealtimeKeyboardDiffer.diff(fieldValue.text, newValue.text)
                    for (action in actions) {
                        when (action) {
                            is KeyboardDiffAction.SendText -> {
                                if (stickyModifiers.isNotEmpty() && action.text.length == 1) {
                                    val ch = action.text[0].uppercaseChar()
                                    if (ch in 'A'..'Z' || ch in '0'..'9') {
                                        dispatchKeyOrCombo(ch.toString())
                                    } else {
                                        inputSender.sendText(action.text)
                                    }
                                } else {
                                    inputSender.sendText(action.text)
                                }
                            }
                            is KeyboardDiffAction.SendBackspaces -> {
                                repeat(action.count) {
                                    inputSender.sendKey("BACKSPACE", "press")
                                }
                            }
                            is KeyboardDiffAction.SendEnter -> {
                                dispatchKeyOrCombo("ENTER")
                            }
                        }
                    }

                    // Keep the sentinel prefix so Backspace always works even on an empty line
                    val normalized = if (newValue.text.isEmpty()) {
                        RealtimeKeyboardDiffer.SENTINEL
                    } else if (!newValue.text.startsWith(RealtimeKeyboardDiffer.SENTINEL)) {
                        RealtimeKeyboardDiffer.SENTINEL + newValue.text
                    } else {
                        // Trim buffer when it grows long to keep diff O(1)
                        val body = newValue.text.removePrefix(RealtimeKeyboardDiffer.SENTINEL)
                        val tail = if (body.length > 40) body.takeLast(20) else body
                        RealtimeKeyboardDiffer.SENTINEL + tail
                    }

                    fieldValue = TextFieldValue(
                        text = normalized,
                        selection = TextRange(normalized.length)
                    )
                },
                label = {
                    val hint = if (stickyModifiers.isEmpty()) {
                        "Escribe aquí para enviar a la PC…"
                    } else {
                        "Modificador activo: ${stickyModifiers.joinToString("+")} + tecla"
                    }
                    Text(hint)
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { dispatchKeyOrCombo("ENTER") }
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )

            FilledTonalIconButton(onClick = { dispatchKeyOrCombo("BACKSPACE") }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "Retroceso"
                )
            }

            FilledTonalIconButton(onClick = { dispatchKeyOrCombo("ENTER") }) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                    contentDescription = "Enter"
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
