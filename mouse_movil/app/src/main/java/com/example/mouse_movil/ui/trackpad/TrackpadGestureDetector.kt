package com.example.mouse_movil.ui.trackpad

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import kotlin.math.hypot

data class TrackpadGestureCallbacks(
    val onMove: (rawDx: Float, rawDy: Float, dtMs: Long) -> Unit,
    val onClick: (button: String, count: Int) -> Unit,
    val onScroll: (panDx: Float, panDy: Float) -> Unit,
    val onZoom: (steps: Int) -> Unit,
    val onDragStart: () -> Unit,
    val onDragEnd: () -> Unit
)

fun Modifier.trackpadGestures(
    callbacks: TrackpadGestureCallbacks
): Modifier = this.pointerInput(callbacks) {
    val tapSlopPx = viewConfiguration.touchSlop * 1.15f
    var lastSingleTapUpTime = 0L

    awaitEachGesture {
        var maxPointers = 0
        var firstDownTime = 0L
        var lastEventTime = 0L
        var totalMovementPx = 0f
        var isDragging = false

        var initialTwoFingerSpan = 0f
        var zoomAnchorSpan = 0f
        var prevTwoFingerCentroid: Offset? = null
        var scrollResidualsX = 0f
        var scrollResidualsY = 0f

        try {
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                val now = event.changes.firstOrNull()?.uptimeMillis ?: System.currentTimeMillis()

                if (firstDownTime == 0L) {
                    firstDownTime = now
                    lastEventTime = now
                }
                val dt = (now - lastEventTime).coerceAtLeast(1L)
                lastEventTime = now

                if (pressed.isEmpty()) {
                    // All fingers lifted
                    val duration = now - firstDownTime
                    if (isDragging) {
                        callbacks.onDragEnd()
                        isDragging = false
                    } else if (totalMovementPx <= tapSlopPx) {
                        when (maxPointers) {
                            1 -> {
                                if (duration <= GestureMath.TAP_MAX_DURATION_MS) {
                                    if (now - lastSingleTapUpTime <= GestureMath.DOUBLE_TAP_WINDOW_MS) {
                                        callbacks.onClick("left", 2)
                                        lastSingleTapUpTime = 0L
                                    } else {
                                        callbacks.onClick("left", 1)
                                        lastSingleTapUpTime = now
                                    }
                                }
                            }
                            2 -> {
                                if (duration <= GestureMath.TWO_FINGER_TAP_MAX_DURATION_MS) {
                                    callbacks.onClick("right", 1)
                                }
                            }
                            3 -> {
                                if (duration <= GestureMath.THREE_FINGER_TAP_MAX_DURATION_MS) {
                                    callbacks.onClick("middle", 1)
                                }
                            }
                        }
                    }
                    break
                }

                if (pressed.size > maxPointers) {
                    maxPointers = pressed.size
                }

                when (pressed.size) {
                    1 -> {
                        val change = pressed[0]
                        val delta = change.positionChange()
                        val stepDist = hypot(delta.x, delta.y)
                        totalMovementPx += stepDist

                        val elapsed = now - firstDownTime
                        if (!isDragging &&
                            maxPointers == 1 &&
                            elapsed >= GestureMath.LONG_PRESS_DRAG_MS &&
                            totalMovementPx <= tapSlopPx * 1.4f
                        ) {
                            isDragging = true
                            callbacks.onDragStart()
                        }

                        if ((maxPointers == 1 || isDragging) && (delta.x != 0f || delta.y != 0f)) {
                            callbacks.onMove(delta.x, delta.y, dt)
                        }
                        change.consume()
                    }

                    2 -> {
                        if (isDragging) {
                            callbacks.onDragEnd()
                            isDragging = false
                        }

                        val p0 = pressed[0].position
                        val p1 = pressed[1].position
                        val centroid = Offset((p0.x + p1.x) * 0.5f, (p0.y + p1.y) * 0.5f)
                        val span = hypot(p1.x - p0.x, p1.y - p0.y).coerceAtLeast(1f)

                        if (initialTwoFingerSpan == 0f) {
                            initialTwoFingerSpan = span
                            zoomAnchorSpan = span
                            prevTwoFingerCentroid = centroid
                        } else {
                            val prevCentroid = prevTwoFingerCentroid ?: centroid
                            val panDx = centroid.x - prevCentroid.x
                            val panDy = centroid.y - prevCentroid.y
                            prevTwoFingerCentroid = centroid

                            totalMovementPx += hypot(panDx, panDy) + abs(span - initialTwoFingerSpan)

                            val spanRatioChange = abs(span - initialTwoFingerSpan) / initialTwoFingerSpan
                            if (spanRatioChange > 0.08f) {
                                val (steps, nextAnchor) = GestureMath.computePinchZoomSteps(zoomAnchorSpan, span)
                                zoomAnchorSpan = nextAnchor
                                if (steps != 0) {
                                    callbacks.onZoom(steps)
                                }
                            } else {
                                scrollResidualsX += panDx
                                scrollResidualsY += panDy
                                if (abs(scrollResidualsX) >= 4f || abs(scrollResidualsY) >= 4f) {
                                    callbacks.onScroll(scrollResidualsX, scrollResidualsY)
                                    scrollResidualsX = 0f
                                    scrollResidualsY = 0f
                                }
                            }
                        }

                        pressed.forEach(PointerInputChange::consume)
                    }

                    else -> {
                        // 3+ fingers
                        for (c in pressed) {
                            val d = c.positionChange()
                            totalMovementPx += hypot(d.x, d.y)
                            c.consume()
                        }
                    }
                }
            }
        } finally {
            if (isDragging) {
                callbacks.onDragEnd()
            }
        }
    }
}
