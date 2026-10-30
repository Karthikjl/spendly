package com.kk.spendly.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

/**
 * Date helpers on java.time. The formatters are built once: creating a SimpleDateFormat and several Calendars for every list row
 * showed up as a real cost while scrolling the Activity tab.
 */
internal object Dates {
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
    private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

    fun zone(): ZoneId = ZoneId.systemDefault()

    fun dateOf(ts: Long, zone: ZoneId = zone()): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()

    fun time(ts: Long): String = timeFormat.format(Instant.ofEpochMilli(ts).atZone(zone()))

    fun label(date: LocalDate, today: LocalDate = LocalDate.now(zone())): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> dayFormat.format(date)
    }

    fun dayLabel(ts: Long): String = label(dateOf(ts))

    fun startMillis(date: LocalDate, zone: ZoneId = zone()): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** Last day of the 7-day strip on Home: today in the current month, otherwise the last day of the browsed month. */
    fun weekEnd(monthStart: Calendar, today: LocalDate = LocalDate.now(zone())): LocalDate {
        val first = LocalDate.of(monthStart.get(Calendar.YEAR), monthStart.get(Calendar.MONTH) + 1, 1)
        return if (first.year == today.year && first.month == today.month) today else first.withDayOfMonth(first.lengthOfMonth())
    }
}
