package com.sjtu.runner.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sjtu.runner.config.AppConfig
import com.sjtu.runner.data.DataGenerator
import com.sjtu.runner.data.LngLat
import com.sjtu.runner.data.LogLevel
import com.sjtu.runner.data.RunOutcome
import com.sjtu.runner.network.ApiService
import com.sjtu.runner.network.PointRules
import com.sjtu.runner.route.RouteInfo
import com.sjtu.runner.route.RouteManager
import com.sjtu.runner.stats.AutoPlan
import com.sjtu.runner.stats.CalendarWeek
import com.sjtu.runner.stats.SemesterStats
import com.sjtu.runner.stats.autoPlan
import com.sjtu.runner.stats.buildSchedule
import com.sjtu.runner.stats.calendarWeeks
import com.sjtu.runner.stats.formatKm
import com.sjtu.runner.stats.formatPercent
import com.sjtu.runner.stats.formatPlanKm
import com.sjtu.runner.stats.formatSkippedDays
import com.sjtu.runner.stats.placeholderWeeks
import com.sjtu.runner.stats.todayCst
import com.sjtu.runner.utils.GpsUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random

private const val PREFS = "app_prefs"
private const val KEY_SKIP_EXISTING = "skip_existing"
private const val KEY_PLAN_AUTO_ACTIVE = "plan_auto_active"
private const val KEY_PLAN_AUTO_DAYS = "plan_auto_days"
private const val KEY_PLAN_AUTO_DISTANCE = "plan_auto_distance"

data class RunConfig(
    val days: Int = 1,
    val distanceKm: Double = AppConfig.DEFAULT_DISTANCE_KM,
    val hour: Int = AppConfig.DEFAULT_RUN_HOUR,
    val minute: Int = AppConfig.DEFAULT_RUN_MINUTE,
    val second: Int = AppConfig.DEFAULT_RUN_SECOND,
    val startDate: LocalDate = LocalDate.now().minusDays(1),
    val routeDecision: RouteDecision? = null
)

/** 顶部浮层提示：`id` 变化才重播动画，5 秒后由界面主动 `clearNotice`。 */
data class Notice(
    val id: Long,
    val text: String
)

/** 「本学期进度」与「学期日历」的展示数据，未登录时保留占位日历。 */
data class SemesterView(
    val loggedIn: Boolean = false,
    val kmText: String = "--",
    val percentText: String = "--",
    val weeks: List<CalendarWeek> = emptyList()
)

