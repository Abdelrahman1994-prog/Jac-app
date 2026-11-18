package com.jacdrive.analytics.trips

import com.jacdrive.core.database.TripEntity
import java.util.Calendar
import java.util.TimeZone

enum class StatsPeriod { DAYS_7, DAYS_30, MONTHS_12 }
enum class StatsMetric { DISTANCE, AVG_SPEED, CONSUMPTION }

data class Bucket(val startMs: Long, val trips: List<TripEntity>) {
    val distanceKm get() = trips.sumOf { it.distanceKm }
    val durationS get() = trips.sumOf { it.durationS }
    /** Distance-weighted average speed; null when no driving. */
    val avgSpeed: Double? get() = durationS.takeIf { it > 0 }?.let { distanceKm / (it / 3600.0) }
    /** Only trips that have fuel data contribute; null when none do. */
    val consumption: Double? get() {
        val withFuel = trips.filter { it.fuelUsedL != null }
        val d = withFuel.sumOf { it.distanceKm }
        return if (d >= 1.0) withFuel.sumOf { it.fuelUsedL!! } / d * 100 else null
    }

    fun value(metric: StatsMetric): Double? = when (metric) {
        StatsMetric.DISTANCE -> distanceKm
        StatsMetric.AVG_SPEED -> avgSpeed
        StatsMetric.CONSUMPTION -> consumption
    }
}

/** Groups completed trips into daily (7 or 30) or monthly (12) buckets ending now. */
object TripStats {
    fun buckets(trips: List<TripEntity>, period: StatsPeriod, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): List<Bucket> {
        val cal = Calendar.getInstance(zone).apply {
            timeInMillis = nowMs
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (period == StatsPeriod.MONTHS_12) set(Calendar.DAY_OF_MONTH, 1)
        }
        val (count, field) = when (period) {
            StatsPeriod.DAYS_7 -> 7 to Calendar.DAY_OF_YEAR
            StatsPeriod.DAYS_30 -> 30 to Calendar.DAY_OF_YEAR
            StatsPeriod.MONTHS_12 -> 12 to Calendar.MONTH
        }
        cal.add(field, -(count - 1))
        val starts = (0..count).map { i ->
            (cal.clone() as Calendar).apply { add(field, i) }.timeInMillis
        }
        val done = trips.filter { it.completed }
        return (0 until count).map { i ->
            Bucket(starts[i], done.filter { it.startMs >= starts[i] && it.startMs < starts[i + 1] })
        }
    }

    fun summary(buckets: List<Bucket>): Bucket = Bucket(buckets.firstOrNull()?.startMs ?: 0, buckets.flatMap { it.trips })
}
