package com.sjtu.runner.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.request.ImageRequest
import com.sjtu.runner.config.AppConfig
import com.sjtu.runner.ui.components.AboutDialog
import com.sjtu.runner.ui.components.ChoiceChips
import com.sjtu.runner.ui.components.CommitmentDialog
import com.sjtu.runner.ui.components.ConsoleLog
import com.sjtu.runner.ui.components.DistanceSlider
import com.sjtu.runner.ui.components.MenuDropdown
import com.sjtu.runner.ui.components.MenuOption
import com.sjtu.runner.ui.components.NumberField
import com.sjtu.runner.ui.components.RouteConflictDialog
import com.sjtu.runner.ui.components.RoutePreviewCanvas
import com.sjtu.runner.ui.components.SectionCard
import com.sjtu.runner.ui.components.SectionDivider
import com.sjtu.runner.ui.components.StatusCard
import com.sjtu.runner.ui.components.StatusPill
import com.sjtu.runner.ui.components.TimeField
import com.sjtu.runner.ui.theme.LocalAppColors
import com.sjtu.runner.viewmodel.MainViewModel
import com.sjtu.runner.viewmodel.RouteDecision
import com.sjtu.runner.viewmodel.RunConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val PREFS = "app_prefs"
private const val KEY_COMMITMENT_ACCEPTED = "commitment_accepted"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onNavigateToRouteDesign: () -> Unit,
    onReloginRequested: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE) }

    val logs by viewModel.log.collectAsStateWithLifecycle()
    val running by viewModel.running.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val routes by viewModel.routes.collectAsStateWithLifecycle()
    val selectedRoute by viewModel.selectedRoute.collectAsStateWithLifecycle()
    val preview by viewModel.preview.collectAsStateWithLifecycle()
    val conflict by viewModel.routeConflict.collectAsStateWithLifecycle()
    val reloginRequired by viewModel.reloginRequired.collectAsStateWithLifecycle()

    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenHeightDp < 700
    val logHeight = if (isCompact) 150.dp else 210.dp
    val previewHeight = if (isCompact) 120.dp else 150.dp

    var days by remember { mutableIntStateOf(1) }
    var showCustomDays by remember { mutableStateOf(false) }
    var customDaysText by remember { mutableStateOf("") }
    var hour by remember { mutableIntStateOf(AppConfig.DEFAULT_RUN_HOUR) }
    var minute by remember { mutableIntStateOf(AppConfig.DEFAULT_RUN_MINUTE) }
    var second by remember { mutableIntStateOf(AppConfig.DEFAULT_RUN_SECOND) }
    var distanceKm by remember { mutableStateOf(AppConfig.DEFAULT_DISTANCE_KM) }
    var dateText by remember {
        mutableStateOf(LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE))
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var routeToDelete by remember { mutableStateOf<com.sjtu.runner.route.RouteInfo?>(null) }
    var commitmentAccepted by remember { mutableStateOf(prefs.getBoolean(KEY_COMMITMENT_ACCEPTED, false)) }
    var showGif by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val distanceRange = AppConfig.MIN_DISTANCE_KM.toFloat()..AppConfig.MAX_DISTANCE_KM.toFloat()
    val config = RunConfig(
        days = days,
        distanceKm = distanceKm,
        hour = hour,
        minute = minute,
        second = second,
        startDate = runCatching { LocalDate.parse(dateText) }.getOrElse { LocalDate.now().minusDays(1) }
    )
    LaunchedEffect(config) { viewModel.updateConfig(config) }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            LocalAppColors.current.gradientStart.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.combinedClickable(
                                onClick = {},
                                onDoubleClick = { showGif = true }
                            )
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.DirectionsRun,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("交我润", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "v${AppConfig.VERSION_NAME}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showAbout = true }) {
                            Icon(Icons.Outlined.Info, contentDescription = "关于与检查更新")
                        }
                        IconButton(onClick = onReloginRequested) {
                            Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = "重新登录")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                    ),
                    scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                BottomActionBar(
                    running = running,
                    canStart = selectedRoute != null,
                    onStart = {
                        val error = viewModel.validate(config)
                        if (error != null) {
                            scope.launch { snackbarHostState.showSnackbar(error) }
                        } else {
                            viewModel.startUpload(config)
                        }
                    },
                    onStop = { viewModel.stopUpload() },
                    onPreview = { viewModel.previewRoute() }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StatusCard(
                    progress = progress,
                    headline = "${days} 次 · %.1f km".format(distanceKm),
                    stats = listOf(
                        "次数" to days.toString(),
                        "距离" to "%.1f km".format(distanceKm),
                        "时间" to "%02d:%02d:%02d".format(hour, minute, second)
                    )
                )

                if (reloginRequired) {
                    ReloginBanner(onRelogin = onReloginRequested)
                }

                SectionCard(
                    title = "跑步路线",
                    icon = Icons.Outlined.Route,
                    supporting = selectedRoute?.let { "${it.pointCount} 个坐标点" } ?: "请选择路线",
                    action = {
                        IconButton(onClick = onNavigateToRouteDesign) {
                            Icon(Icons.Outlined.Add, contentDescription = "设计新路线")
                        }
                    }
                ) {
                    MenuDropdown(
                        value = selectedRoute?.name ?: "请选择路线",
                        label = "预设路线",
                        modifier = Modifier.fillMaxWidth(),
                        options = routes.map { route ->
                            MenuOption(
                                key = route.id,
                                title = route.name,
                                subtitle = "${route.pointCount} 个点" + if (route.isDefault) " · 内置" else "",
                                selected = selectedRoute?.id == route.id
                            )
                        },
                        onSelect = { option ->
                            routes.firstOrNull { it.id == option.key }?.let { viewModel.selectRoute(it) }
                        }
                    )

                    RoutePreviewCanvas(
                        samples = preview?.samples ?: emptyList(),
                        jumpCount = preview?.jumpCount ?: 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(previewHeight)
                            .clip(RoundedCornerShape(14.dp))
                            .background(LocalAppColors.current.cardLow)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                preview?.title ?: "路线预览",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                preview?.summary ?: "点击「预览路线」按当前设置离线生成",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val jumps = preview?.jumpCount ?: 0
                        if (jumps > 0) {
                            StatusPill(text = "$jumps 处跳段", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    SectionDivider()

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.previewRoute() },
                            enabled = !running && selectedRoute != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("预览路线")
                        }
                        OutlinedButton(
                            onClick = { routeToDelete = selectedRoute },
                            enabled = !running && selectedRoute != null && selectedRoute?.isDefault == false,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("删除路线")
                        }
                    }
                }

                SectionCard(title = "任务设置", icon = Icons.Outlined.Schedule) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("跑步次数", style = MaterialTheme.typography.titleSmall)
                        if (!showCustomDays) {
                            ChoiceChips(
                                options = listOf("1", "3", "5", "7", "10", "15", "30", "自定义"),
                                selected = days.toString(),
                                onSelect = { option ->
                                    if (option == "自定义") {
                                        showCustomDays = true
                                        customDaysText = days.toString()
                                    } else {
                                        days = option.toIntOrNull() ?: days
                                    }
                                }
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                NumberField(
                                    value = customDaysText,
                                    onValueChange = { customDaysText = it },
                                    width = 140.dp,
                                    label = "次数 1-30",
                                    maxLength = 2
                                )
                                TextButton(
                                    onClick = {
                                        days = customDaysText.toIntOrNull()
                                            ?.coerceIn(1, AppConfig.MAX_RUN_TIMES) ?: days
                                        showCustomDays = false
                                    }
                                ) { Text("确定") }
                            }
                        }
                    }

                    SectionDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("开始时间", style = MaterialTheme.typography.titleSmall)
                        TimeField(
                            hour = hour,
                            minute = minute,
                            second = second,
                            onChange = { h, m, s ->
                                hour = h
                                minute = m
                                second = s
                            }
                        )
                        Text(
                            "每次跑步自动加入 ±10 分钟 / ±30 秒随机波动",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    SectionDivider()

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("结束日期", style = MaterialTheme.typography.titleSmall)
                        OutlinedTextField(
                            value = dateText,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("YYYY-MM-DD") },
                            trailingIcon = {
                                IconButton(onClick = { if (!running) showDatePicker = true }) {
                                    Icon(Icons.Outlined.CalendarMonth, contentDescription = "选择日期")
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !running) { showDatePicker = true },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }

                    SectionDivider()

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Straighten,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("目标距离", style = MaterialTheme.typography.titleSmall)
                        }
                        DistanceSlider(
                            distanceKm = distanceKm,
                            onDistanceChange = { distanceKm = it },
                            valueRange = distanceRange
                        )
                    }
                }

                SectionCard(
                    title = "运行日志",
                    icon = Icons.Outlined.Terminal,
                    supporting = if (running) "任务运行中" else "共 ${logs.size} 条记录",
                    action = {
                        if (!running && logs.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearLogs() }) { Text("清空") }
                        }
                    }
                ) {
                    ConsoleLog(
                        entries = logs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(logHeight)
                            .clip(RoundedCornerShape(14.dp))
                    )
                }

                DisclaimerNotice()

                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (showGif) {
            SurpriseOverlay(onDismiss = { showGif = false })
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = config.startDate
                .atStartOfDay(ZoneId.of("UTC"))
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        dateText = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.of("UTC"))
                            .toLocalDate()
                            .format(DateTimeFormatter.ISO_LOCAL_DATE)
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    conflict?.let { routeConflict ->
        RouteConflictDialog(
            conflict = routeConflict,
            onDecision = { decision -> viewModel.resolveRouteConflict(decision) }
        )
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    routeToDelete?.let { route ->
        AlertDialog(
            onDismissRequest = { routeToDelete = null },
            title = { Text("删除路线") },
            text = { Text("确定要删除路线「${route.name}」吗？该操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRoute(route)
                        routeToDelete = null
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { routeToDelete = null }) { Text("取消") }
            }
        )
    }

    if (!commitmentAccepted) {
        CommitmentDialog(
            onAccept = {
                prefs.edit().putBoolean(KEY_COMMITMENT_ACCEPTED, true).apply()
                commitmentAccepted = true
            },
            onDecline = { (context as? android.app.Activity)?.finish() }
        )
    }
}

