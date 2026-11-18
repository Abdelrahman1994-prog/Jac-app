package com.jacdrive.analytics.trips

import com.jacdrive.core.database.TripEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** CSV export (RFC 4180, UTF-8 with BOM so spreadsheet apps detect the encoding). */
object TripCsv {
    private const val BOM = "\uFEFF"

    fun build(trips: List<TripEntity>, zone: TimeZone = TimeZone.getDefault()): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = zone }
        fun n(v: Double?, digits: Int) = v?.let { String.format(Locale.US, "%.${digits}f", it) } ?: ""
        val sb = StringBuilder(BOM)
        sb.append("start,end,distance_km,duration_min,moving_min,avg_speed_kmh,max_speed_kmh,avg_rpm,fuel_l,consumption_l_100km,fuel_source\r\n")
        for (t in trips.sortedBy { it.startMs }) {
            sb.append(listOf(
                fmt.format(Date(t.startMs)),
                fmt.format(Date(t.endMs)),
                n(t.distanceKm, 2),
                n(t.durationS / 60.0, 1),
                n(t.movingS / 60.0, 1),
                n(t.avgSpeedKmh, 1),
                n(t.maxSpeedKmh, 0),
                n(t.avgRpm, 0),
                n(t.fuelUsedL, 2),
                n(t.consumptionL100, 1),
                t.fuelSource.orEmpty(),
            ).joinToString(",") { escape(it) })
            sb.append("\r\n")
        }
        return sb.toString()
    }

    private fun escape(v: String) = if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}
