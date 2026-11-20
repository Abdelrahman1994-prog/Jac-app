package com.jacdrive.dashboard

import androidx.compose.ui.test.junit4.createComposeRule
import com.jacdrive.dashboard.dash.rememberRoadDistance
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The road animation must advance at the car's real speed and stand still when it is stopped. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HeroSceneMotionTest {
    @get:Rule val compose = createComposeRule()

    private fun metresAfter(speedKmh: Float, enabled: Boolean, ms: Long): Float {
        var metres = 0f
        compose.mainClock.autoAdvance = false
        compose.setContent { metres = rememberRoadDistance(speedKmh, enabled).value }
        compose.mainClock.advanceTimeBy(100)
        val start = metres
        compose.mainClock.advanceTimeBy(ms)
        return metres - start
    }

    @Test fun movesAtRealSpeed() {
        // 72 km/h = 20 m/s → about 40 m in 2 s.
        assertEquals(40f, metresAfter(72f, enabled = true, ms = 2_000), 1.5f)
    }

    @Test fun stillWhenStopped() = assertEquals(0f, metresAfter(0f, enabled = true, ms = 2_000), 0.001f)

    @Test fun stillWhenAnimationsOff() = assertEquals(0f, metresAfter(72f, enabled = false, ms = 2_000), 0.001f)
}
