package com.example.mouse_movil.ui.trackpad

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

class SubPixelAccumulator {
    var residualX: Float = 0f
        private set
    var residualY: Float = 0f
        private set

    fun reset() {
        residualX = 0f
        residualY = 0f
    }

    fun accumulate(scaledDx: Float, scaledDy: Float): Pair<Int, Int> {
        val totalX = scaledDx + residualX
        val totalY = scaledDy + residualY

        val outX = totalX.toInt()
        val outY = totalY.toInt()

        residualX = totalX - outX
        residualY = totalY - outY

        return outX to outY
    }
}

object GestureMath {
    const val TAP_MAX_DURATION_MS: Long = 210L
    const val DOUBLE_TAP_WINDOW_MS: Long = 290L
    const val TWO_FINGER_TAP_MAX_DURATION_MS: Long = 260L
    const val THREE_FINGER_TAP_MAX_DURATION_MS: Long = 260L
    const val LONG_PRESS_DRAG_MS: Long = 400L

    private const val VELOCITY_THRESHOLD_PX_PER_MS = 1.6f
    private const val WHEEL_STEP_PX = 36f

    /**
     * Formula: out = delta * sensitivity * (1 + acceleration * min(speed / threshold, 2))
     */
    fun applyPointerAcceleration(
        rawDx: Float,
        rawDy: Float,
        dtMs: Long,
        sensitivity: Float,
        acceleration: Float
    ): Pair<Float, Float> {
        val safeDt = dtMs.coerceAtLeast(1L).toFloat()
        val dist = hypot(rawDx, rawDy)
        val speed = dist / safeDt
        val boostFactor = 1f + acceleration * min(speed / VELOCITY_THRESHOLD_PX_PER_MS, 2f)
        val factor = sensitivity * boostFactor
        return (rawDx * factor) to (rawDy * factor)
    }

    /**
     * Converts 2-finger pan deltas (in screen pixels) into Windows wheel units (120 = 1 notch).
     * Positive dy in protocol = scroll up.
     */
    fun computeScrollWheelUnits(
        panDxPx: Float,
        panDyPx: Float,
        scrollSpeed: Float,
        naturalScroll: Boolean
    ): Pair<Int, Int> {
        val sign = if (naturalScroll) 1f else -1f
        val mult = (scrollSpeed * 120f) / WHEEL_STEP_PX
        val wheelX = (-panDxPx * sign * mult).roundToInt()
        val wheelY = (panDyPx * sign * mult).roundToInt()
        return wheelX to wheelY
    }

    /**
     * Computes integer zoom steps (±1 per 10% scale change) and returns `(steps, newAnchorDistance)`.
     */
    fun computePinchZoomSteps(
        anchorDistancePx: Float,
        currentDistancePx: Float
    ): Pair<Int, Float> {
        if (anchorDistancePx <= 1f || currentDistancePx <= 1f) {
            return 0 to currentDistancePx
        }
        val ratioChange = (currentDistancePx - anchorDistancePx) / anchorDistancePx
        if (abs(ratioChange) < 0.10f) {
            return 0 to anchorDistancePx
        }
        val steps = (ratioChange / 0.10f).toInt().coerceIn(-5, 5)
        return steps to currentDistancePx
    }
}
