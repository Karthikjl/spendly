package com.kk.spendly.ui

import com.kk.spendly.data.TxnEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** One day of the "Last 7 days" strip: what was spent and what was received, and how many transactions made it up. */
internal data class DayBar(val date: LocalDate, val letter: String, val spent: Long, val received: Long, val count: Int)

/** The seven days ending on [end], oldest first, totalled in a single pass over [txns]. */
internal fun weekSummary(txns: List<TxnEntity>, end: LocalDate, zone: ZoneId = Dates.zone()): List<DayBar> {
    val spent = HashMap<LocalDate, Long>()
    val received = HashMap<LocalDate, Long>()
    val count = HashMap<LocalDate, Int>()
    for (t in txns) {
        val day = Dates.dateOf(t.timestamp, zone)
        count.merge(day, 1, Int::plus)
        if (t.isDebit) spent.merge(day, t.amountPaise, Long::plus) else received.merge(day, t.amountPaise, Long::plus)
    }
    return (6 downTo 0).map { back ->
        val day = end.minusDays(back.toLong())
        DayBar(day, day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), spent[day] ?: 0L, received[day] ?: 0L, count[day] ?: 0)
    }
}
