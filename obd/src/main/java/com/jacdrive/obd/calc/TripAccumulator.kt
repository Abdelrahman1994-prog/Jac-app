package com.jacdrive.obd.calc

/**
 * Integrates distance from successive speed samples (trapezoidal rule). Gaps longer than
 * [maxGapMs] are not integrated because the vehicle state during the gap is unknown.
 */
class TripAccumulator(private val maxGapMs: Long = 5_000) {
    var distanceKm: Double = 0.0
        private set
    var fuelUsedLitres: Double = 0.0
        private set

    private var lastSpeedKmh: Double? = null
    private var lastFuelRateLph: Double? = null
    private var lastTimestampMs: Long? = null

    fun reset() {
        distanceKm = 0.0
        fuelUsedLitres = 0.0
        lastSpeedKmh = null
        lastFuelRateLph = null
        lastTimestampMs = null
    }

    /**
     * @param fuelRateLph fuel flow in litres/hour if a reliable source exists, otherwise null.
     */
    fun add(speedKmh: Double, timestampMs: Long, fuelRateLph: Double? = null) {
        val prevT = lastTimestampMs
        val prevV = lastSpeedKmh
        if (prevT != null && prevV != null) {
            val dt = timestampMs - prevT
            if (dt in 1..maxGapMs) {
                val hours = dt / 3_600_000.0
                distanceKm += (prevV + speedKmh) / 2.0 * hours
                val prevF = lastFuelRateLph
                if (prevF != null && fuelRateLph != null) {
                    fuelUsedLitres += (prevF + fuelRateLph) / 2.0 * hours
                }
            }
        }
        lastSpeedKmh = speedKmh
        lastFuelRateLph = fuelRateLph
        lastTimestampMs = timestampMs
    }

    /** L/100km, only once enough distance has been covered for the number to mean anything. */
    fun averageConsumption(minDistanceKm: Double = 0.5): Double? =
        if (distanceKm >= minDistanceKm && fuelUsedLitres > 0) fuelUsedLitres / distanceKm * 100.0 else null
}
