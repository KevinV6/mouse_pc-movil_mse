package com.example.mouse_movil.core.network

import com.example.mouse_movil.core.protocol.ButtonMessage
import com.example.mouse_movil.core.protocol.ClickMessage
import com.example.mouse_movil.core.protocol.ComboMessage
import com.example.mouse_movil.core.protocol.KeyMessage
import com.example.mouse_movil.core.protocol.MediaMessage
import com.example.mouse_movil.core.protocol.ProtocolConstants
import com.example.mouse_movil.core.protocol.SystemMessage
import com.example.mouse_movil.core.protocol.TextMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class InputSender(
    private val connectionManager: ConnectionManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val pendingMoveDx = AtomicInteger(0)
    private val pendingMoveDy = AtomicInteger(0)
    private val pendingScrollDx = AtomicInteger(0)
    private val pendingScrollDy = AtomicInteger(0)

    init {
        scope.launch {
            while (isActive) {
                flushCoalesced()
                delay(ProtocolConstants.MOVE_FLUSH_INTERVAL_MS)
            }
        }
    }

    fun queueMove(dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        pendingMoveDx.addAndGet(dx)
        pendingMoveDy.addAndGet(dy)
    }

    fun queueScroll(dx: Int, dy: Int) {
        if (dx == 0 && dy == 0) return
        pendingScrollDx.addAndGet(dx)
        pendingScrollDy.addAndGet(dy)
    }

    private fun flushCoalesced() {
        val conn = connectionManager.activeConnection ?: run {
            pendingMoveDx.set(0)
            pendingMoveDy.set(0)
            pendingScrollDx.set(0)
            pendingScrollDy.set(0)
            return
        }

        val mdx = pendingMoveDx.getAndSet(0)
        val mdy = pendingMoveDy.getAndSet(0)
        if (mdx != 0 || mdy != 0) {
            conn.sendMouseMove(
                mdx.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort(),
                mdy.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            )
        }

        val sdx = pendingScrollDx.getAndSet(0)
        val sdy = pendingScrollDy.getAndSet(0)
        if (sdx != 0 || sdy != 0) {
            conn.sendScroll(
                sdx.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort(),
                sdy.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            )
        }
    }

    fun sendZoom(steps: Int) {
        if (steps == 0) return
        connectionManager.activeConnection?.sendZoom(
            steps.coerceIn(-20, 20).toShort()
        )
    }

    fun sendClick(button: String = "left", count: Int = 1) {
        connectionManager.activeConnection?.send(ClickMessage(button = button, count = count))
    }

    fun sendButton(button: String = "left", action: String) {
        connectionManager.activeConnection?.send(ButtonMessage(button = button, action = action))
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        connectionManager.activeConnection?.send(TextMessage(text = text))
    }

    fun sendKey(key: String, action: String = "press") {
        connectionManager.activeConnection?.send(KeyMessage(key = key, action = action))
    }

    fun sendCombo(keys: List<String>) {
        if (keys.isEmpty()) return
        connectionManager.activeConnection?.send(ComboMessage(keys = keys))
    }

    fun sendMedia(action: String) {
        connectionManager.activeConnection?.send(MediaMessage(action = action))
    }

    fun sendSystem(action: String, confirm: Boolean = true) {
        connectionManager.activeConnection?.send(SystemMessage(action = action, confirm = confirm))
    }
}
