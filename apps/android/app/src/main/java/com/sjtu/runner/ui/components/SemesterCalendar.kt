package com.sjtu.runner.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sjtu.runner.stats.CalendarDay
import com.sjtu.runner.stats.CalendarWeek
import com.sjtu.runner.stats.DayState
import com.sjtu.runner.ui.theme.LocalAppColors

private val WEEK_LABELS = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * 「学期日历」卡片：周一起始，逐月展开本学期每一天。
 *
 * 实心绿 = 计入成绩，描边 = 有记录但未计入，灰 = 未跑，空白格 = 补齐或不在学期内。
 */
@Composable
fun SemesterCalendar(
    loggedIn: Boolean,
    weeks: List<CalendarWeek>,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    SectionCard(
        modifier = modifier,
        title = "学期日历",
        icon = Icons.Outlined.DateRange,
        supporting = if (loggedIn) {
            "实心绿=计入成绩，描边=无效记录，灰=未跑。"
        } else {
            "登录后显示本学期打卡记录。"
        }
    ) {
        SectionDivider()
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEK_LABELS.forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            weeks.forEach { week ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        week.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (week.title.isEmpty()) 0.dp else 18.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.days.forEach { day ->
                            DayCell(
                                day = day,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp)
                            )
                        }
                    }
                }
            }
        }
        if (weeks.isEmpty()) {
            Text(
                "服务器未返回本学期起止日期。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DayCell(
    day: CalendarDay,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (day.day <= 0) return@Box
        val background = when (day.state) {
            DayState.VALID -> appColors.runValid
            DayState.INVALID -> appColors.runInvalid
            DayState.NONE -> appColors.calendarEmpty
        }
        // 无效记录只描边，不填实心绿，和电脑版 DayCell 一致。
        val outlined = day.state == DayState.INVALID
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(background, CircleShape)
                .let { target ->
                    if (outlined) target.border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary)) else target
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                day.day.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontSize = 11.sp,
                fontWeight = if (day.state == DayState.VALID) FontWeight.SemiBold else FontWeight.Normal,
                color = appColors.calendarInk,
                textAlign = TextAlign.Center
            )
        }
    }
}
