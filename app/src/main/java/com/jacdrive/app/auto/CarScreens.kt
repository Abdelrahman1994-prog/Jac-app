package com.jacdrive.app.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import com.jacdrive.app.R
import com.jacdrive.core.model.ConnectionState
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.Freshness
import com.jacdrive.core.model.TemperatureUnit
import com.jacdrive.core.model.UnitConverter
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.VehicleSnapshot
import com.jacdrive.core.model.freshness
import com.jacdrive.core.util.Num
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Live gauges on the car screen. Android Auto templates don't allow free drawing, so each value
 * is a grid item whose image is a gauge rendered as a bitmap, with the value as the item title.
 * The number of items follows the host's grid limit (typically 6 while driving); refreshes are
 * capped at one every 2 s, and the host may throttle further. Demo data is labelled.
 */
class LiveScreen(carContext: CarContext, private val deps: CarDeps) : Screen(carContext) {

    private var snapshot: VehicleSnapshot = deps.vehicleData.snapshot.value
    private var tempUnit = TemperatureUnit.CELSIUS
    private var speedUnit = com.jacdrive.core.model.SpeedUnit.KMH
    private var redline = 6000
    private var dirty = false

    init {
        lifecycleScope.launch {
            val s = deps.settings.settings.first()
            tempUnit = s.temperatureUnit
            speedUnit = s.speedUnit
            redline = deps.settings.cockpit.first().redlineRpm
            launch { deps.vehicleData.snapshot.collect { snapshot = it; dirty = true } }
            while (true) {
                delay(REFRESH_MS)
                if (dirty) { dirty = false; invalidate() }
            }
        }
    }

    private data class Gauge(val label: Int, val metric: VehicleMetric, val fraction: (Double) -> Float, val text: (Double) -> String, val danger: Float? = null, val warn: (Double) -> Boolean = { false })

