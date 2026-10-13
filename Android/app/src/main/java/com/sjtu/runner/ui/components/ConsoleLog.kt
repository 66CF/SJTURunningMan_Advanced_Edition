package com.sjtu.runner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.sjtu.runner.data.LogLevel
import com.sjtu.runner.ui.theme.ConsoleOnDark
import com.sjtu.runner.ui.theme.ConsoleTextStyle
import com.sjtu.runner.ui.theme.LocalAppColors
import com.sjtu.runner.viewmodel.LogEntry

@Composable
fun ConsoleLog(
    entries: List<LogEntry>,
    modifier: Modifier = Modifier,
    placeholder: String = "就绪。点击「开始任务」启动。"
) {
    val appColors = LocalAppColors.current
    val errorColor = MaterialTheme.colorScheme.error
    val levelColors = remember(appColors, errorColor) {
        mapOf(
            LogLevel.INFO to appColors.consoleOn,
            LogLevel.SUCCESS to appColors.success,
            LogLevel.WARN to appColors.warning,
            LogLevel.ERROR to errorColor
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(appColors.console)
            .border(1.dp, appColors.outlineStrong.copy(alpha = 0.35f)),
        contentAlignment = Alignment.TopStart
    ) {
        val scrollState = rememberScrollState()
        LaunchedEffect(entries.size) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
        if (entries.isEmpty()) {
            Text(
                text = placeholder,
                style = ConsoleTextStyle,
                color = appColors.consoleOn.copy(alpha = 0.55f),
                modifier = Modifier.padding(14.dp)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(14.dp)
            ) {
                entries.forEach { entry ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = entry.time,
                            style = ConsoleTextStyle.copy(color = appColors.consoleOn.copy(alpha = 0.45f))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = levelColors[entry.level] ?: ConsoleOnDark)) {
                                    append(entry.message)
                                }
                            },
                            style = ConsoleTextStyle,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Surface(
        modifier = modifier.height(24.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.16f),
        contentColor = color
    ) {
        androidx.compose.material3.Text(
            text = text,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
