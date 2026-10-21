package com.sjtu.runner.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class SemesterStatsTest {

    private fun date(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)

    private fun days(weeks: List<CalendarWeek>): List<CalendarDay> = weeks.flatMap { it.days }

    @Test
    fun rangeRejectsReversedOrAbsurdSpans() {
        assertNotNull(SemesterRange.of(date(2026, 9, 14), date(2027, 1, 3)))
        assertNull(SemesterRange.of(date(2027, 1, 3), date(2026, 9, 14)))
        assertNull(SemesterRange.of(date(2026, 1, 1), date(2027, 12, 31)))
    }

    @Test
    fun calendarPadsMonthStartOnMondayAndKeepsMonthTitles() {
        // 2026-09-01 是周二：第一行第一格应为空白，第二格是 1 号。
        val range = SemesterRange.of(date(2026, 9, 1), date(2026, 9, 30))!!
        val weeks = calendarWeeks(range, emptyMap())
        assertEquals("2026年9月", weeks[0].title)
        assertEquals(0, weeks[0].days[0].day)
        assertEquals(1, weeks[0].days[1].day)
        assertEquals("", weeks[1].title)
        // 9 月 30 天 + 1 个前导空格 = 31 格 → 5 行，最后一行尾部补空。
        assertEquals(5, weeks.size)
        assertEquals(30, weeks[4].days[2].day)
        assertEquals(0, weeks[4].days[3].day)
        assertEquals(1, weeks.count { it.title == "2026年9月" })
    }

    @Test
    fun calendarSpansMonthsAndYears() {
        val range = SemesterRange.of(date(2026, 9, 14), date(2027, 1, 3))!!
        val weeks = calendarWeeks(range, emptyMap())
        val titles = weeks.map { it.title }.filter { it.isNotEmpty() }
        assertEquals(
            listOf("2026年9月", "2026年10月", "2026年11月", "2026年12月", "2027年1月"),
            titles,
        )
        // 学期前的 9/1~9/13 与学期后的 1/4~1/31 都是空白格。
        val filled = days(weeks).count { it.day != 0 }
        val expected = ChronoUnit.DAYS.between(date(2026, 9, 14), date(2027, 1, 3)) + 1
        assertEquals(expected, filled.toLong())
        assertTrue(weeks[0].days.all { it.day == 0 || it.day >= 14 })
        val january = weeks.last { it.title.isNotEmpty() }
        assertTrue(january.days.filter { it.day != 0 }.all { it.day <= 3 })
    }

    @Test
    fun outOfRangeDatesInsideTheMonthStayBlank() {
        val range = SemesterRange.of(date(2026, 9, 14), date(2026, 9, 20))!!
        val weeks = calendarWeeks(range, emptyMap())
        val present = days(weeks).filter { it.day != 0 }.map { it.day }
        assertEquals((14..20).toList(), present)
    }

    @Test
    fun validRecordBeatsInvalidOnTheSameDay() {
        val runDays = mutableMapOf<LocalDate, DayState>()
        val day = date(2026, 9, 19)
        recordDay(runDays, day, DayState.VALID)
        recordDay(runDays, day, DayState.INVALID)
        assertEquals(DayState.VALID, runDays[day])
        recordDay(runDays, day, DayState.VALID)
        assertEquals(DayState.VALID, runDays[day])

        val other = date(2026, 9, 20)
        recordDay(runDays, other, DayState.INVALID)
        recordDay(runDays, other, DayState.VALID)
        assertEquals(DayState.VALID, runDays[other])
    }

    @Test
    fun runDaysDriveCellStates() {
        val range = SemesterRange.of(date(2026, 9, 14), date(2026, 9, 20))!!
        val runDays = mutableMapOf<LocalDate, DayState>()
        recordDay(runDays, date(2026, 9, 19), DayState.VALID)
        recordDay(runDays, date(2026, 9, 20), DayState.INVALID)
        val states = days(calendarWeeks(range, runDays))
            .filter { it.day != 0 }
            .associate { it.day to it.state }
        assertEquals(DayState.VALID, states[19])
        assertEquals(DayState.INVALID, states[20])
        assertEquals(DayState.NONE, states[14])
    }

    @Test
    fun placeholderRangesFollowTheCalendarSeason() {
        val autumn = placeholderWeeks(date(2026, 10, 21))
        assertEquals("2026年9月", autumn[0].title)
        assertEquals("2027年1月", autumn.last { it.title.isNotEmpty() }.title)
        assertTrue(days(autumn).all { it.state == DayState.NONE })

        val winter = placeholderWeeks(date(2027, 1, 10))
        assertEquals("2026年9月", winter[0].title)
        assertEquals("2027年1月", winter.last { it.title.isNotEmpty() }.title)

        val spring = placeholderWeeks(date(2027, 4, 5))
        assertEquals("2027年3月", spring[0].title)
        assertEquals("2027年7月", spring.last { it.title.isNotEmpty() }.title)
    }

    @Test
    fun formattersFallBackToPlaceholder() {
        assertEquals("80.00 km", formatKm(80.0))
        assertEquals("91.22 km", formatKm(91.223))
        assertEquals("--", formatKm(null))
        assertEquals("--", formatKm(Double.NaN))
        assertEquals("--", formatKm(Double.POSITIVE_INFINITY))
        assertEquals("100.0%", formatPercent(100.0))
        assertEquals("66.7%", formatPercent(66.666))
        assertEquals("--", formatPercent(Double.NaN))
        assertEquals("--", formatPercent(null))
    }

    @Test
    fun ratioFallbackOnlyWhenBothSidesAreUsable() {
        assertEquals(80.0, percentFromRatio(80.0, 100.0)!!, 1e-9)
        assertEquals(100.0, percentFromRatio(91.22, 91.22)!!, 1e-9)
        assertNull(percentFromRatio(80.0, 0.0))
        assertNull(percentFromRatio(Double.NaN, 100.0))
        assertNull(percentFromRatio(null, 100.0))
    }

    @Test
    fun remainingKmNeedsBothSides() {
        assertEquals(20.0, SemesterStats(resultKm = 80.0, targetKm = 100.0).remainingKm!!, 1e-9)
        assertNull(SemesterStats(resultKm = null, targetKm = 100.0).remainingKm)
        assertNull(SemesterStats(resultKm = 80.0, targetKm = null).remainingKm)
        assertNull(SemesterStats(resultKm = Double.NaN, targetKm = 100.0).remainingKm)
    }

    @Test
    fun autoPlanSpreadsTheGapWithinLimits() {
        assertEquals(AutoPlan(1, 4.0), autoPlan(4.0))
        assertEquals(AutoPlan(3, 4.0), autoPlan(12.0))
        assertEquals(AutoPlan(5, 4.2), autoPlan(21.0))
        assertEquals(AutoPlan(5, 4.69), autoPlan(23.45))
        assertEquals(AutoPlan(3, 5.0), autoPlan(15.0))
        // 缺口太小也要留一条有效记录，单次距离不低于 1 km。
        assertEquals(AutoPlan(1, 1.0), autoPlan(0.5))
        // 缺口大到 30 次 × 5 km 也补不完时，按上限给出最接近的计划。
        assertEquals(AutoPlan(30, 5.0), autoPlan(160.0))
        assertNull(autoPlan(0.0))
        assertNull(autoPlan(-3.0))
        assertNull(autoPlan(Double.NaN))
        assertNull(autoPlan(Double.POSITIVE_INFINITY))
    }

    @Test
    fun autoPlanCoversTheGapItReports() {
        for (remaining in listOf(1.0, 3.3, 7.7, 9.99, 12.5, 23.45, 48.6, 99.99)) {
            val plan = autoPlan(remaining)
            assertNotNull("$remaining km 缺口应有计划", plan)
            val resolved = plan!!
            assertTrue("$remaining km 的次数越界", resolved.runs in 1..PLAN_MAX_RUNS)
            assertTrue(
                "$remaining km 的单次距离越界",
                resolved.distanceKm in PLAN_MIN_KM..PLAN_MAX_KM,
            )
            assertTrue(
                "$remaining km 缺口被 $resolved 漏掉",
                resolved.runs * resolved.distanceKm >= remaining,
            )
        }
    }

    @Test
    fun planKmDropsTrailingZeros() {
        assertEquals("4", formatPlanKm(4.0))
        assertEquals("4.5", formatPlanKm(4.5))
        assertEquals("4.25", formatPlanKm(4.25))
        assertEquals("1", formatPlanKm(1.0))
        assertEquals("5", formatPlanKm(5.0))
    }

    @Test
    fun todayUsesBeijingTime() {
        assertTrue(todayCst().year >= 2020)
    }
}
