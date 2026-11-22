package com.example.mouse_movil.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mouse_movil.core.protocol.ProtocolConstants

@Composable
fun ManualIpDialog(
    onDismiss: () -> Unit,
    onConnect: (host: String, port: Int) -> Unit
) {
    var host by rememberSaveable { mutableStateOf("") }
    var portText by rememberSaveable { mutableStateOf(ProtocolConstants.TCP_PORT.toString()) }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Conexión por IP manual") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Introduce la IP local y el puerto que muestra la ventana de MSE Receptor en tu PC.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = {
                        host = it
                        errorText = null
                    },
                    label = { Text("Dirección IPv4 (ej. 192.168.1.20)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = {
                        portText = it.filter { ch -> ch.isDigit() }
                        errorText = null
                    },
                    label = { Text("Puerto TCP") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = errorText!!,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedHost = host.trim()
                    val port = portText.toIntOrNull() ?: -1
                    if (trimmedHost.isEmpty()) {
                        errorText = "Ingresa una dirección IP válida."
                    } else if (port !in 1..65535) {
                        errorText = "Puerto fuera de rango (1-65535)."
                    } else {
                        onConnect(trimmedHost, port)
                    }
                }
            ) {
                Text("Continuar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
