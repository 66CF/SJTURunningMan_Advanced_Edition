package com.sjtu.runner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sjtu.runner.config.AppConfig
import com.sjtu.runner.viewmodel.RouteConflict
import com.sjtu.runner.viewmodel.RouteDecision

@Composable
fun CommitmentDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    var accepted by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text("使用承诺书") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    AppConfig.COMMITMENT_TEXT,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                    Text(
                        "我已阅读以上内容，并承诺不会将本软件用于任何违规操作。",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept, enabled = accepted) { Text("我承诺，进入软件") }
        },
        dismissButton = {
            TextButton(onClick = onDecline) { Text("退出") }
        }
    )
}

@Composable
fun RouteConflictDialog(
    conflict: RouteConflict,
    onDecision: (RouteDecision) -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDecision(RouteDecision.CANCEL) },
        title = { Text("路线距离提醒") },
        text = {
            Text(
                "当前路线「${conflict.routeName}」长度为 " +
                    String.format("%.2fkm", conflict.routeDistanceM / 1000.0) +
                    "，超过了您选择的 " +
                    String.format("%.2fkm", conflict.targetDistanceM / 1000.0) +
                    "。\n\n削减：自动截断路线至目标距离\n完整路线：按整条路线跑步并同步目标距离",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = { onDecision(RouteDecision.TRUNCATE) }) { Text("削减路线") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onDecision(RouteDecision.CANCEL) }) { Text("取消任务") }
                TextButton(onClick = { onDecision(RouteDecision.FULL_ROUTE) }) { Text("完整路线") }
            }
        }
    )
}
