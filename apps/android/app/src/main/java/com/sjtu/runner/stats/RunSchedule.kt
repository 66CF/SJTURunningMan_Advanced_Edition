package com.sjtu.runner.stats

import java.time.LocalDate

/** 跳过已有记录时最多向前翻多少天，避免一路翻到学期之外。 */
const val MAX_LOOKBACK_DAYS = 120L

data class RunSchedule(
    /** 实际要上传的日期，从「结束日期」向前排列。 */
    val dates: List<LocalDate>,
    /** 因为已经有记录而被跳过的日期。 */
    val skipped: List<LocalDate>,
    /** 向前翻到上限或学期起点仍未凑够设定的次数。 */
    val truncated: Boolean,
)

/**
 * 决定这批记录落在哪些日期。开启「跳过已有记录」后仍然上传设定的次数，
 * 只是把已有记录的那几天往后（更早）挪；关闭时保持原来的连续向前生成。
 */
fun buildSchedule(
    endDate: LocalDate,
    runs: Int,
    skipExisting: Boolean,
    occupied: Set<LocalDate>,
    earliest: LocalDate?,
): RunSchedule {
    val dates = mutableListOf<LocalDate>()
    val skipped = mutableListOf<LocalDate>()
    if (!skipExisting) {
        for (index in 0 until runs) {
            dates.add(endDate.minusDays(index.toLong()))
        }
        return RunSchedule(dates, skipped, false)
    }
    var offset = 0L
    var truncated = false
    while (dates.size < runs) {
        val date = endDate.minusDays(offset)
        if (offset > MAX_LOOKBACK_DAYS || (earliest != null && date.isBefore(earliest))) {
            truncated = true
            break
        }
        if (occupied.contains(date)) {
            skipped.add(date)
        } else {
            dates.add(date)
        }
        offset += 1
    }
    return RunSchedule(dates, skipped, truncated)
}

/** 日志里那句「跳过已有记录的日期」：最多列出前 5 天，再多只报数量。 */
fun formatSkippedDays(skipped: List<LocalDate>): String {
    if (skipped.isEmpty()) {
        return ""
    }
    val shown = skipped.take(5).joinToString("、") { it.toString() }
    return if (skipped.size > 5) {
        "跳过已有记录的日期：$shown 等 ${skipped.size} 天"
    } else {
        "跳过已有记录的日期：$shown"
    }
}
