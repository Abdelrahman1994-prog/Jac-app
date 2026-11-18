package com.jacdrive.app.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
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
 * Live vehicle data as a template list (max 6 rows, the driving limit). Values refresh at most
 * every 2 s; the host may throttle further. Speed is deliberately not shown — the car's own
 * cluster already displays it.
 */
class LiveScreen(carContext: CarContext, private val deps: CarDeps) : Screen(carContext) {

    private var snapshot: VehicleSnapshot = deps.vehicleData.snapshot.value
    private var tempUnit = TemperatureUnit.CELSIUS
    private var dirty = false

    init {
        lifecycleScope.launch {
            val s = deps.settings.settings.first()
            tempUnit = s.temperatureUnit
            launch { deps.vehicleData.snapshot.collect { snapshot = it; dirty = true } }
            while (true) {
                delay(REFRESH_MS)
                if (dirty) { dirty = false; invalidate() }
            }
        }
    }

    override fun onGetTemplate(): Template {
        val s = snapshot
        val now = System.currentTimeMillis()
        val sim = s.mode == DataMode.DEMO
        fun value(m: VehicleMetric, fmt: (Double) -> String): String {
            val v = s[m]
            return when {
                s.isUnsupported(m) -> str(R.string.car_not_supported)
                v == null || v.freshness(m, now) == Freshness.MISSING -> "—"
                v.freshness(m, now) == Freshness.STALE -> "${fmt(v.value)} · ${str(R.string.car_stale)}"
                else -> fmt(v.value) + if (sim) " · ${str(R.string.car_sim)}" else ""
            }
        }
        val connection = when {
            sim -> str(R.string.car_demo)
            s.connection is ConnectionState.Connected -> str(R.string.car_connected, (s.connection as ConnectionState.Connected).adapterName)
            s.connection is ConnectionState.Connecting -> str(R.string.car_connecting)
            s.connection is ConnectionState.Failed -> str(R.string.car_reconnecting)
            else -> str(R.string.car_not_connected)
        }
        val tempLabel = if (tempUnit == TemperatureUnit.CELSIUS) "°C" else "°F"
        val coolant = s[VehicleMetric.COOLANT_TEMP]?.value
        val list = ItemList.Builder()
            .addItem(row(R.string.car_link, connection))
            .addItem(
                Row.Builder().setTitle(str(R.string.car_coolant))
                    .addText(colored(value(VehicleMetric.COOLANT_TEMP) { "${Num.int(UnitConverter.temperature(it, tempUnit))} $tempLabel" }, coolant != null && coolant >= 105))
                    .build(),
            )
            .addItem(row(R.string.car_battery, value(VehicleMetric.BATTERY_VOLTAGE) { "${Num.one(it)} V" }))
            .addItem(row(R.string.car_engine, value(VehicleMetric.RPM) { "${Num.int(it)} rpm" } + "  ·  " + value(VehicleMetric.ENGINE_LOAD) { "${Num.int(it)} %" }))
            .addItem(row(R.string.car_fuel, value(VehicleMetric.FUEL_LEVEL) { "${Num.int(it)} %" }))
            .addItem(
                Row.Builder().setTitle(str(R.string.car_mil))
                    .addText(
                        when (s.milOn) {
                            true -> colored(str(R.string.car_mil_on), true)
                            false -> androidx.car.app.model.CarText.create(str(R.string.car_mil_off))
                            null -> androidx.car.app.model.CarText.create(str(R.string.car_mil_unknown))
                        },
                    ).build(),
            )
            .build()

        return ListTemplate.Builder()
            .setTitle("JAC DRIVE")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(list)
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
    private fun row(title: Int, text: String) = Row.Builder().setTitle(str(title)).addText(text).build()

    private fun colored(text: String, alert: Boolean): androidx.car.app.model.CarText {
        if (!alert) return androidx.car.app.model.CarText.create(text)
        val span = android.text.SpannableString(text)
        span.setSpan(androidx.car.app.model.ForegroundCarColorSpan.create(CarColor.RED), 0, text.length, android.text.Spanned.SPAN_INCLUSIVE_EXCLUSIVE)
        return androidx.car.app.model.CarText.create(span)
    }

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

