package com.example.mouse_movil.core.network

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.mouse_movil.core.protocol.AuthFailMessage
import com.example.mouse_movil.core.protocol.AuthMessage
import com.example.mouse_movil.core.protocol.AuthOkMessage
import com.example.mouse_movil.core.protocol.ByeMessage
import com.example.mouse_movil.core.protocol.HelloAckMessage
import com.example.mouse_movil.core.protocol.HelloMessage
import com.example.mouse_movil.core.protocol.PairFailMessage
import com.example.mouse_movil.core.protocol.PairOkMessage
import com.example.mouse_movil.core.protocol.PairRequestMessage
import com.example.mouse_movil.core.protocol.PingMessage
import com.example.mouse_movil.core.protocol.PongMessage
import com.example.mouse_movil.core.protocol.ProtocolConstants
import com.example.mouse_movil.core.protocol.ProtocolMessage
import com.example.mouse_movil.data.PairedPcRepository
import com.example.mouse_movil.data.model.PairedPc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Discovering : ConnectionState
    data class Connecting(val targetName: String) : ConnectionState
    data class AwaitingPin(
        val host: String,
        val port: Int,
        val serverName: String,
        val verificationCode: String,
        val fingerprint: String,
        val attemptsLeft: Int = ProtocolConstants.PIN_MAX_ATTEMPTS,
        val errorMessage: String? = null
    ) : ConnectionState
    data class Connected(
        val serverId: String,
        val pcName: String,
        val host: String,
        val rttMs: Long
    ) : ConnectionState
    data class Reconnecting(val pcName: String, val attempt: Int) : ConnectionState
    data class Error(val reason: String) : ConnectionState
}

