package com.sjtu.runner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.Intent
import android.net.Uri
import com.sjtu.runner.config.AppConfig
import com.sjtu.runner.ui.theme.LocalAppColors
import com.sjtu.runner.update.UpdateChecker
import com.sjtu.runner.update.UpdateResult
import com.sjtu.runner.viewmodel.RouteConflict
import com.sjtu.runner.viewmodel.RouteDecision

private fun openUrl(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<UpdateResult?>(null) }
    var checking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("交我润", fontWeight = FontWeight.Bold)
                StatusPill(
                    text = "v${AppConfig.VERSION_NAME}",
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    AppConfig.DISCLAIMER_TEXT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "界面优化：Github@CEQ151\n三改：Github@JackyTJie\n二改：Github@accelerator-s\n原作者：Github@Labyrinth0419",
                    style = MaterialTheme.typography.bodySmall
                )
                when (val current = result) {
                    is UpdateResult.UpToDate -> Text(
                        "已是最新版本 ${current.latestTag}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAppColors.current.success
                    )

                    is UpdateResult.UpdateAvailable -> Text(
                        "发现新版本 ${current.latest.tag}",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalAppColors.current.success
                    )

                    is UpdateResult.Failed -> Text(
                        "检查更新失败：${current.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )

                    null -> Unit
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (checking) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                        Text("检查中…", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    TextButton(onClick = { checking = true }) { Text("检查更新") }
                }
                TextButton(onClick = onDismiss) { Text("知道了") }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LinkButton(icon = Icons.Outlined.Code, label = "仓库") {
                    openUrl(context, AppConfig.GITHUB_REPO_URL)
                }
                LinkButton(icon = Icons.AutoMirrored.Outlined.OpenInNew, label = "水源") {
                    openUrl(context, AppConfig.SHUIYUAN_TOPIC_URL)
                }
                if (result is UpdateResult.UpdateAvailable) {
                    LinkButton(icon = Icons.Outlined.CloudDownload, label = "下载") {
                        openUrl(context, (result as UpdateResult.UpdateAvailable).latest.url)
                    }
                }
            }
        }
    )

    LaunchedEffect(checking) {
        if (!checking) return@LaunchedEffect
        val checked = UpdateChecker.checkForUpdates()
        result = checked
        checking = false
    }
}

@Composable
private fun LinkButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(onClick = onClick) {
        Icon(icon, null, modifier = Modifier.padding(end = 6.dp))
        Text(label)
    }
}
