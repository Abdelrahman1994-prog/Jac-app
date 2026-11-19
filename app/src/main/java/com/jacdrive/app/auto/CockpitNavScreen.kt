package com.jacdrive.app.auto

import android.graphics.Rect
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.jacdrive.app.R
import com.jacdrive.core.model.ConnectionState
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.Freshness
import com.jacdrive.core.model.SpeedUnit
import com.jacdrive.core.model.TemperatureUnit
import com.jacdrive.core.model.UnitConverter
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.core.model.freshness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * PERSONAL-USE BUILD: full cockpit drawn on the Android Auto map surface. Only navigation-category
 * apps receive a drawable surface, so this requires declaring the navigation category — which is
 * not accurate for this app and must never be published publicly. See docs/ANDROID_AUTO.md.
 *
 * Driving-safe by design: no interaction needed, large numerals, no blinking; redraws only when
 * new data arrives (at most ~10 times per second).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CockpitNavScreen(carContext: CarContext, private val deps: CarDeps) : Screen(carContext), SurfaceCallback {

    private var surface: SurfaceContainer? = null
    private var visible = Rect()
    private var snapshot: VehicleSnapshot = deps.vehicleData.snapshot.value
    private var speedUnit = SpeedUnit.KMH
    private var tempUnit = TemperatureUnit.CELSIUS
    private var redline = 6000
    private val drawer = Dispatchers.Default.limitedParallelism(1)
    private val renderer = CockpitRenderer(
        runCatching { ResourcesCompat.getFont(carContext, com.jacdrive.ui.R.font.rajdhani_bold) }.getOrNull(),
        runCatching { ResourcesCompat.getFont(carContext, com.jacdrive.ui.R.font.rajdhani_light) }.getOrNull(),
    )
    private var lastDraw = 0L
    private var roadPhase = 0f
    private val drawing = java.util.concurrent.atomic.AtomicBoolean(false)

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                carContext.getCarService(AppManager::class.java).setSurfaceCallback(this@CockpitNavScreen)
            }
        })
        lifecycleScope.launch {
            val s = deps.settings.settings.first()
            speedUnit = s.speedUnit
            tempUnit = s.temperatureUnit
            redline = deps.settings.cockpit.first().redlineRpm
            launch {
                deps.vehicleData.snapshot.collect { snap ->
                    snapshot = snap
                    val now = System.currentTimeMillis()
                    if (now - lastDraw >= 100) { lastDraw = now; render() }
                }
            }
            // Road animation: lane marks advance with the real vehicle speed (about 25 fps while
            // moving). Nothing animates when the car is stopped or there is no speed data.
            var last = System.currentTimeMillis()
            while (true) {
                kotlinx.coroutines.delay(FRAME_MS)
                val now = System.currentTimeMillis()
                val kmh = snapshot[VehicleMetric.SPEED]?.takeIf { it.freshness(VehicleMetric.SPEED, now) == Freshness.LIVE }?.value ?: 0.0
                if (kmh > 0.5) {
                    // One full dash cycle per ~3.6 m travelled.
                    roadPhase = ((roadPhase + (kmh / 3.6 * (now - last) / 1000.0 / DASH_METERS).toFloat()) % 1f)
                    lastDraw = now
                    render()
                }
                last = now
            }
        }
    }

    override fun onSurfaceAvailable(container: SurfaceContainer) { surface = container; render() }
    override fun onVisibleAreaChanged(visibleArea: Rect) { visible = visibleArea; render() }
    override fun onStableAreaChanged(stableArea: Rect) = Unit
    override fun onSurfaceDestroyed(container: SurfaceContainer) { surface = null }

    private fun render() {
        val container = surface ?: return
        if (!drawing.compareAndSet(false, true)) return // previous frame still drawing: skip
        val frame = frame()
        lifecycleScope.launch {
            try { withContext(drawer) {
                val s = container.surface ?: return@withContext
                if (!s.isValid) return@withContext
                val canvas = runCatching { s.lockHardwareCanvas() }.getOrNull() ?: return@withContext
                try {
                    val area = if (visible.isEmpty) Rect(0, 0, container.width, container.height) else Rect(visible)
                    renderer.draw(canvas, area, frame)
                } finally {
                    s.unlockCanvasAndPost(canvas)
                }
            } } finally { drawing.set(false) }
        }
    }

    fun frame(now: Long = System.currentTimeMillis()): CockpitFrame {
        val s = snapshot
        fun v(m: VehicleMetric) = s[m]?.takeIf { it.freshness(m, now) != Freshness.MISSING }?.value
        val coolant = v(VehicleMetric.COOLANT_TEMP)
        val battery = v(VehicleMetric.BATTERY_VOLTAGE)
        val status = when {
            s.mode == DataMode.DEMO -> carContext.getString(R.string.car_demo)
            s.connection is ConnectionState.Connected -> carContext.getString(R.string.car_connected, (s.connection as ConnectionState.Connected).adapterName)
            s.connection is ConnectionState.Connecting -> carContext.getString(R.string.car_connecting)
            s.connection is ConnectionState.Failed -> carContext.getString(R.string.car_reconnecting)
            else -> carContext.getString(R.string.car_not_connected)
        }
        return CockpitFrame(
            speed = v(VehicleMetric.SPEED)?.let { UnitConverter.speed(it, speedUnit).toFloat() },
            speedMax = UnitConverter.speedScaleMax(speedUnit).toFloat(),
            speedUnit = if (speedUnit == SpeedUnit.KMH) "km/h" else "mph",
            rpm = v(VehicleMetric.RPM)?.toFloat(),
            redline = redline.toFloat(),
            coolant = coolant?.let { UnitConverter.temperature(it, tempUnit).toFloat() },
            coolantUnit = if (tempUnit == TemperatureUnit.CELSIUS) "°C" else "°F",
            coolantHot = (coolant ?: 0.0) >= 105,
            battery = battery?.toFloat(),
            batteryBad = battery != null && (battery < 12.0 || battery > 15.2),
            fuel = v(VehicleMetric.FUEL_LEVEL)?.toFloat(),
            load = v(VehicleMetric.ENGINE_LOAD)?.toFloat(),
            status = status,
            demo = s.mode == DataMode.DEMO,
            milOn = s.milOn == true,
            throttle = v(VehicleMetric.THROTTLE_POSITION)?.toFloat(),
            intake = v(VehicleMetric.INTAKE_AIR_TEMP)?.let { UnitConverter.temperature(it, tempUnit).toFloat() },
            trip = v(VehicleMetric.TRIP_DISTANCE)?.let { "%.1f %s".format(UnitConverter.distance(it, speedUnit), if (speedUnit == SpeedUnit.KMH) "km" else "mi") },
            avgFuel = v(VehicleMetric.AVG_FUEL_CONSUMPTION)?.let { "%.1f L/100km".format(it) },
            roadPhase = roadPhase,
            labels = CockpitFrame.Labels(
                rpm = carContext.getString(R.string.car_rpm_x1000),
                coolant = carContext.getString(R.string.car_coolant),
                battery = carContext.getString(R.string.car_battery),
                fuel = carContext.getString(R.string.car_fuel),
                load = carContext.getString(R.string.car_load),
                demo = carContext.getString(R.string.car_demo),
                mil = carContext.getString(R.string.car_mil_on_short),
                throttle = carContext.getString(R.string.car_throttle),
                intake = carContext.getString(R.string.car_intake),
                trip = carContext.getString(R.string.car_trip),
                avgFuel = carContext.getString(R.string.car_avg_fuel),
            ),
        )
    }

    private companion object {
        const val FRAME_MS = 40L
        const val DASH_METERS = 3.6
    }

    override fun onGetTemplate(): Template = NavigationTemplate.Builder()
        .setActionStrip(
            ActionStrip.Builder()
                .addAction(Action.Builder().setTitle(carContext.getString(R.string.car_faults)).setOnClickListener { screenManager.push(FaultsScreen(carContext, deps)) }.build())
                .addAction(
                    Action.Builder()
                        .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, R.drawable.ic_car_alerts)).build())
                        .setOnClickListener { screenManager.push(AlertsCarScreen(carContext, deps)) }
                        .build(),
                )
                .build(),
        )
        .build()
}
