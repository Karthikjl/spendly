package com.kk.spendly.ui

import com.kk.spendly.data.TxnEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar

class WeekSummaryTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val end = LocalDate.of(2026, 10, 27)

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int, s: Int = 0) =
        LocalDateTime.of(y, m, d, h, min, s).atZone(zone).toInstant().toEpochMilli()

    private fun txn(ts: Long, paise: Long, debit: Boolean, id: Long = ts) =
        TxnEntity(id, "k$id", paise, debit, "M", if (debit) "Food" else "Income", null, ts)

    @Test
    fun sevenDaysOldestFirstEndingOnTheEndDate() {
        val days = weekSummary(emptyList(), end, zone)
        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 10, 21), days.first().date)
        assertEquals(end, days.last().date)
        assertTrue(days.all { it.spent == 0L && it.received == 0L && it.count == 0 })
        assertTrue(days.all { it.letter.isNotEmpty() })
    }

    @Test
    fun spentAndReceivedAreTotalledSeparatelyPerDay() {
        val days = weekSummary(
            listOf(
                txn(at(2026, 10, 27, 9, 0), 10_000, true), txn(at(2026, 10, 27, 18, 0), 5_000, true),
                txn(at(2026, 10, 27, 12, 0), 250_000, false),
                txn(at(2026, 10, 26, 8, 0), 700, true),
            ),
            end, zone,
        )
        val today = days.last(); val yesterday = days[5]
        assertEquals(15_000L, today.spent); assertEquals(250_000L, today.received); assertEquals(3, today.count)
        assertEquals(700L, yesterday.spent); assertEquals(0L, yesterday.received); assertEquals(1, yesterday.count)
    }

    @Test
    fun midnightBelongsToTheNewDay() {
        val days = weekSummary(
            listOf(txn(at(2026, 10, 27, 0, 0, 0), 100, true), txn(at(2026, 10, 26, 23, 59, 59), 200, true)),
            end, zone,
        )
        assertEquals(100L, days.last().spent)
        assertEquals(200L, days[5].spent)
    }

    @Test
    fun transactionsOutsideTheWeekAreIgnored() {
        val days = weekSummary(listOf(txn(at(2026, 10, 20, 12, 0), 999, true), txn(at(2026, 10, 28, 12, 0), 888, true)), end, zone)
        assertEquals(0L, days.sumOf { it.spent })
    }

    @Test
    fun theStripEndsTodayInTheCurrentMonthAndAtMonthEndWhenBrowsingBack() {
        val today = LocalDate.of(2026, 10, 27)
        fun month(y: Int, m: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, 1) }
        assertEquals(today, Dates.weekEnd(month(2026, 10), today))
        assertEquals(LocalDate.of(2026, 9, 30), Dates.weekEnd(month(2026, 9), today))
        assertEquals(LocalDate.of(2026, 2, 28), Dates.weekEnd(month(2026, 2), today))
    }

    @Test
    fun dayLabelsReadNaturally() {
        val today = LocalDate.of(2026, 10, 27)
        assertEquals("Today", Dates.label(today, today))
        assertEquals("Yesterday", Dates.label(today.minusDays(1), today))
        assertTrue(Dates.label(today.minusDays(3), today).contains("24"))
    }
}
