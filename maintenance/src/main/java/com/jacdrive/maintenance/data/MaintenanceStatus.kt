package com.jacdrive.maintenance.data

import com.jacdrive.core.database.MaintenanceItemEntity
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.min

enum class DueState { NOT_CONFIGURED, NO_BASELINE, OK, DUE_SOON, OVERDUE }

data class ItemStatus(
    val state: DueState,
    val nextDueKm: Int?,
    val remainingKm: Int?,
    val nextDueDateMs: Long?,
    val remainingDays: Int?,
    /** 0 = just serviced, 1 = due now (by whichever limit is closer). */
    val progress: Float?,
)

/**
 * Due-date logic: an item is due at last service + interval (km and/or months), whichever comes
 * first. DUE_SOON = within 10 % of the interval, or 1000 km / 30 days, whichever is larger.
 */
object MaintenanceStatus {
    private const val DAY_MS = 86_400_000L

    fun compute(item: MaintenanceItemEntity, odometerKm: Int?, nowMs: Long, zone: TimeZone = TimeZone.getDefault()): ItemStatus {
        val intervalKm = item.intervalKm
        val intervalMonths = item.intervalMonths
        val lastKm = item.lastServiceKm
        val lastDate = item.lastServiceDateMs
        if (intervalKm == null && intervalMonths == null) return ItemStatus(DueState.NOT_CONFIGURED, null, null, null, null, null)

        val nextKm = if (intervalKm != null && lastKm != null) lastKm + intervalKm else null
        val remainingKm = if (nextKm != null && odometerKm != null) nextKm - odometerKm else null
        val nextDate = if (intervalMonths != null && lastDate != null) {
            Calendar.getInstance(zone).apply { timeInMillis = lastDate; add(Calendar.MONTH, intervalMonths) }.timeInMillis
        } else null
        val remainingDays = nextDate?.let { Math.floorDiv(it - nowMs, DAY_MS).toInt() }

        if (remainingKm == null && remainingDays == null) return ItemStatus(DueState.NO_BASELINE, nextKm, null, nextDate, null, null)

        val kmProgress = if (remainingKm != null && intervalKm != null && intervalKm > 0) 1f - remainingKm.toFloat() / intervalKm else null
        val dayProgress = if (remainingDays != null && nextDate != null && lastDate != null) {
            val total = ((nextDate - lastDate) / DAY_MS).coerceAtLeast(1)
            1f - remainingDays.toFloat() / total
        } else null
        val progress = listOfNotNull(kmProgress, dayProgress).maxOrNull()?.coerceIn(0f, 1.2f)

        val kmSoon = intervalKm?.let { maxOf(1000, it / 10) }
        val daySoon = intervalMonths?.let { maxOf(30, it * 3) }
        val state = when {
            (remainingKm != null && remainingKm <= 0) || (remainingDays != null && remainingDays <= 0) -> DueState.OVERDUE
            (remainingKm != null && kmSoon != null && remainingKm <= kmSoon) || (remainingDays != null && daySoon != null && remainingDays <= daySoon) -> DueState.DUE_SOON
            else -> DueState.OK
        }
        return ItemStatus(state, nextKm, remainingKm, nextDate, remainingDays, progress?.let { min(it, 1.2f) })
    }
}
