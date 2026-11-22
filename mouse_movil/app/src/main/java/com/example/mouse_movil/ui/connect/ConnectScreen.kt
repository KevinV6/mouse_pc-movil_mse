package com.example.mouse_movil.ui.connect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.mouse_movil.core.network.ConnectionManager
import com.example.mouse_movil.core.network.ConnectionState
import com.example.mouse_movil.core.network.DiscoveredPc
import com.example.mouse_movil.data.PairedPcRepository
import com.example.mouse_movil.data.model.PairedPc
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(
    connectionManager: ConnectionManager,
    pairedPcRepository: PairedPcRepository,
    onConnectedNavigateToTrackpad: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val state by connectionManager.state.collectAsStateWithLifecycle()
    val discovered by connectionManager.discoveredPcs.collectAsStateWithLifecycle()
    val pairedPcs by pairedPcRepository.pairedPcsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    var showManualDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        connectionManager.scanNetwork()
    }

    LaunchedEffect(state) {
        if (state is ConnectionState.Connected) {
            onConnectedNavigateToTrackpad()
        }
    }

    if (showManualDialog) {
        ManualIpDialog(
            onDismiss = { showManualDialog = false },
            onConnect = { host, port ->
                showManualDialog = false
                connectionManager.preparePinPairing(host = host, port = port)
            }
        )
    }

    val awaitingPin = state as? ConnectionState.AwaitingPin
    if (awaitingPin != null) {
        PinEntryDialog(
            state = awaitingPin,
            onDismiss = { connectionManager.cancelPinPrompt() },
            onSubmitPin = { pin -> connectionManager.submitPin(pin) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MSE Mouse · Conectar") },
                actions = {
                    IconButton(onClick = { connectionManager.scanNetwork() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Buscar PCs en la red")
                    }
                    IconButton(onClick = onOpenGuide) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Guía de uso")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Configuración")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                ConnectionStatusBanner(state = state)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onOpenQrScanner,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Escanear QR")
                    }

                    OutlinedButton(
                        onClick = { showManualDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.EditNote, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("IP Manual")
                    }
                }
            }

            if (pairedPcs.isNotEmpty()) {
                item {
                    Text(
                        text = "PCs vinculadas (confianza)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(pairedPcs, key = { it.serverId }) { pc ->
                    PairedPcRow(
                        pc = pc,
                        onConnect = { connectionManager.connectSaved(pc) },
                        onForget = {
                            scope.launch { pairedPcRepository.forget(pc.serverId) }
                        }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PCs detectadas en tu Wi-Fi",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (state is ConnectionState.Discovering) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }

            if (discovered.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "No se detectaron PCs automáticamente.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Asegúrate de que MSE Receptor esté abierto en Windows y en la misma red Wi-Fi, o usa «Escanear QR» / «IP Manual».",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(discovered, key = { it.serverId }) { pc ->
                    DiscoveredPcRow(
                        pc = pc,
                        onClick = {
                            val alreadyPaired = pairedPcs.firstOrNull { it.serverId == pc.serverId }
                            if (alreadyPaired != null) {
                                connectionManager.connectSaved(
                                    alreadyPaired.copy(host = pc.host, port = pc.port)
                                )
                            } else {
                                connectionManager.preparePinPairing(
                                    host = pc.host,
                                    port = pc.port,
                                    knownFp = pc.fingerprint,
                                    displayName = pc.serverName
                                )
                            }
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ConnectionStatusBanner(state: ConnectionState) {
    val (title, subtitle, isError) = when (state) {
        is ConnectionState.Idle -> Triple(
            "Listo para conectar",
            "Selecciona tu PC, escanea el código QR o ingresa la IP.",
            false
        )
        is ConnectionState.Discovering -> Triple(
            "Buscando PCs en la red local…",
            "Enviando consulta UDP al puerto 47801",
            false
        )
        is ConnectionState.Connecting -> Triple(
            "Conectando con ${state.targetName}…",
            "Estableciendo canal TLS seguro",
            false
        )
        is ConnectionState.Reconnecting -> Triple(
            "Reconectando con ${state.pcName} (intento #${state.attempt})…",
            "Buscando servidor en la red Wi-Fi",
            false
        )
        is ConnectionState.Connected -> Triple(
            "Conectado a ${state.pcName}",
            "${state.host} · RTT ${state.rttMs} ms",
            false
        )
        is ConnectionState.AwaitingPin -> Triple(
            "Verificando PIN con ${state.serverName}",
            "Código: ${state.verificationCode}",
            false
        )
        is ConnectionState.Error -> Triple(
            "Atención",
            state.reason,
            true
        )
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun PairedPcRow(
    pc: PairedPc,
    onConnect: () -> Unit,
    onForget: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onConnect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Computer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = pc.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${pc.host}:${pc.port}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(onClick = onConnect) {
                Text("Conectar")
            }
            IconButton(onClick = onForget) {
                Icon(Icons.Default.DeleteOutline, contentDescription = "Olvidar PC")
            }
        }
    }
}

@Composable
private fun DiscoveredPcRow(
    pc: DiscoveredPc,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = pc.serverName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "${pc.host}:${pc.port} · ${if (pc.pairingOpen) "Disponible" else "Emparejamiento cerrado"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(onClick = onClick) {
                Text("Vincular")
            }
        }
    }
}