/** 与电脑版主界面一致：常驻醒目使用警告。 */
@Composable
private fun DisclaimerNotice() {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(appColors.warning.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Outlined.Warning,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = appColors.warning
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            "本项目仅供学习、研究和技术测试，请勿用于伪造运动数据或规避体育锻炼要求。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BottomActionBar(
    running: Boolean,
    canStart: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPreview: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onPreview,
                enabled = !running,
                modifier = Modifier
                    .weight(0.9f)
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            ) {
                Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("预览")
            }
            if (running) {
                Button(
                    onClick = onStop,
                    modifier = Modifier
                        .weight(1.1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("停止任务", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onStart,
                    enabled = canStart,
                    modifier = Modifier
                        .weight(1.1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("开始任务", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ReloginBanner(onRelogin: () -> Unit) {
    SectionCard(
        elevated = true,
        icon = Icons.Outlined.Warning,
        title = "Cookie 已过期"
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "登录状态失效，请重新登录后继续上传。",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            FilledTonalButton(onClick = onRelogin) { Text("登录") }
        }
    }
}

@Composable
private fun SurpriseOverlay(onDismiss: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "gifScale"
    )
    LaunchedEffect(Unit) {
        delay(10_000)
        onDismiss()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data("file:///android_asset/surprise.gif")
                .decoderFactory { result, options, _ -> GifDecoder(result.source, options) }
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(300.dp)
                .scale(scale)
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Fit
        )
    }
}
