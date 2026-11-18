package com.jacdrive.analytics

import com.jacdrive.analytics.performance.AccelerationTimer
import com.jacdrive.analytics.performance.GMath
import com.jacdrive.analytics.performance.RunType
import com.jacdrive.analytics.performance.TimerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceTest {

    /** Constant 3 m/s² ≈ 10.8 km/h per second, sampled every 200 ms from standstill at t = 1000 ms. */
    private fun constantAccel(timer: AccelerationTimer, launchMs: Long = 1_000, until: Double = 130.0) {
        timer.onSpeed(0.0, 0); timer.onSpeed(0.0, 500); timer.onSpeed(0.0, launchMs)
        var t = launchMs + 200
        while (true) {
            val v = 10.8 * (t - launchMs) / 1000.0
            if (v > until) break
            timer.onSpeed(v, t)
            t += 200
        }
    }

    @Test fun zeroTo100IsInterpolatedFromSamples() {
        val timer = AccelerationTimer(RunType.ZERO_100)
        constantAccel(timer)
        assertEquals(TimerState.FINISHED, timer.state)
        // Exact: 100 / 10.8 = 9.259 s minus the 0.5 km/h launch threshold (0.046 s).
        assertEquals(100 / 10.8 - 0.5 / 10.8, timer.resultS!!, 0.01)
    }

    @Test fun sixtyTo120() {
        val timer = AccelerationTimer(RunType.SIXTY_120)
        constantAccel(timer)
        assertEquals(60 / 10.8, timer.resultS!!, 0.01)
    }

    @Test fun rollingStartDoesNotCountForZeroToSixty() {
        val timer = AccelerationTimer(RunType.ZERO_60)
        timer.onSpeed(30.0, 0); timer.onSpeed(40.0, 1000); timer.onSpeed(70.0, 3000)
        assertNull(timer.resultS)
        assertEquals(TimerState.WAITING_FOR_STOP, timer.state)
    }

    @Test fun liftingOffAborts() {
        val timer = AccelerationTimer(RunType.ZERO_100)
        timer.onSpeed(0.0, 0); timer.onSpeed(10.0, 1000); timer.onSpeed(40.0, 3000); timer.onSpeed(30.0, 4000)
        assertEquals(TimerState.ABORTED, timer.state)
    }

    @Test fun gProjectionForUprightPhone() {
        // Upright landscape phone, screen facing the driver: gravity along device +X (left edge down),
        // forward is device −Z. Accelerating at 0.5 g forward → linear acceleration along −Z.
        val g = floatArrayOf(9.81f, 0f, 0f)
        val s = GMath.project(floatArrayOf(0f, 0f, -4.903f), g, 0)!!
        assertEquals(0.5, s.longitudinal, 0.01)
        assertEquals(0.0, s.lateral, 0.01)
        val side = GMath.project(floatArrayOf(0f, 2.0f, 0f), g, 0)!!
        assertEquals(0.0, side.longitudinal, 0.01)
        assertTrue(kotlin.math.abs(side.lateral) > 0.19)
    }

    @Test fun flatPhoneIsRejected() {
        assertNull(GMath.project(floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, 9.81f), 0))
    }
}
