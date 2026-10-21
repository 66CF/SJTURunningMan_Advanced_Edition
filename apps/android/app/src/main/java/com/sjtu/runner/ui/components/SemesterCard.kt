package com.sjtu.runner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 「本学期进度」卡片：只读展示当前学期的达标公里与完成率。
 *
 * 与电脑版首页同一张卡片，未登录时两个数值都是 `--`。
 */
@Composable
fun SemesterCard(
    loggedIn: Boolean,
    kmText: String,
    percentText: String,
    modifier: Modifier = Modifier
) {
    SectionCard(
        modifier = modifier,
        title = "本学期进度",
        icon = Icons.Outlined.Insights,
        supporting = if (loggedIn) {
            "登录状态下自动同步，只读展示当前学期。"
        } else {
            "登录账号后自动同步本学期数据。"
        }
    ) {
        SectionDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SemesterMetric(label = "达标公里", value = kmText, modifier = Modifier.weight(1f))
            SemesterMetric(label = "完成率", value = percentText, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SemesterMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
    }
}
