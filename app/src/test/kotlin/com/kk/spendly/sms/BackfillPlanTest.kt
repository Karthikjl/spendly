package com.kk.spendly.sms

import org.junit.Assert.assertEquals
import org.junit.Test

class BackfillPlanTest {
    private val day = 24 * 3_600_000L
    private val now = 100 * day
    private val v = BackfillPlan.PARSER_VERSION

    @Test
    fun firstRunReadsEverything() {
        assertEquals(0L, BackfillPlan.sinceMillis(0, 0L, 0L, now))
    }

    @Test
    fun aParserUpdateReadsEverythingOnce() {
        assertEquals(0L, BackfillPlan.sinceMillis(v - 1, now - day, now - day, now))
    }

    @Test
    fun normallyOnlyRecentMessagesAreRead() {
        // Scanned up to 1 day ago with a 2-day overlap: reads from 3 days ago.
        assertEquals(now - 3 * day, BackfillPlan.sinceMillis(v, now - day, now - 2 * day, now))
    }

    @Test
    fun aWeeklyFullRescanCatchesRestoredOldMessages() {
        assertEquals(0L, BackfillPlan.sinceMillis(v, now - day, now - 8 * day, now))
    }

    @Test
    fun overlapNeverGoesBeforeTheStartOfTime() {
        assertEquals(0L, BackfillPlan.sinceMillis(v, day, now - day, now))
    }
}
