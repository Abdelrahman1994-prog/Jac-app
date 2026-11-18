package com.jacdrive.maintenance

import com.jacdrive.core.database.MaintenanceItemEntity
import com.jacdrive.maintenance.data.DueState
import com.jacdrive.maintenance.data.MaintenanceStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class MaintenanceStatusTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private val day = 86_400_000L

    private fun item(km: Int? = null, months: Int? = null, lastKm: Int? = null, lastDate: Long? = null) =
        MaintenanceItemEntity(1, "ENGINE_OIL", null, km, months, lastKm, lastDate, true, 0)

    @Test fun notConfiguredWithoutIntervals() =
        assertEquals(DueState.NOT_CONFIGURED, MaintenanceStatus.compute(item(), 10_000, 0, utc).state)

    @Test fun needsBaseline() =
        assertEquals(DueState.NO_BASELINE, MaintenanceStatus.compute(item(km = 10_000), 10_000, 0, utc).state)

    @Test fun kmBased() {
        val s = MaintenanceStatus.compute(item(km = 10_000, lastKm = 20_000), 25_000, 0, utc)
        assertEquals(DueState.OK, s.state)
        assertEquals(30_000, s.nextDueKm)
        assertEquals(5_000, s.remainingKm)
        assertEquals(0.5f, s.progress!!, 0.001f)
        assertEquals(DueState.DUE_SOON, MaintenanceStatus.compute(item(km = 10_000, lastKm = 20_000), 29_200, 0, utc).state)
        assertEquals(DueState.OVERDUE, MaintenanceStatus.compute(item(km = 10_000, lastKm = 20_000), 30_100, 0, utc).state)
    }

    @Test fun whicheverComesFirst() {
        // Km is fine, but 6 months have passed.
        val s = MaintenanceStatus.compute(item(km = 10_000, months = 6, lastKm = 20_000, lastDate = 0), 21_000, 200 * day, utc)
        assertEquals(DueState.OVERDUE, s.state)
    }
}
