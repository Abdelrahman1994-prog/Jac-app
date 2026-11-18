package com.jacdrive.analytics

import com.jacdrive.analytics.trips.StatsMetric
import com.jacdrive.analytics.trips.StatsPeriod
import com.jacdrive.analytics.trips.TripCsv
import com.jacdrive.analytics.trips.TripStats
import com.jacdrive.analytics.trips.TripTracker
import com.jacdrive.core.database.TripEntity
import com.jacdrive.core.model.ConnectionState
import com.jacdrive.core.model.DataMode
import com.jacdrive.core.model.DataSource
import com.jacdrive.core.model.LiveValue
import com.jacdrive.core.model.VehicleMetric
import com.jacdrive.core.model.VehicleSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class TripTrackerTest {

    private fun live(t: Long, speed: Double, rpm: Double, rate: Double? = null, src: DataSource = DataSource.ECU, mode: DataMode = DataMode.LIVE) =
        VehicleSnapshot(
            mode, ConnectionState.Connected("a", null),
            buildMap {
                put(VehicleMetric.SPEED, LiveValue(speed, t, src))
                put(VehicleMetric.RPM, LiveValue(rpm, t, src))
                rate?.let { put(VehicleMetric.FUEL_RATE, LiveValue(it, t, src)) }
            },
            null, t,
        )

    @Test fun recordsAOneHourDriveAt60() {
        val tr = TripTracker()
        val events = mutableListOf<TripTracker.Event>()
        var t = 1_000L
        repeat(3_601) { tr.onSnapshot(live(t, 60.0, 2000.0, rate = 6.0), t)?.let(events::add); t += 1_000 }
        // Engine off: snapshots stop being LIVE; idle timeout closes the trip.
        repeat(100) { tr.onSnapshot(VehicleSnapshot.offline(t), t)?.let(events::add); t += 1_000 }
        val finished = events.filterIsInstance<TripTracker.Event.Finished>().single().trip
        assertEquals(60.0, finished.distanceKm, 0.05)
        assertEquals(3600.0, finished.durationS.toDouble(), 2.0)
        assertEquals(2000.0, finished.avgRpm!!, 0.1)
        assertEquals(6.0, finished.fuelUsedL!!, 0.01)
        assertEquals(10.0, finished.consumptionL100!!, 0.05)
        assertEquals("ECU_FUEL_RATE", finished.fuelSource)
        assertTrue(events.first() is TripTracker.Event.Started)
        assertTrue(events.any { it is TripTracker.Event.Checkpoint })
    }

    @Test fun demoDataNeverStartsATrip() {
        val tr = TripTracker()
        var t = 1_000L
        repeat(600) {
            assertNull(tr.onSnapshot(live(t, 80.0, 2500.0, src = DataSource.SIMULATED, mode = DataMode.DEMO), t))
            t += 1_000
        }
    }

    @Test fun idlingInTheDrivewayIsDiscarded() {
        val tr = TripTracker()
        val events = mutableListOf<TripTracker.Event>()
        var t = 1_000L
        repeat(30) { tr.onSnapshot(live(t, 0.0, 800.0), t)?.let(events::add); t += 1_000 }
        repeat(100) { tr.onSnapshot(VehicleSnapshot.offline(t), t)?.let(events::add); t += 1_000 }
        assertTrue(events.last() is TripTracker.Event.Discarded)
    }

    @Test fun shortLinkDropContinuesTheSameTrip() {
        val tr = TripTracker()
        val events = mutableListOf<TripTracker.Event>()
        var t = 1_000L
        repeat(120) { tr.onSnapshot(live(t, 50.0, 1800.0), t)?.let(events::add); t += 1_000 }
        repeat(20) { tr.onSnapshot(VehicleSnapshot.offline(t), t)?.let(events::add); t += 1_000 }
        repeat(120) { tr.onSnapshot(live(t, 50.0, 1800.0), t)?.let(events::add); t += 1_000 }
        assertEquals(1, events.count { it is TripTracker.Event.Started })
        assertTrue(events.none { it is TripTracker.Event.Finished })
    }

    private fun trip(start: Long, km: Double, s: Long, fuel: Double? = null) =
        TripEntity(startMs = start, endMs = start + s * 1000, distanceKm = km, durationS = s, movingS = s, maxSpeedKmh = 90.0, avgRpm = 2000.0, fuelUsedL = fuel, fuelSource = fuel?.let { "ECU_FUEL_RATE" }, completed = true)

    @Test fun dailyBucketsAndConsumptionOnlyFromTripsWithFuel() {
        val utc = TimeZone.getTimeZone("UTC")
        val day = 86_400_000L
        val now = 10 * day + 12 * 3_600_000L
        val trips = listOf(trip(10 * day + 1000, 20.0, 1200, fuel = 1.6), trip(10 * day + 5000, 10.0, 600), trip(8 * day, 5.0, 300))
        val b = TripStats.buckets(trips, StatsPeriod.DAYS_7, now, utc)
        assertEquals(7, b.size)
        assertEquals(30.0, b.last().value(StatsMetric.DISTANCE)!!, 1e-9)
        assertEquals(8.0, b.last().value(StatsMetric.CONSUMPTION)!!, 1e-9) // 1.6 L over the 20 km that had fuel data
        assertEquals(5.0, b[4].value(StatsMetric.DISTANCE)!!, 1e-9)
        assertNull(b[4].value(StatsMetric.CONSUMPTION))
        assertEquals(60.0, b.last().value(StatsMetric.AVG_SPEED)!!, 1e-9)
    }

    @Test fun csvHasHeaderBomAndRows() {
        val csv = TripCsv.build(listOf(trip(0, 12.345, 600, 1.0)), TimeZone.getTimeZone("UTC"))
        assertTrue(csv.startsWith("﻿start,end,distance_km"))
        assertTrue(csv.contains("1970-01-01 00:00:00,1970-01-01 00:10:00,12.35,10.0,10.0,74.1,90,2000,1.00,8.1,ECU_FUEL_RATE"))
    }
}
