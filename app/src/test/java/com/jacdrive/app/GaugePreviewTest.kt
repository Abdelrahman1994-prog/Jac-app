package com.jacdrive.app

import android.graphics.Color
import com.jacdrive.app.auto.GaugeBitmap
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Writes the car-screen gauge images to build/outputs/gauges for visual review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class GaugePreviewTest {
    @Test fun writePreviews() {
        val dir = File("build/outputs/gauges").apply { mkdirs() }
        val blue = Color.rgb(54, 194, 255)
        listOf(
            "rpm" to GaugeBitmap.render(0.27f, blue, 0.75f),
            "speed" to GaugeBitmap.render(0.38f, blue),
            "coolant" to GaugeBitmap.render(0.72f, Color.rgb(255, 176, 32), 0.72f),
            "battery" to GaugeBitmap.render(0.69f, blue),
            "load" to GaugeBitmap.render(0.35f, blue),
            "fuel" to GaugeBitmap.render(null, blue),
        ).forEach { (n, b) -> File(dir, "$n.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
    }

    @Test fun writeCockpitSurfacePreview() {
        val bmp = android.graphics.Bitmap.createBitmap(1280, 720, android.graphics.Bitmap.Config.ARGB_8888)
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val r = com.jacdrive.app.auto.CockpitRenderer(
            androidx.core.content.res.ResourcesCompat.getFont(ctx, com.jacdrive.ui.R.font.rajdhani_bold),
            androidx.core.content.res.ResourcesCompat.getFont(ctx, com.jacdrive.ui.R.font.rajdhani_light),
        )
        val frame = com.jacdrive.app.auto.CockpitFrame(
            speed = 92f, speedMax = 240f, speedUnit = "km/h", rpm = 2350f, redline = 6000f,
            coolant = 91f, coolantUnit = "°C", coolantHot = false, battery = 14.1f, batteryBad = false,
            fuel = 64f, load = 38f, status = "Connected · OBDII", demo = false, milOn = false,
            throttle = 22f, intake = 34f, trip = "12.4 km", avgFuel = "8.6 L/100km", roadPhase = 0.35f,
            labels = com.jacdrive.app.auto.CockpitFrame.Labels("x1000 rpm", "Coolant", "Battery", "Fuel", "Load", "DEMO", "CHECK ENGINE", "Throttle", "Intake", "Trip", "Avg"),
        )
        // Host controls occupy the top-right corner; draw in the remaining area.
        r.draw(android.graphics.Canvas(bmp), android.graphics.Rect(0, 90, 1280, 720), frame)
        File("build/outputs/gauges/cockpit_surface.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        // Portrait head unit (JAC J7): tall surface.
        val tall = android.graphics.Bitmap.createBitmap(800, 1066, android.graphics.Bitmap.Config.ARGB_8888)
        r.draw(android.graphics.Canvas(tall), android.graphics.Rect(0, 80, 800, 1066), frame.copy(milOn = true, coolant = 107f, coolantHot = true))
        File("build/outputs/gauges/cockpit_surface_portrait.png").outputStream().use { tall.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