/** 自动排计划的一次生效记录，`token` 保证同样的数值也能被界面重新应用。 */
data class AutoPlanApplied(
    val runs: Int,
    val distanceKm: Double,
    val token: Long
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val api = ApiService(application)
    private val routeManager = RouteManager(application)

    var onReloginRequired: (() -> Unit)? = null

    private val _log = MutableStateFlow<List<LogEntry>>(emptyList())
    val log: StateFlow<List<LogEntry>> = _log

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _progress = MutableStateFlow(TaskProgress())
    val progress: StateFlow<TaskProgress> = _progress

    private val _routes = MutableStateFlow<List<RouteInfo>>(emptyList())
    val routes: StateFlow<List<RouteInfo>> = _routes

    private val _selectedRoute = MutableStateFlow<RouteInfo?>(null)
    val selectedRoute: StateFlow<RouteInfo?> = _selectedRoute

    private val _preview = MutableStateFlow<RoutePreview?>(null)
    val preview: StateFlow<RoutePreview?> = _preview

    private val _routeConflict = MutableStateFlow<RouteConflict?>(null)
    val routeConflict: StateFlow<RouteConflict?> = _routeConflict

    private val _reloginRequired = MutableStateFlow(false)
    val reloginRequired: StateFlow<Boolean> = _reloginRequired

    private val _loggedIn = MutableStateFlow(false)
    val loggedIn: StateFlow<Boolean> = _loggedIn

    private val _semester = MutableStateFlow(SemesterView(weeks = placeholderWeeks(todayCst())))
    val semester: StateFlow<SemesterView> = _semester

    private val _notice = MutableStateFlow<Notice?>(null)
    val notice: StateFlow<Notice?> = _notice

    private val _skipExisting = MutableStateFlow(true)
    val skipExisting: StateFlow<Boolean> = _skipExisting

    private val _autoPlanApplied = MutableStateFlow<AutoPlanApplied?>(null)
    val autoPlanApplied: StateFlow<AutoPlanApplied?> = _autoPlanApplied

    private var job: Job? = null
    private var previewJob: Job? = null

    /** 最近一次成功的学期数据，用于「跳过已有记录的日期」和自动排计划。 */
    private var stats: SemesterStats? = null

    /** 换账号 / 重新登录后递增，迟到的统计响应据此丢弃。 */
    private var sessionSerial = 0L
    private var autoAdjustPending = false
    private var noticeSerial = 0L
    private var autoPlanToken = 0L
    private var lastAppliedPlan: AutoPlan? = null

    init {
        loadRoutes()
        restorePrefs()
        if (CookieManager.getInstance().getCookie("https://jaccount.sjtu.edu.cn")?.contains("JAAuthCookie") == true) {
            _loggedIn.value = true
            refreshSemesterStats()
        }
    }

    // ------------------------------------------------------------ semester stats

    /** 登录页返回成功后调用：换会话、拉学期数据，并允许按缺口调整计划。 */
    fun onLoginSucceeded() {
        sessionSerial += 1
        autoAdjustPending = true
        _reloginRequired.value = false
        _loggedIn.value = true
        refreshSemesterStats()
    }

    fun refreshSemesterStats() {
        val serial = sessionSerial
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { api.semesterStats() } }
            if (serial != sessionSerial) return@launch
            result.onSuccess { data -> applyStats(data) }
                .onFailure { e ->
                    autoAdjustPending = false
                    logMsg(LogLevel.ERROR, "学期数据同步失败：${e.message ?: e.javaClass.simpleName}")
                }
        }
    }

    private fun applyStats(data: SemesterStats) {
        stats = data
        val weeks = data.range?.let { calendarWeeks(it, data.runDays) }
        _semester.value = SemesterView(
            loggedIn = true,
            kmText = formatKm(data.resultKm),
            percentText = formatPercent(data.percent),
            weeks = weeks ?: placeholderWeeks(todayCst())
        )
        if (weeks != null) {
            logMsg(LogLevel.INFO, "学期数据已同步 · ${data.runDays.size} 天有记录")
        } else {
            logMsg(LogLevel.INFO, "服务器未返回本学期起止日期，日历保持占位")
        }
        if (autoAdjustPending) {
            autoAdjustPending = false
            autoAdjustPlan(data)
        }
    }

    /** 登录后的第一次学期数据用来按本学期缺口反推「次数 × 单次距离」（与电脑版一致）。 */
    private fun autoAdjustPlan(data: SemesterStats) {
        val target = data.targetKm?.takeIf { it.isFinite() }
        val done = data.resultKm?.takeIf { it.isFinite() }
        if (target == null || done == null) {
            logMsg(LogLevel.INFO, "服务器未返回本学期目标或已完成公里数，跑步计划保持不变")
            return
        }
        val remaining = target - done
        val plan = autoPlan(remaining)
        if (plan == null) {
            logMsg(LogLevel.INFO, "本学期没有待补的公里数，跑步计划保持不变")
            return
        }
        val defaults = RunConfig()
        val untouched = currentConfig.days == defaults.days && currentConfig.distanceKm == defaults.distanceKm
        val lastPlan = lastAppliedPlan
        if (!untouched && lastPlan != AutoPlan(currentConfig.days, currentConfig.distanceKm)) {
            logMsg(LogLevel.INFO, "跑步次数 / 单次距离已被手动修改，保持你的设置")
            return
        }
        lastAppliedPlan = plan
        prefs().edit()
            .putInt(KEY_PLAN_AUTO_DAYS, plan.runs)
            .putString(KEY_PLAN_AUTO_DISTANCE, formatPlanKm(plan.distanceKm))
            .putBoolean(KEY_PLAN_AUTO_ACTIVE, true)
            .apply()
        // 次数和距离都变了，上一条预览不再对应当前参数。
        _preview.value = null
        autoPlanToken += 1
        _autoPlanApplied.value = AutoPlanApplied(plan.runs, plan.distanceKm, autoPlanToken)
        notify(
            "已按本学期未完成 ${formatKm(remaining)} 自动设为 ${plan.runs} 次 × ${formatPlanKm(plan.distanceKm)} km"
        )
    }

    // ------------------------------------------------------------- persisted prefs

    private fun prefs(): SharedPreferences =
        getApplication<Application>().getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun restorePrefs() {
        val store = prefs()
        _skipExisting.value = store.getBoolean(KEY_SKIP_EXISTING, true)
        if (!store.getBoolean(KEY_PLAN_AUTO_ACTIVE, false)) return
        val runs = store.getInt(KEY_PLAN_AUTO_DAYS, 0)
        val distance = store.getString(KEY_PLAN_AUTO_DISTANCE, null)?.toDoubleOrNull()
        if (runs in 1..AppConfig.MAX_RUN_TIMES && distance != null && distance.isFinite()) {
            lastAppliedPlan = AutoPlan(runs, distance)
            autoPlanToken += 1
            _autoPlanApplied.value = AutoPlanApplied(runs, distance, autoPlanToken)
        }
    }

    fun setSkipExisting(value: Boolean) {
        _skipExisting.value = value
        prefs().edit().putBoolean(KEY_SKIP_EXISTING, value).apply()
    }

    // ------------------------------------------------------------------ notice

    /** 浮层提示 + 日志各一份，和电脑版 `notify` 一样不会只留一处。 */
    private fun notify(message: String) {
        noticeSerial += 1
        _notice.value = Notice(noticeSerial, message)
        logMsg(LogLevel.INFO, message)
    }

    fun clearNotice() {
        _notice.value = null
    }

    // ---------------------------------------------------------------- routes

    fun loadRoutes() {
        viewModelScope.launch {
            val allRoutes = routeManager.getAllRoutes()
            _routes.value = allRoutes
            if (_selectedRoute.value == null && allRoutes.isNotEmpty()) {
                _selectedRoute.value = allRoutes.firstOrNull { it.isDefault } ?: allRoutes.first()
            }
            refreshPreviewForSelectedRoute()
        }
    }

    fun selectRoute(route: RouteInfo) {
        _selectedRoute.value = route
        logMsg(LogLevel.INFO, "已选择路线: ${route.name}")
        refreshPreviewForSelectedRoute()
    }

    fun deleteRoute(route: RouteInfo) {
        if (route.isDefault) {
            logMsg(LogLevel.WARN, "默认路线不能删除")
            return
        }
        viewModelScope.launch {
            val success = routeManager.deleteRoute(route.id)
            if (success) {
                logMsg(LogLevel.SUCCESS, "已删除路线: ${route.name}")
                loadRoutes()
                if (_selectedRoute.value?.id == route.id) {
                    _selectedRoute.value = _routes.value.firstOrNull { it.isDefault }
                }
            } else {
                logMsg(LogLevel.ERROR, "删除路线失败")
            }
        }
    }

    fun refreshRoutes() {
        loadRoutes()
    }

    // ------------------------------------------------------------------ logs

    fun clearLogs() {
        _log.value = emptyList()
    }

    private fun logMsg(level: LogLevel, msg: String) {
        viewModelScope.launch(Dispatchers.Main) {
            val entry = LogEntry(level, LocalTime.now().withNano(0).format(DateTimeFormatter.ofPattern("HH:mm:ss")), msg)
            val current = _log.value
            _log.value = if (current.size >= MAX_LOG_ENTRIES) current.drop(current.size - MAX_LOG_ENTRIES + 1) + entry
            else current + entry
        }
    }

    // --------------------------------------------------------------- validation

    fun validate(config: RunConfig): String? = when {
        config.days !in 1..AppConfig.MAX_RUN_TIMES -> "跑步次数应在 1-${AppConfig.MAX_RUN_TIMES} 之间"
        config.distanceKm <= 0.0 || config.distanceKm > AppConfig.MAX_DISTANCE_KM ->
            "距离应大于 0 且不超过 ${AppConfig.MAX_DISTANCE_KM.toInt()}km"
        config.hour !in 0..23 || config.minute !in 0..59 || config.second !in 0..59 ->
            "时间范围无效，小时 00-23，分钟和秒 00-59"
        _selectedRoute.value == null -> "未选择任何路线，请先选择路线"
        else -> null
    }

    // ----------------------------------------------------------------- upload

    fun startUpload(config: RunConfig) {
        if (_running.value) return

        validate(config)?.let { message ->
            notify(message)
            return
        }

        val route = _selectedRoute.value ?: return
        val targetDistanceM = config.distanceKm * 1000.0

        // 上传前检查路线长度，超过目标距离时先征询用户（与电脑版一致）
        if (config.routeDecision == null) {
            viewModelScope.launch(Dispatchers.IO) {
                val coordinates = currentRouteCoordinates()
                if (coordinates.size < 2) {
                    logMsg(LogLevel.ERROR, "路线坐标为空，请选择其他路线")
                    return@launch
                }
                val routeDistanceM = DataGenerator.correctedRouteDistance(coordinates)
                if (routeDistanceM > targetDistanceM) {
                    withContext(Dispatchers.Main) {
                        _routeConflict.value = RouteConflict(route.name, routeDistanceM, targetDistanceM)
                    }
                } else {
                    runUpload(config.copy(routeDecision = RouteDecision.TRUNCATE))
                }
            }
            return
        }

        if (config.routeDecision == RouteDecision.CANCEL) {
            _routeConflict.value = null
            logMsg(LogLevel.WARN, "已取消本次任务")
            return
        }

        runUpload(config)
    }

    fun resolveRouteConflict(decision: RouteDecision) {
        val conflict = _routeConflict.value ?: return
        _routeConflict.value = null
        when (decision) {
            RouteDecision.TRUNCATE -> logMsg(LogLevel.INFO, "用户选择自动削减路线至目标距离")
            RouteDecision.FULL_ROUTE -> logMsg(LogLevel.INFO, "用户选择按照完整路线进行跑步")
            RouteDecision.CANCEL -> logMsg(LogLevel.WARN, "用户选择停止任务")
        }
        startUpload(
            currentConfig.copy(
                routeDecision = decision,
                distanceKm = if (decision == RouteDecision.FULL_ROUTE) {
                    (conflict.routeDistanceM / 1000.0)
                } else {
                    currentConfig.distanceKm
                }
            )
        )
    }

    private fun runUpload(config: RunConfig) {
        // 上传前先决定这批记录落在哪些日期：已有记录的那几天挪到更早的日期。
        val schedule = buildSchedule(
            endDate = config.startDate,
            runs = config.days,
            skipExisting = _skipExisting.value,
            occupied = stats?.runDays?.keys?.toSet() ?: emptySet(),
            earliest = stats?.range?.start
        )
        if (schedule.dates.isEmpty()) {
            notify("设定次数内的日期都已有记录，可关闭「跳过已有记录的日期」或改用更早的结束日期")
            return
        }
        val total = schedule.dates.size

        _reloginRequired.value = false
        job = viewModelScope.launch(Dispatchers.IO) {
            _running.value = true
            _notice.value = null
            _progress.value = TaskProgress(0, total, TaskStatus.PREPARING)

            try {
                val route = _selectedRoute.value ?: error("未选择路线")
                logMsg(LogLevel.INFO, "使用路线: ${route.name}")

                formatSkippedDays(schedule.skipped).takeIf { it.isNotEmpty() }?.let {
                    logMsg(LogLevel.INFO, it)
                }
                if (schedule.truncated) {
                    logMsg(
                        LogLevel.INFO,
                        "向前查找已到头，只找到 $total 个可上传的日期（设定 ${config.days} 次）"
                    )
                }

                logMsg(LogLevel.INFO, "步骤 1/2: 获取认证...")
                val coordinates = currentRouteCoordinates()
                if (coordinates.size < 2) {
                    logMsg(LogLevel.ERROR, "路线坐标为空，请选择其他路线")
                    _progress.value = TaskProgress(0, total, TaskStatus.FAILED)
                    return@launch
                }
                logMsg(LogLevel.INFO, "路线点数量: ${coordinates.size}")

                val uid = api.getUid()
                if (uid == null) {
                    logMsg(LogLevel.ERROR, "UID 获取失败，cookie 可能已过期")
                    _progress.value = TaskProgress(0, total, TaskStatus.FAILED)
                    triggerRelogin()
                    return@launch
                }
                logMsg(LogLevel.SUCCESS, "UID 获取成功")

                _progress.value = TaskProgress(0, total, TaskStatus.AUTHENTICATING)
                val rules = api.getPointRules(uid, AppConfig.START_LONGITUDE, AppConfig.START_LATITUDE)
                logMsg(
                    LogLevel.INFO,
                    "打卡规则: id=${rules.id} 配速 ${rules.spmin / 60}-${rules.spmax / 60} 分钟/公里"
                )

                logMsg(LogLevel.INFO, "步骤 2/2: 生成并上传跑步数据...")

                val targetDistanceM = config.distanceKm * 1000.0
                var successCount = 0
                var failCount = 0

                for ((i, date) in schedule.dates.withIndex()) {
                    if (!_running.value) break

                    val startDateTime = baseStartDateTime(config, date)
                    logMsg(
                        LogLevel.INFO,
                        "生成第 ${i + 1}/$total 条数据 (${startDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))})"
                    )

                    _progress.value = TaskProgress(i, total, TaskStatus.GENERATING)
                    val outcome = DataGenerator.generate(
                        coordinates = coordinates,
                        userId = uid,
                        targetDistanceM = targetDistanceM,
                        startTimeMs = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        intervalSeconds = AppConfig.INTERVAL_SECONDS,
                        rules = rules,
                        truncateToTarget = config.routeDecision != RouteDecision.FULL_ROUTE,
                        log = { level, msg -> logMsg(level, msg) }
                    )
                    publishPreview(outcome, route.name, "待上传", i + 1, total)
                    logMsg(
                        LogLevel.INFO,
                        "实际距离 ${String.format("%.2f", outcome.distanceM / 1000.0)}km / ${outcome.durationSec / 60}min / ${outcome.pointCount}点"
                    )

                    _progress.value = TaskProgress(i, total, TaskStatus.UPLOADING)
                    val result = api.upload(uid, outcome.payload)
                    if (result.success) {
                        successCount++
                        logMsg(LogLevel.SUCCESS, "第 ${i + 1}/$total 条上传成功")
                        publishPreview(outcome, route.name, "已上传", i + 1, total)
                    } else if (result.needRelogin) {
                        logMsg(LogLevel.ERROR, "上传失败: ${result.message}")
                        _progress.value = TaskProgress(i + 1, total, TaskStatus.FAILED)
                        triggerRelogin()
                        break
                    } else {
                        failCount++
                        logMsg(LogLevel.ERROR, "第 ${i + 1} 条失败: ${result.message}")
                        publishPreview(outcome, route.name, "上传失败", i + 1, total)
                    }

                    _progress.value = TaskProgress(i + 1, total, TaskStatus.UPLOADING)
                }

                if (_running.value) {
                    val finished = failCount == 0
                    _progress.value = TaskProgress(
                        successCount + failCount,
                        total,
                        if (finished) TaskStatus.FINISHED else TaskStatus.FAILED
                    )
                    logMsg(
                        if (finished) LogLevel.SUCCESS else LogLevel.WARN,
                        "任务完成: 成功 $successCount 条，失败 $failCount 条"
                    )
                } else {
                    logMsg(LogLevel.WARN, "任务已手动停止")
                }

                // 上传成功之后当天才算得上数，顺手刷新一次日历（不再触发自动排计划）。
                if (successCount > 0) {
                    sessionSerial += 1
                    refreshSemesterStats()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                logMsg(LogLevel.WARN, "任务已手动停止")
            } catch (e: Exception) {
                logMsg(LogLevel.ERROR, "错误: ${e.message ?: e.javaClass.simpleName}")
                _progress.value = _progress.value.copy(status = TaskStatus.FAILED)
            } finally {
                _running.value = false
            }
        }
    }

    fun stopUpload() {
        if (!_running.value) return
        _progress.value = _progress.value.copy(status = TaskStatus.STOPPING)
        logMsg(LogLevel.WARN, "正在停止任务...")
        job?.cancel()
        _running.value = false
        _progress.value = _progress.value.copy(status = TaskStatus.IDLE)
    }

    /** 记录当前 UI 配置，供路线冲突确认后复用。 */
    var currentConfig: RunConfig = RunConfig()
        private set

    fun updateConfig(config: RunConfig) {
        currentConfig = config
    }

    // --------------------------------------------------------------- preview

    /** 离线预览：不登录，按当前设置本地跑一遍生成算法。 */
    fun previewRoute() {
        if (previewJob?.isActive == true) return
        previewJob = viewModelScope.launch(Dispatchers.IO) {
            val route = _selectedRoute.value
            if (route == null) {
                logMsg(LogLevel.ERROR, "未选择路线，无法预览")
                return@launch
            }
            val error = validate(currentConfig)
            if (error != null) {
                logMsg(LogLevel.ERROR, "配置错误: $error")
                return@launch
            }
            logMsg(LogLevel.INFO, "正在离线预览路线…")
            try {
                val coordinates = currentRouteCoordinates()
                if (coordinates.size < 2) {
                    logMsg(LogLevel.ERROR, "路线坐标为空，无法预览")
                    return@launch
                }
                val outcome = DataGenerator.generate(
                    coordinates = coordinates,
                    userId = "preview",
                    targetDistanceM = currentConfig.distanceKm * 1000.0,
                    startTimeMs = System.currentTimeMillis(),
                    intervalSeconds = AppConfig.INTERVAL_SECONDS,
                    truncateToTarget = true,
                    log = { level, msg -> logMsg(level, msg) }
                )
                publishPreview(outcome, route.name, "离线预览", 1, 1)
                logMsg(
                    LogLevel.SUCCESS,
                    "预览生成完成: " + String.format(
                        "%.2fkm / %d点 / %d处跳段",
                        outcome.distanceM / 1000.0, outcome.pointCount, outcome.jumpCount
                    )
                )
            } catch (e: Exception) {
                logMsg(LogLevel.ERROR, "预览失败: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun refreshPreviewForSelectedRoute() {
        val route = _selectedRoute.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val coordinates = currentRouteCoordinates()
            if (coordinates.size < 2) return@launch
            val corrected = DataGenerator.correct(coordinates)
            val distanceM = DataGenerator.routeDistance(corrected)
            _preview.value = RoutePreview(
                title = route.name,
                status = "原始路线",
                distanceKm = distanceM / 1000.0,
                durationMin = 0.0,
                pointCount = corrected.size,
                segmentCount = 0,
                jumpCount = 0,
                maxSegmentM = corrected.zipWithNext { a, b ->
                    GpsUtil.haversineDistance(a.second, a.first, b.second, b.first)
                }.maxOfOrNull { it } ?: 0.0,
                startEndDistanceM = if (corrected.size >= 2) {
                    GpsUtil.haversineDistance(
                        corrected.first().second, corrected.first().first,
                        corrected.last().second, corrected.last().first
                    )
                } else 0.0,
                samples = corrected.map { com.sjtu.runner.data.TrackSample(it.first, it.second, 0L) }
            )
        }
    }

    private fun publishPreview(
        outcome: RunOutcome,
        routeName: String,
        status: String,
        index: Int,
        total: Int
    ) {
        _preview.value = RoutePreview(
            title = if (total > 1) "$routeName · 第 $index/$total 条" else routeName,
            status = status,
            distanceKm = outcome.distanceM / 1000.0,
            durationMin = outcome.durationSec / 60.0,
            pointCount = outcome.pointCount,
            segmentCount = outcome.segmentCount,
            jumpCount = outcome.jumpCount,
            maxSegmentM = outcome.maxSegmentM,
            startEndDistanceM = outcome.startEndDistanceM,
            samples = outcome.samples
        )
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun currentRouteCoordinates(): List<LngLat> {
        val route = _selectedRoute.value ?: return emptyList()
        return if (route.isDefault) {
            GpsUtil.readCoordinates(getApplication())
        } else {
            val file = File(route.filePath)
            if (file.exists()) {
                GpsUtil.readCoordinatesFromFile(file)
            } else {
                logMsg(LogLevel.WARN, "路线文件不存在，使用默认路线")
                GpsUtil.readCoordinates(getApplication())
            }
        }
    }

    /** 记录落在排好的日期上，并加入 ±10 分钟 / ±30 秒随机波动。 */
    private fun baseStartDateTime(config: RunConfig, date: LocalDate): LocalDateTime {
        val base = date
            .atTime(config.hour, config.minute, config.second)
            .plusMinutes(Random.nextLong(-10, 11))
            .plusSeconds(Random.nextLong(-30, 31))
        return base
    }

    private fun triggerRelogin() {
        // 会话已经作废：日历回到占位，迟到的统计响应也会被 sessionSerial 挡掉。
        sessionSerial += 1
        stats = null
        autoAdjustPending = false
        _loggedIn.value = false
        _semester.value = SemesterView(weeks = placeholderWeeks(todayCst()))
        _reloginRequired.value = true
        viewModelScope.launch(Dispatchers.Main) {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            onReloginRequired?.invoke()
        }
    }

    private companion object {
        const val MAX_LOG_ENTRIES = 400
    }
}
