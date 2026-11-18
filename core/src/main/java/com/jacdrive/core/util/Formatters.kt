package com.jacdrive.core.util

import java.util.Locale

/**
 * Number formatting for gauges. Uses Western digits on purpose: automotive clusters in the region
 * use them and they keep fixed-width numerals readable at a glance in both languages.
 */
object Num {
    fun int(value: Double): String = String.format(Locale.US, "%d", value.toLong())
    fun one(value: Double): String = String.format(Locale.US, "%.1f", value)
    fun two(value: Double): String = String.format(Locale.US, "%.2f", value)
}
