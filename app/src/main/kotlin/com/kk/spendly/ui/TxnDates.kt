package com.kk.spendly.ui

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** Date helpers for the transaction form. Material's date picker speaks in UTC midnights; transactions use local time. */
internal object TxnDates {
    /** The picker value (UTC midnight) for the local calendar day that [timestamp] falls on. */
    fun utcMidnightOf(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** [timestamp] moved to the day the picker chose, keeping its time of day, and never later than [now]. */
    fun withDate(
        timestamp: Long,
        pickedUtcMidnight: Long,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val day = Instant.ofEpochMilli(pickedUtcMidnight).atZone(ZoneOffset.UTC).toLocalDate()
        val time = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalTime()
        return minOf(day.atTime(time).atZone(zone).toInstant().toEpochMilli(), now)
    }
}