class ConnectionManager(
    private val pairedPcRepository: PairedPcRepository,
    private val discoveryClient: DiscoveryClient
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _discoveredPcs = MutableStateFlow<List<DiscoveredPc>>(emptyList())
    val discoveredPcs: StateFlow<List<DiscoveredPc>> = _discoveredPcs.asStateFlow()

    @Volatile
    var activeConnection: RemoteConnection? = null
        private set

    private var heartbeatJob: Job? = null
    private var monitorJob: Job? = null
    private var reconnectJob: Job? = null
    private var inForeground = true
    private var manualDisconnect = false

    init {
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        } catch (_: Exception) {
            // Unit tests without Android Looper
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        inForeground = true
        if (!manualDisconnect && activeConnection?.isConnected != true) {
            connectSaved()
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        inForeground = false
        reconnectJob?.cancel()
        scope.launch {
            activeConnection?.closeGracefully("client_exit")
            activeConnection = null
            heartbeatJob?.cancel()
            monitorJob?.cancel()
        }
    }

    fun scanNetwork() {
        scope.launch {
            if (_state.value is ConnectionState.Idle || _state.value is ConnectionState.Error) {
                _state.value = ConnectionState.Discovering
            }
            val found = discoveryClient.discover()
            _discoveredPcs.value = found
            if (_state.value is ConnectionState.Discovering) {
                _state.value = ConnectionState.Idle
            }
        }
    }

    fun pairWithQr(rawUri: String) {
        val data = PairingUri.parse(rawUri)
        if (data == null) {
            _state.value = ConnectionState.Error("El código QR escaneado no es un código MSE válido.")
            return
        }
        manualDisconnect = false
        reconnectJob?.cancel()
        scope.launch {
            performPairInternal(
                host = data.host,
                port = data.port,
                expectedFp = data.fingerprint,
                pin = data.pin,
                fallbackName = data.serverName
            )
        }
    }

    fun preparePinPairing(host: String, port: Int = ProtocolConstants.TCP_PORT, knownFp: String? = null, displayName: String = host) {
        manualDisconnect = false
        reconnectJob?.cancel()
        scope.launch {
            _state.value = ConnectionState.Connecting(displayName)
            val probe = RemoteConnection(host, port, knownFp, scope)
            try {
                probe.connect()
                val deviceId = pairedPcRepository.getOrCreateDeviceId()
                val deviceName = pairedPcRepository.getDeviceName()
                probe.send(HelloMessage(deviceId = deviceId, deviceName = deviceName))

                val ack = withTimeoutOrNull(ProtocolConstants.HANDSHAKE_TIMEOUT_MS) {
                    probe.incomingMessages.first { it is HelloAckMessage || it is AuthFailMessage }
                }

                val observedFp = probe.trustManager.observedFingerprint
                val verCode = probe.trustManager.observedVerificationCode
                probe.closeGracefully("client_exit")

                if (ack is HelloAckMessage) {
                    if (!ack.pairingOpen) {
                        _state.value = ConnectionState.Error("La PC «${ack.serverName}» tiene bloqueados los nuevos emparejamientos.")
                        return@launch
                    }
                    _state.value = ConnectionState.AwaitingPin(
                        host = host,
                        port = port,
                        serverName = ack.serverName,
                        verificationCode = verCode,
                        fingerprint = observedFp
                    )
                } else {
                    _state.value = ConnectionState.Error("No se recibió respuesta válida de la PC.")
                }
            } catch (pinEx: CertificatePinMismatchException) {
                probe.close()
                _state.value = ConnectionState.Error(pinEx.message ?: "La identidad de la PC cambió.")
            } catch (e: Exception) {
                probe.close()
                _state.value = ConnectionState.Error("No se pudo conectar a $host:$port (${e.localizedMessage ?: "Error de red"}).")
            }
        }
    }

    fun submitPin(pin: String) {
        val current = _state.value as? ConnectionState.AwaitingPin ?: return
        val cleanPin = pin.trim()
        if (cleanPin.length != 6 || !cleanPin.all { it.isDigit() }) {
            _state.value = current.copy(errorMessage = "El PIN debe tener 6 dígitos.")
            return
        }

        scope.launch {
            performPairInternal(
                host = current.host,
                port = current.port,
                expectedFp = current.fingerprint,
                pin = cleanPin,
                fallbackName = current.serverName,
                previousAwaitingState = current
            )
        }
    }

    fun cancelPinPrompt() {
        _state.value = ConnectionState.Idle
    }

    private suspend fun performPairInternal(
        host: String,
        port: Int,
        expectedFp: String?,
        pin: String,
        fallbackName: String,
        previousAwaitingState: ConnectionState.AwaitingPin? = null
    ) {
        _state.value = ConnectionState.Connecting(fallbackName)
        val conn = RemoteConnection(host, port, expectedFp, scope)
        try {
            conn.connect()
            val deviceId = pairedPcRepository.getOrCreateDeviceId()
            val deviceName = pairedPcRepository.getDeviceName()

            conn.send(HelloMessage(deviceId = deviceId, deviceName = deviceName))
            val ack = withTimeoutOrNull(ProtocolConstants.HANDSHAKE_TIMEOUT_MS) {
                conn.incomingMessages.first()
            }
            if (ack !is HelloAckMessage) {
                conn.close()
                _state.value = ConnectionState.Error("Respuesta de saludo inválida desde la PC.")
                return
            }

            conn.send(PairRequestMessage(deviceId = deviceId, deviceName = deviceName, pin = pin))
            val pairResp = withTimeoutOrNull(ProtocolConstants.HANDSHAKE_TIMEOUT_MS) {
                conn.incomingMessages.first()
            }

            when (pairResp) {
                is PairOkMessage -> {
                    val fp = conn.trustManager.observedFingerprint
                    pairedPcRepository.savePairedPc(
                        serverId = pairResp.serverId,
                        name = pairResp.serverName,
                        host = host,
                        port = port,
                        fp = fp,
                        rawToken = pairResp.token
                    )
                    onSessionAuthenticated(conn, pairResp.serverId, pairResp.serverName, host)
                }

                is PairFailMessage -> {
                    conn.close()
                    val msg = when (pairResp.reason) {
                        "bad_pin" -> "PIN incorrecto. Intentos restantes: ${pairResp.attemptsLeft}"
                        "locked" -> "Demasiados intentos. Bloqueado por ${pairResp.retryAfterSec} s."
                        "expired" -> "El PIN expiró en la PC. Ingresa el nuevo PIN mostrado."
                        "pairing_closed" -> "La PC tiene cerrados los nuevos emparejamientos."
                        else -> "Error de emparejamiento (${pairResp.reason})."
                    }
                    if (previousAwaitingState != null && pairResp.reason == "bad_pin" && pairResp.attemptsLeft > 0) {
                        _state.value = previousAwaitingState.copy(
                            attemptsLeft = pairResp.attemptsLeft,
                            errorMessage = msg
                        )
                    } else {
                        _state.value = ConnectionState.Error(msg)
                    }
                }

                else -> {
                    conn.close()
                    _state.value = ConnectionState.Error("No se pudo completar el emparejamiento.")
                }
            }
        } catch (pinEx: CertificatePinMismatchException) {
            conn.close()
            _state.value = ConnectionState.Error("La identidad de la PC cambió. Verifica el código QR.")
        } catch (e: Exception) {
            conn.close()
            _state.value = ConnectionState.Error("Error al conectar con $host:$port (${e.localizedMessage ?: "Red inalcanzable"}).")
        }
    }

    fun connectSaved(targetPc: PairedPc? = null) {
        manualDisconnect = false
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val pc = targetPc ?: pairedPcRepository.getLastOrDefault()
            if (pc == null) {
                _state.value = ConnectionState.Idle
                return@launch
            }
            connectWithBackoffLoop(pc)
        }
    }

    private suspend fun connectWithBackoffLoop(initialPc: PairedPc) {
        var currentPc = initialPc
        val delaysSec = intArrayOf(1, 2, 4, 8, 16, 30)
        var attempt = 0

        while (scope.isActive && inForeground && !manualDisconnect) {
            if (attempt == 0) {
                _state.value = ConnectionState.Connecting(currentPc.name)
            } else {
                _state.value = ConnectionState.Reconnecting(currentPc.name, attempt)
            }

            val token = pairedPcRepository.decryptToken(currentPc)
            if (token.isNullOrBlank()) {
                pairedPcRepository.forget(currentPc.serverId)
                _state.value = ConnectionState.Error("El token de seguridad local expiró. Vuelve a vincular la PC.")
                return
            }

            val outcome = tryAuthenticateOnce(currentPc, token)
            when (outcome) {
                is AuthAttemptOutcome.Success -> return
                is AuthAttemptOutcome.Fatal -> {
                    if (outcome.forgetPc) {
                        pairedPcRepository.forget(currentPc.serverId)
                    }
                    _state.value = ConnectionState.Error(outcome.userMessage)
                    return
                }
                is AuthAttemptOutcome.TransientFailure -> {
                    // Try UDP discovery to see if the PC changed IP via DHCP
                    val discovered = discoveryClient.discover(900L)
                    _discoveredPcs.value = discovered
                    val match = discovered.firstOrNull { it.serverId == currentPc.serverId }
                    if (match != null && (match.host != currentPc.host || match.port != currentPc.port)) {
                        pairedPcRepository.updateHostAndTimestamp(currentPc.serverId, match.host, match.port)
                        currentPc = currentPc.copy(host = match.host, port = match.port)
                    }
                }
            }

            val waitSec = delaysSec[attempt.coerceAtMost(delaysSec.lastIndex)]
            attempt++
            delay(waitSec * 1_000L)
        }
    }

    private sealed interface AuthAttemptOutcome {
        data object Success : AuthAttemptOutcome
        data class Fatal(val userMessage: String, val forgetPc: Boolean) : AuthAttemptOutcome
        data object TransientFailure : AuthAttemptOutcome
    }

    private suspend fun tryAuthenticateOnce(pc: PairedPc, rawToken: String): AuthAttemptOutcome {
        val conn = RemoteConnection(pc.host, pc.port, pc.fp, scope)
        return try {
            conn.connect(connectTimeoutMs = 3_000)
            val deviceId = pairedPcRepository.getOrCreateDeviceId()
            val deviceName = pairedPcRepository.getDeviceName()

            conn.send(HelloMessage(deviceId = deviceId, deviceName = deviceName))
            val ack = withTimeoutOrNull(ProtocolConstants.HANDSHAKE_TIMEOUT_MS) {
                conn.incomingMessages.first()
            }
            if (ack !is HelloAckMessage) {
                conn.close()
                return AuthAttemptOutcome.TransientFailure
            }

            conn.send(AuthMessage(deviceId = deviceId, token = rawToken))
            val authResp = withTimeoutOrNull(ProtocolConstants.HANDSHAKE_TIMEOUT_MS) {
                conn.incomingMessages.first()
            }

            when (authResp) {
                is AuthOkMessage -> {
                    pairedPcRepository.updateHostAndTimestamp(pc.serverId, pc.host, pc.port)
                    onSessionAuthenticated(conn, pc.serverId, authResp.serverName, pc.host)
                    AuthAttemptOutcome.Success
                }
                is AuthFailMessage -> {
                    conn.close()
                    val msg = when (authResp.reason) {
                        "revoked" -> "El acceso de este teléfono fue revocado desde la PC."
                        "unknown_device", "bad_token" -> "La vinculación con «${pc.name}» ya no es válida. Vuelve a emparejar."
                        else -> "Autenticación rechazada (${authResp.reason})."
                    }
                    AuthAttemptOutcome.Fatal(msg, forgetPc = true)
                }
                else -> {
                    conn.close()
                    AuthAttemptOutcome.TransientFailure
                }
            }
        } catch (_: CertificatePinMismatchException) {
            conn.close()
            AuthAttemptOutcome.Fatal(
                "La identidad de la PC «${pc.name}» cambió. Por seguridad se detuvo la conexión.",
                forgetPc = false
            )
        } catch (_: Exception) {
            conn.close()
            AuthAttemptOutcome.TransientFailure
        }
    }

    private fun onSessionAuthenticated(
        conn: RemoteConnection,
        serverId: String,
        serverName: String,
        host: String
    ) {
        activeConnection?.close()
        activeConnection = conn
        _state.value = ConnectionState.Connected(
            serverId = serverId,
            pcName = serverName,
            host = host,
            rttMs = 0L
        )

        heartbeatJob?.cancel()
        monitorJob?.cancel()

        var lastPongAt = System.currentTimeMillis()

        monitorJob = scope.launch {
            launch {
                conn.incomingMessages.collect { msg ->
                    handleAuthenticatedIncoming(msg, serverId, serverName, host) {
                        lastPongAt = System.currentTimeMillis()
                    }
                }
            }
            launch {
                conn.disconnected.first()
                if (activeConnection === conn) {
                    activeConnection = null
                    heartbeatJob?.cancel()
                    if (!manualDisconnect && inForeground) {
                        connectSaved()
                    }
                }
            }
        }

        heartbeatJob = scope.launch {
            while (isActive && conn.isConnected) {
                val now = System.currentTimeMillis()
                if (now - lastPongAt > ProtocolConstants.IDLE_TIMEOUT_MS) {
                    conn.close()
                    break
                }
                conn.send(PingMessage(now))
                delay(ProtocolConstants.PING_INTERVAL_MS)
            }
        }
    }

    private suspend fun handleAuthenticatedIncoming(
        msg: ProtocolMessage,
        serverId: String,
        serverName: String,
        host: String,
        onPongReceived: () -> Unit
    ) {
        when (msg) {
            is PongMessage -> {
                onPongReceived()
                val rtt = (System.currentTimeMillis() - msg.ts).coerceAtLeast(1L)
                val current = _state.value
                if (current is ConnectionState.Connected && current.serverId == serverId) {
                    _state.value = current.copy(rttMs = rtt)
                } else {
                    _state.value = ConnectionState.Connected(serverId, serverName, host, rtt)
                }
            }
            is ByeMessage -> {
                manualDisconnect = msg.reason == "revoked"
                activeConnection?.close()
                activeConnection = null
                heartbeatJob?.cancel()
                if (msg.reason == "revoked") {
                    pairedPcRepository.forget(serverId)
                    _state.value = ConnectionState.Error("Acceso revocado desde la PC.")
                } else if (inForeground) {
                    connectSaved()
                }
            }
            else -> Unit
        }
    }

    fun disconnectUserInitiated() {
        manualDisconnect = true
        reconnectJob?.cancel()
        heartbeatJob?.cancel()
        monitorJob?.cancel()
        val conn = activeConnection
        activeConnection = null
        scope.launch {
            conn?.closeGracefully("client_exit")
            _state.value = ConnectionState.Idle
        }
    }
}
