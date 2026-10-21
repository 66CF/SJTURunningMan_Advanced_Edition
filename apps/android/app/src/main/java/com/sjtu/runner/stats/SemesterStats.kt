package com.sjtu.runner.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 学期统计的纯逻辑：日期模型、日历展开与显示格式化。
 *
 * 这里不碰网络也不碰 UI，方便单测；网络解析在 `ApiService.semesterStats`。
 */

private val CST: ZoneId = ZoneId.of("Asia/Shanghai")

/** 有记录时 +08:00 的「今天」。服务器与客户端都按北京时间记账。 */
fun todayCst(): LocalDate = LocalDate.now(CST)

enum class DayState {
    /** 区间内没有有效记录。 */
    NONE,

    /** 计入成绩（服务器 `state == "0"`）。 */
    VALID,

    /** 有记录但未计入成绩。 */
    INVALID,
}

data class SemesterRange(val start: LocalDate, val end: LocalDate) {
    companion object {
        /**
         * 服务器给出的日期不可信：起终点颠倒或跨度过大时按「没有区间」处理，
         * 免得异常响应撑爆日历模型。
         */
        fun of(start: LocalDate, end: LocalDate): SemesterRange? {
            val span = ChronoUnit.DAYS.between(start, end)
            if (span < 0 || span > 400) {
                return null
            }
            return SemesterRange(start, end)
        }
    }
}

/** [CalendarDay.day] 为 0 表示空白格（月首月尾补齐、或落在学期区间之外）。 */
data class CalendarDay(val day: Int, val state: DayState)

data class CalendarWeek(val title: String, val days: List<CalendarDay>)

data class SemesterStats(
    val resultKm: Double? = null,
    val totalKm: Double? = null,
    val percent: Double? = null,
    val targetKm: Double? = null,
    val semesterName: String = "",
    val range: SemesterRange? = null,
    val runDays: Map<LocalDate, DayState> = emptyMap(),
) {
    val remainingKm: Double?
        get() {
            val target = targetKm?.takeIf { it.isFinite() } ?: return null
            val done = resultKm?.takeIf { it.isFinite() } ?: return null
            return target - done
        }
}

/** 同一天可能有多条记录：有效记录优先，无效记录不能把它盖掉。 */
fun recordDay(runDays: MutableMap<LocalDate, DayState>, date: LocalDate, state: DayState) {
    if (runDays[date] == DayState.VALID) {
        return
    }
    runDays[date] = state
}

/** 未登录时的占位日历：按月份猜一个学期区间，全部为「未跑」。 */
fun placeholderWeeks(today: LocalDate): List<CalendarWeek> {
    val year = today.year
    val range = when (today.monthValue) {
        1, 2 -> SemesterRange.of(LocalDate.of(year - 1, 9, 1), LocalDate.of(year, 1, 31))
        3, 4, 5, 6, 7 -> SemesterRange.of(LocalDate.of(year, 3, 1), LocalDate.of(year, 7, 31))
        else -> SemesterRange.of(LocalDate.of(year, 9, 1), LocalDate.of(year + 1, 1, 31))
    }
    return range?.let { calendarWeeks(it, emptyMap()) } ?: emptyList()
}

/** 按月份逐月展开成「周行」，周一起始，每月第一行带月份标题。 */
fun calendarWeeks(range: SemesterRange, runDays: Map<LocalDate, DayState>): List<CalendarWeek> {
    val weeks = mutableListOf<CalendarWeek>()
    var year = range.start.year
    var month = range.start.monthValue
    val lastYear = range.end.year
    val lastMonth = range.end.monthValue
    while (year < lastYear || (year == lastYear && month <= lastMonth)) {
        val first = LocalDate.of(year, month, 1)
        val daysInMonth = first.lengthOfMonth()
        val leading = first.dayOfWeek.value - DayOfWeek.MONDAY.value
        val rows = (leading + daysInMonth + 6) / 7
        for (row in 0 until rows) {
            val days = ArrayList<CalendarDay>(7)
            for (slot in 0 until 7) {
                val dayNumber = row * 7 + slot - leading
                if (dayNumber < 0 || dayNumber >= daysInMonth) {
                    days.add(CalendarDay(0, DayState.NONE))
                    continue
                }
                val date = LocalDate.of(year, month, dayNumber + 1)
                if (date.isBefore(range.start) || date.isAfter(range.end)) {
                    days.add(CalendarDay(0, DayState.NONE))
                    continue
                }
                days.add(CalendarDay(dayNumber + 1, runDays[date] ?: DayState.NONE))
            }
            val title = if (row == 0) "%d年%d月".format(Locale.ROOT, year, month) else ""
            weeks.add(CalendarWeek(title, days))
        }
        if (month == 12) {
            year += 1
            month = 1
        } else {
            month += 1
        }
    }
    return weeks
}

fun formatKm(value: Double?): String {
    val finite = value?.takeIf { it.isFinite() } ?: return "--"
    return String.format(Locale.ROOT, "%.2f km", finite)
}

/** 服务器的 `percent` 已经是百分数（`"100.00"`），不要再乘 100。 */
fun formatPercent(value: Double?): String {
    val finite = value?.takeIf { it.isFinite() } ?: return "--"
    return String.format(Locale.ROOT, "%.1f%%", finite)
}

/** 服务器没给 `percent` 时的兜底：达标 / 总公里。分母为 0 时不算。 */
fun percentFromRatio(numerator: Double?, denominator: Double?): Double? {
    val num = numerator?.takeIf { it.isFinite() } ?: return null
    val den = denominator?.takeIf { it.isFinite() } ?: return null
    if (den <= 0.0) {
        return null
    }
    return num / den * 100.0
}

/** 自动排计划的边界：单次距离取服务器允许的 1–5 km，次数取客户端上限 30。 */
const val PLAN_MIN_KM = 1.0
const val PLAN_MAX_KM = 5.0
const val PLAN_MAX_RUNS = 30

data class AutoPlan(val runs: Int, val distanceKm: Double)

/**
 * 按本学期未完成公里数反推「次数 × 单次距离」：先用单次上限摊出次数，再把缺口
 * 平摊到每次并向上取整到 0.01 km，保证这批记录足以补上缺口。
 *
 * 公里数换算成整百分公里再取整，避免 `21 / 5` 这类浮点误差把 4.2 变成 4.21。
 */
fun autoPlan(remainingKm: Double): AutoPlan? {
    val remaining = remainingKm.takeIf { it.isFinite() } ?: return null
    if (remaining <= 0.0) {
        return null
    }
    val centis = Math.round(remaining * 100.0)
    var runs = Math.ceil(centis / (PLAN_MAX_KM * 100.0)).toInt()
    runs = runs.coerceIn(1, PLAN_MAX_RUNS)
    val distance = Math.ceil(centis.toDouble() / runs) / 100.0
    return AutoPlan(runs, distance.coerceIn(PLAN_MIN_KM, PLAN_MAX_KM))
}

/** 填进输入框的距离：去掉多余的零，`4.00 → "4"`、`4.50 → "4.5"`、`4.25 → "4.25"`。 */
fun formatPlanKm(value: Double): String {
    var text = String.format(Locale.ROOT, "%.2f", value)
    text = text.trimEnd('0')
    text = text.trimEnd('.')
    return text
}
