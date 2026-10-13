package com.sjtu.runner.viewmodel

import com.sjtu.runner.data.LogLevel
import com.sjtu.runner.data.TrackSample

data class LogEntry(
    val level: LogLevel,
    val time: String,
    val message: String
)

enum class TaskStatus(val label: String) {
    IDLE("空闲"),
    PREPARING("准备中"),
    AUTHENTICATING("获取认证"),
    GENERATING("生成数据"),
    UPLOADING("上传中"),
    STOPPING("正在停止"),
    FINISHED("已完成"),
    FAILED("失败")
}

data class TaskProgress(
    val current: Int = 0,
    val total: Int = 0,
    val status: TaskStatus = TaskStatus.IDLE
) {
    val fraction: Float
        get() = if (total <= 0) 0f else (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

enum class RouteDecision { TRUNCATE, FULL_ROUTE, CANCEL }

data class RouteConflict(
    val routeName: String,
    val routeDistanceM: Double,
    val targetDistanceM: Double
)

data class RoutePreview(
    val title: String,
    val status: String,
    val distanceKm: Double,
    val durationMin: Double,
    val pointCount: Int,
    val segmentCount: Int,
    val jumpCount: Int,
    val maxSegmentM: Double,
    val startEndDistanceM: Double,
    val samples: List<TrackSample>
) {
    val summary: String
        get() = String.format(
            "%.2fkm / %d点 / %d处跳段 / %s",
            distanceKm, pointCount, jumpCount, status
        )
}
