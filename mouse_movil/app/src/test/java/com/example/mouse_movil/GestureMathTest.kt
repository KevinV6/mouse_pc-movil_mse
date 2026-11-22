package com.example.mouse_movil

import com.example.mouse_movil.ui.trackpad.GestureMath
import com.example.mouse_movil.ui.trackpad.SubPixelAccumulator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureMathTest {

    @Test
    fun subPixelAccumulator_carriesFractionalDeltasWithoutLoss() {
        val acc = SubPixelAccumulator()
        val (x1, y1) = acc.accumulate(0.4f, -0.4f)
        assertEquals(0, x1)
        assertEquals(0, y1)

        val (x2, y2) = acc.accumulate(0.4f, -0.4f)
        assertEquals(0, x2)
        assertEquals(0, y2)

        val (x3, y3) = acc.accumulate(0.4f, -0.4f)
        assertEquals(1, x3)
        assertEquals(-1, y3)
    }

    @Test
    fun pointerAcceleration_fastSwipeProducesHigherMultiplierThanSlowSwipe() {
        val (slowX, _) = GestureMath.applyPointerAcceleration(
            rawDx = 10f,
            rawDy = 0f,
            dtMs = 40L,
            sensitivity = 1.0f,
            acceleration = 0.5f
        )
        val (fastX, _) = GestureMath.applyPointerAcceleration(
            rawDx = 10f,
            rawDy = 0f,
            dtMs = 2L,
            sensitivity = 1.0f,
            acceleration = 0.5f
        )
        assertTrue("Fast swipe ($fastX) must exceed slow swipe ($slowX)", fastX > slowX)
    }

    @Test
    fun scrollWheelUnits_naturalVsInverted() {
        val (_, natY) = GestureMath.computeScrollWheelUnits(0f, 36f, scrollSpeed = 1.0f, naturalScroll = true)
        val (_, invY) = GestureMath.computeScrollWheelUnits(0f, 36f, scrollSpeed = 1.0f, naturalScroll = false)
        assertEquals(120, natY)
        assertEquals(-120, invY)
    }

    @Test
    fun pinchZoomSteps_emitsStepAt10PercentChange() {
        val (smallStep, anchor1) = GestureMath.computePinchZoomSteps(100f, 105f)
        assertEquals(0, smallStep)
        assertEquals(100f, anchor1, 0.001f)

        val (zoomInStep, anchor2) = GestureMath.computePinchZoomSteps(100f, 122f)
        assertEquals(2, zoomInStep)
        assertEquals(122f, anchor2, 0.001f)

        val (zoomOutStep, _) = GestureMath.computePinchZoomSteps(100f, 85f)
        assertEquals(-1, zoomOutStep)
    }
}
