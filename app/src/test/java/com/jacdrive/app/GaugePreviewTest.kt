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
}