    override fun onGetTemplate(): Template {
        val s = snapshot
        val now = System.currentTimeMillis()
        val sim = s.mode == DataMode.DEMO
        val tempLabel = if (tempUnit == TemperatureUnit.CELSIUS) "°C" else "°F"
        val gauges = listOf(
            Gauge(R.string.car_rpm, VehicleMetric.RPM, { (it / 8000).toFloat() }, { "${Num.int(it)} rpm" }, danger = redline / 8000f),
            Gauge(R.string.car_speed, VehicleMetric.SPEED, { (UnitConverter.speed(it, speedUnit) / UnitConverter.speedScaleMax(speedUnit)).toFloat() },
                { "${Num.int(UnitConverter.speed(it, speedUnit))} ${if (speedUnit == com.jacdrive.core.model.SpeedUnit.KMH) "km/h" else "mph"}" }),
            Gauge(R.string.car_coolant, VehicleMetric.COOLANT_TEMP, { ((it - 40) / 90).toFloat() }, { "${Num.int(UnitConverter.temperature(it, tempUnit))} $tempLabel" }, danger = (105f - 40f) / 90f, warn = { it >= 105 }),
            Gauge(R.string.car_battery, VehicleMetric.BATTERY_VOLTAGE, { ((it - 10) / 6).toFloat() }, { "${Num.one(it)} V" }, warn = { it < 12.0 || it > 15.2 }),
            Gauge(R.string.car_load, VehicleMetric.ENGINE_LOAD, { (it / 100).toFloat() }, { "${Num.int(it)} %" }),
            Gauge(R.string.car_fuel, VehicleMetric.FUEL_LEVEL, { (it / 100).toFloat() }, { "${Num.int(it)} %" }, warn = { it < 10 }),
            Gauge(R.string.car_throttle, VehicleMetric.THROTTLE_POSITION, { (it / 100).toFloat() }, { "${Num.int(it)} %" }),
            Gauge(R.string.car_intake, VehicleMetric.INTAKE_AIR_TEMP, { ((it + 10) / 90).toFloat() }, { "${Num.int(UnitConverter.temperature(it, tempUnit))} $tempLabel" }),
        )
        val limit = runCatching {
            carContext.getCarService(androidx.car.app.constraints.ConstraintManager::class.java)
                .getContentLimit(androidx.car.app.constraints.ConstraintManager.CONTENT_LIMIT_TYPE_GRID)
        }.getOrDefault(6).coerceIn(1, gauges.size)

        val items = ItemList.Builder()
        // Gauges the ECU reports as unsupported are left out so supported ones get the space.
        gauges.filterNot { s.isUnsupported(it.metric) }.take(limit).forEach { g ->
            val v = s[g.metric]
            val fresh = v != null && v.freshness(g.metric, now) == Freshness.LIVE
            val stale = v != null && v.freshness(g.metric, now) == Freshness.STALE
            val color = when {
                v != null && g.warn(v.value) -> android.graphics.Color.rgb(255, 176, 32)
                sim -> android.graphics.Color.rgb(181, 123, 255)
                else -> android.graphics.Color.rgb(54, 194, 255)
            }
            val bmp = GaugeBitmap.render(if (fresh || stale) g.fraction(v!!.value) else null, color, g.danger)
            val title = when {
                v == null -> "—"
                else -> g.text(v.value)
            }
            val subtitle = buildString {
                append(str(g.label))
                if (sim) append(" · ").append(str(R.string.car_sim))
                if (stale) append(" · ").append(str(R.string.car_stale))
            }
            items.addItem(
                androidx.car.app.model.GridItem.Builder()
                    .setTitle(title)
                    .setText(subtitle)
                    .setImage(
                        androidx.car.app.model.CarIcon.Builder(androidx.core.graphics.drawable.IconCompat.createWithBitmap(bmp)).build(),
                        androidx.car.app.model.GridItem.IMAGE_TYPE_LARGE,
                    )
                    .build(),
            )
        }

        val status = when {
            sim -> str(R.string.car_demo)
            s.connection is ConnectionState.Connected -> {
                val base = str(R.string.car_connected, (s.connection as ConnectionState.Connected).adapterName)
                if (s.milOn == true) "$base · ${str(R.string.car_mil_on_short)}" else base
            }
            s.connection is ConnectionState.Connecting -> str(R.string.car_connecting)
            s.connection is ConnectionState.Failed -> str(R.string.car_reconnecting)
            else -> str(R.string.car_not_connected)
        }

        return androidx.car.app.model.GridTemplate.Builder()
            .setTitle(status)
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(items.build())
            .setActionStrip(
                ActionStrip.Builder()
                    .addAction(Action.Builder().setTitle(str(R.string.car_faults)).setOnClickListener { screenManager.push(FaultsScreen(carContext, deps)) }.build())
                    .addAction(
                        // Only one action may carry a text title; the second uses an icon.
                        Action.Builder()
                            .setIcon(androidx.car.app.model.CarIcon.Builder(androidx.core.graphics.drawable.IconCompat.createWithResource(carContext, R.drawable.ic_car_alerts)).build())
                            .setOnClickListener { screenManager.push(AlertsCarScreen(carContext, deps)) }
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    private fun str(id: Int, vararg args: Any) = carContext.getString(id, *args)

    private companion object {
        const val REFRESH_MS = 2_000L
    }
}

/** Codes from the latest fault scan (read-only; scanning and clearing stay on the phone while parked). */
class FaultsScreen(carContext: CarContext, private val deps: CarDeps) : Screen(carContext) {
    private var rows: List<Pair<String, String>>? = null
    private var header: String = ""

    init {
        lifecycleScope.launch {
            val latest = deps.diagnosticDao.observeSessions().first().firstOrNull { it.session.kind == "SCAN" }
            val arabic = carContext.resources.configuration.locales[0].language == "ar"
            header = latest?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.session.timestampMs)) }.orEmpty()
            rows = latest?.codes?.distinctBy { it.code }?.map { c ->
                c.code to deps.dtc.database.lookup(c.code).title.get(arabic)
            }.orEmpty()
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val b = ListTemplate.Builder().setTitle(carContext.getString(R.string.car_faults)).setHeaderAction(Action.BACK)
        val r = rows ?: return b.setLoading(true).build()
        val list = ItemList.Builder()
        if (r.isEmpty()) {
            list.setNoItemsMessage(carContext.getString(if (header.isEmpty()) R.string.car_no_scan else R.string.car_no_codes))
        } else {
            r.take(6).forEach { (code, title) -> list.addItem(Row.Builder().setTitle(code).addText(title).build()) }
        }
        return b.setSingleList(list.build()).build()
    }
}

/** Most recent alerts. */
class AlertsCarScreen(carContext: CarContext, private val deps: CarDeps) : Screen(carContext) {
    private var rows: List<Pair<String, String>>? = null

    init {
        lifecycleScope.launch {
            rows = deps.alertDao.observeRecent().first().take(6).map { e ->
                val title = when (e.type) {
                    "COOLANT_HIGH" -> R.string.car_alert_coolant
                    "BATTERY_LOW" -> R.string.car_alert_battery_low
                    "BATTERY_HIGH" -> R.string.car_alert_battery_high
                    "HIGH_RPM" -> R.string.car_alert_rpm
                    "CHECK_ENGINE" -> R.string.car_alert_mil
                    "OBD_DISCONNECTED" -> R.string.car_alert_link
                    else -> R.string.car_alert_maintenance
                }
                carContext.getString(title) to DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(e.timestampMs))
            }
            invalidate()
        }
    }

    override fun onGetTemplate(): Template {
        val b = ListTemplate.Builder().setTitle(carContext.getString(R.string.car_alerts)).setHeaderAction(Action.BACK)
        val r = rows ?: return b.setLoading(true).build()
        val list = ItemList.Builder()
        if (r.isEmpty()) list.setNoItemsMessage(carContext.getString(R.string.car_no_alerts))
        r.forEach { (t, d) -> list.addItem(Row.Builder().setTitle(t).addText(d).build()) }
        return b.setSingleList(list.build()).build()
    }
}

