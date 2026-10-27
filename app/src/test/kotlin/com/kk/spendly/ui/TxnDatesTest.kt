package com.kk.spendly.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class TxnDatesTest {
    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val newYork = ZoneId.of("America/New_York")

    private fun at(zone: ZoneId, y: Int, m: Int, d: Int, h: Int, min: Int) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun pickerValueIsUtcMidnightOfTheLocalDay() {
        // 00:30 in Kolkata is still the previous day in UTC, which is where naive code picks the wrong date.
        val ts = at(kolkata, 2026, 10, 25, 0, 30)
        val expected = LocalDate.of(2026, 10, 25).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(expected, TxnDates.utcMidnightOf(ts, kolkata))
    }

    @Test
    fun roundTripKeepsTheTimestamp() {
        val now = at(kolkata, 2026, 10, 27, 12, 0)
        for (zone in listOf(kolkata, newYork, ZoneOffset.UTC)) {
            val ts = at(zone, 2026, 10, 25, 20, 15)
            assertEquals(ts, TxnDates.withDate(ts, TxnDates.utcMidnightOf(ts, zone), now, zone))
        }
    }

    @Test
    fun movingTheDayKeepsTheTimeOfDay() {
        val now = at(kolkata, 2026, 10, 27, 12, 0)
        val ts = at(kolkata, 2026, 10, 25, 20, 15)
        val picked = LocalDate.of(2026, 10, 20).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        assertEquals(at(kolkata, 2026, 10, 20, 20, 15), TxnDates.withDate(ts, picked, now, kolkata))
    }

    @Test
    fun neverReturnsAFutureTime() {
        val now = at(kolkata, 2026, 10, 27, 9, 0)
        val ts = at(kolkata, 2026, 10, 25, 20, 15)
        val today = TxnDates.utcMidnightOf(now, kolkata)
        // Picking today with an evening time-of-day, before that time has happened, is clamped to now.
        assertTrue(TxnDates.withDate(ts, today, now, kolkata) <= now)
    }
}
