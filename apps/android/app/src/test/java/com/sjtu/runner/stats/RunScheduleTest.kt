package com.sjtu.runner.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RunScheduleTest {

    private fun date(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)

    @Test
    fun contiguousWhenSkippingIsOff() {
        val schedule = buildSchedule(date(2026, 10, 20), 3, false, setOf(date(2026, 10, 19)), null)
        assertEquals(listOf(date(2026, 10, 20), date(2026, 10, 19), date(2026, 10, 18)), schedule.dates)
        assertTrue(schedule.skipped.isEmpty())
        assertFalse(schedule.truncated)
    }

    @Test
    fun occupiedDaysMoveToEarlierDates() {
        val occupied = setOf(date(2026, 10, 19), date(2026, 10, 17))
        val schedule = buildSchedule(date(2026, 10, 20), 3, true, occupied, null)
        assertEquals(listOf(date(2026, 10, 20), date(2026, 10, 18), date(2026, 10, 16)), schedule.dates)
        assertEquals(listOf(date(2026, 10, 19), date(2026, 10, 17)), schedule.skipped)
        assertFalse(schedule.truncated)
    }

    @Test
    fun semesterStartStopsTheLookback() {
        val schedule = buildSchedule(
            endDate = date(2026, 10, 20),
            runs = 5,
            skipExisting = true,
            occupied = setOf(date(2026, 10, 19), date(2026, 10, 18)),
            earliest = date(2026, 10, 16),
        )
        assertEquals(listOf(date(2026, 10, 20), date(2026, 10, 17), date(2026, 10, 16)), schedule.dates)
        assertEquals(listOf(date(2026, 10, 19), date(2026, 10, 18)), schedule.skipped)
        assertTrue(schedule.truncated)
    }

    @Test
    fun lookbackIsCapped() {
        val end = date(2026, 10, 20)
        val everyDay = (0..MAX_LOOKBACK_DAYS).map { end.minusDays(it) }.toSet()
        val schedule = buildSchedule(end, 30, true, everyDay, null)
        assertTrue(schedule.dates.isEmpty())
        assertEquals(MAX_LOOKBACK_DAYS + 1, schedule.skipped.size.toLong())
        assertTrue(schedule.truncated)
    }

    @Test
    fun skippedDaysAreReportedConcisely() {
        assertEquals("", formatSkippedDays(emptyList()))
        val five = listOf(date(2026, 10, 20), date(2026, 10, 19), date(2026, 10, 18))
        assertEquals(
            "跳过已有记录的日期：2026-10-20、2026-10-19、2026-10-18",
            formatSkippedDays(five),
        )
        val many = (0..6).map { date(2026, 10, 20).minusDays(it.toLong()) }
        assertEquals(
            "跳过已有记录的日期：2026-10-20、2026-10-19、2026-10-18、2026-10-17、2026-10-16 等 7 天",
            formatSkippedDays(many),
        )
    }
}
