package com.sjtu.runner.data

import com.sjtu.runner.network.PointRules
import com.sjtu.runner.utils.GpsUtil
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random

typealias LngLat = Pair<Double, Double>

enum class LogLevel { INFO, WARN, ERROR, SUCCESS }

data class TrackSample(val longitude: Double, val latitude: Double, val timeMs: Long)

data class RunOutcome(
    val payload: List<Map<String, Any>>,
    val distanceM: Double,
    val durationSec: Int,
    val pointCount: Int,
    val segmentCount: Int,
    val maxSegmentM: Double,
    val jumpCount: Int,
    val startEndDistanceM: Double,
    val samples: List<TrackSample>,
    val paceMinPerKm: Double
)

object DataGenerator {

    private const val OFFSET_LON = -0.00651271494735 + 0.000094
    private const val OFFSET_LAT = -0.00560888976477 - 0.000700

    /** 与电脑版一致：目标距离额外补偿 200m，避免末段不足。 */
    private const val DISTANCE_COMPENSATION_M = 200.0

    /** 起终点直线距离超过该值时使用往返策略，否则使用环路策略。 */
    private const val ROUND_TRIP_THRESHOLD_M = 15.0

    /** 超过该长度的相邻轨迹点视为跳段，用于预览高亮。 */
    const val JUMP_THRESHOLD_M = 150.0

    /** 与电脑版一致：上传坐标保留 7 位小数。 */
    private const val TRACK_POINT_DECIMAL_PLACES = 7

    private const val MIN_SEGMENT_POINTS = 5

    fun correct(coordinates: List<LngLat>): List<LngLat> =
        coordinates.map { (lon, lat) -> (lon + OFFSET_LON) to (lat + OFFSET_LAT) }

    fun routeDistance(coordinates: List<LngLat>): Double =
        coordinates.zipWithNext { a, b ->
            GpsUtil.haversineDistance(a.second, a.first, b.second, b.first)
        }.sum()

    /** 校正后的单圈距离，用于上传前的路线长度提醒。 */
    fun correctedRouteDistance(coordinates: List<LngLat>): Double = routeDistance(correct(coordinates))

    fun generate(
        coordinates: List<LngLat>,
        userId: String,
        targetDistanceM: Double,
        startTimeMs: Long,
        intervalSeconds: Int = 3,
        rules: PointRules = PointRules(id = 9, spmin = 180, spmax = 540),
        truncateToTarget: Boolean = true,
        log: (LogLevel, String) -> Unit
    ): RunOutcome {
        if (coordinates.size < 2) {
            error("路线坐标点不足，至少需要 2 个点")
        }

        val corrected = correct(coordinates)
        log(LogLevel.INFO, "坐标已校正，共 ${corrected.size} 个点")

        val singleLoopDistance = routeDistance(corrected)
        log(LogLevel.INFO, "单圈距离: ${fmt(singleLoopDistance)}m")

        var targetDistanceM = targetDistanceM
        if (!truncateToTarget && singleLoopDistance > targetDistanceM) {
            targetDistanceM = singleLoopDistance
            log(LogLevel.INFO, "按完整路线跑步，目标距离调整为 ${fmt(targetDistanceM / 1000.0)} km")
        }

        // 配速随机化：4-6 分钟/公里
        val randomPaceMinPerKm = Random.nextDouble(4.0, 6.0)
        val totalDurationSec = (randomPaceMinPerKm * 60.0 * targetDistanceM / 1000.0).roundToInt()
            .coerceAtLeast(1)
        val targetSpeedMps = targetDistanceM / totalDurationSec
        log(
            LogLevel.INFO,
            "使用随机配速: ${fmt1(randomPaceMinPerKm)} 分钟/公里 (${(randomPaceMinPerKm * 60).toInt()} 秒/公里)"
        )
        log(LogLevel.INFO, "目标速度: ${fmt(targetSpeedMps)} m/s，目标距离: ${fmt(targetDistanceM)}m")

        val adjusted = adjustPath(corrected, targetSpeedMps, targetDistanceM, intervalSeconds, log)
        log(LogLevel.INFO, "调整后路径点数: ${adjusted.size}")

        val points = generateTrackPoints(adjusted, startTimeMs, targetSpeedMps)
        log(LogLevel.INFO, "生成轨迹点 ${points.size} 个")

        val actualDistance = routeDistance(points.map { it.first to it.second })
        val actualDurationSec = if (points.size >= 2) {
            max(1, ((points.last().third - points.first().third) / 1000.0).roundToInt())
        } else 1

        val tracks = splitIntoSegments(points, actualDurationSec, log)
        log(LogLevel.INFO, "拆分为 ${tracks.size} 个轨迹段")

        val maxSegmentM = points.zipWithNext { a, b ->
            GpsUtil.haversineDistance(a.second, a.first, b.second, b.first)
        }.maxOfOrNull { it } ?: 0.0
        val jumpCount = points.zipWithNext { a, b ->
            GpsUtil.haversineDistance(a.second, a.first, b.second, b.first)
        }.count { it > JUMP_THRESHOLD_M }

        val startEndDistanceM = if (points.size >= 2) {
            GpsUtil.haversineDistance(
                points.first().second, points.first().first,
                points.last().second, points.last().first
            )
        } else 0.0

        val spAvg = computeSpAvg(actualDistance, actualDurationSec, rules, log)

        val payload = listOf(
            mapOf(
                "fravg" to 0,
                "id" to rules.id,
                "sid" to UUID.randomUUID().toString(),
                "signpoints" to emptyList<Any>(),
                "spavg" to spAvg,
                "state" to "0",
                "tracks" to tracks,
                "userId" to userId
            )
        )

        val samples = points.map { TrackSample(it.first, it.second, it.third) }
        return RunOutcome(
            payload = payload,
            distanceM = actualDistance,
            durationSec = actualDurationSec,
            pointCount = points.size,
            segmentCount = tracks.size,
            maxSegmentM = maxSegmentM,
            jumpCount = jumpCount,
            startEndDistanceM = startEndDistanceM,
            samples = samples,
            paceMinPerKm = if (actualDistance > 0) actualDurationSec / (actualDistance / 1000.0) / 60.0 else 0.0
        )
    }

    /** 平均配速按平台规则 (spmin/spmax) 收敛，单位分钟/公里。 */
    private fun computeSpAvg(
        distanceM: Double,
        durationSec: Int,
        rules: PointRules,
        log: (LogLevel, String) -> Unit
    ): Int {
        if (distanceM <= 0 || durationSec <= 0) return 0
        var spAvgMinPerKm = (durationSec / (distanceM / 1000.0) / 60.0).roundToInt()
        val spAvgSecPerKm = spAvgMinPerKm * 60
        if (spAvgSecPerKm < rules.spmin) {
            log(
                LogLevel.WARN,
                "配速 ${spAvgMinPerKm} 分钟/公里快于下限 ${rules.spmin / 60} 分钟/公里，已调整至下限"
            )
            spAvgMinPerKm = ceil(rules.spmin / 60.0).toInt()
        } else if (spAvgSecPerKm > rules.spmax) {
            log(
                LogLevel.WARN,
                "配速 ${spAvgMinPerKm} 分钟/公里慢于上限 ${rules.spmax / 60} 分钟/公里，已调整至上限"
            )
            spAvgMinPerKm = floor(rules.spmax / 60.0).toInt()
        }
        return spAvgMinPerKm
    }

    /**
     * 按目标速度补点，并按目标距离裁剪/扩展路径。
     * 与电脑版 adjust_path_for_speed 一致：+200m 补偿、往返策略、环路策略。
     */
    private fun adjustPath(
        original: List<LngLat>,
        speedMps: Double,
        targetDistanceM: Double,
        intervalSec: Int,
        log: (LogLevel, String) -> Unit
    ): List<LngLat> {
        val interval = max(0.1, speedMps * max(1, intervalSec))
        val compensatedTarget = targetDistanceM + DISTANCE_COMPENSATION_M

        val detailed = mutableListOf(original.first())
        for (i in 0 until original.size - 1) {
            detailed.addAll(interpolateBetween(original[i], original[i + 1], interval))
            detailed.add(original[i + 1])
        }

        val singleLoop = routeDistance(detailed)
        val startEndDistance = GpsUtil.haversineDistance(
            detailed.first().second, detailed.first().first,
            detailed.last().second, detailed.last().first
        )

        if (singleLoop > compensatedTarget) {
            log(
                LogLevel.WARN,
                "单次路径长度 ${fmt(singleLoop)}m 超过目标 ${fmt(targetDistanceM)}m，自动削减至目标距离"
            )
            return truncateTo(detailed, compensatedTarget)
        }

        if (startEndDistance > ROUND_TRIP_THRESHOLD_M) {
            log(LogLevel.INFO, "采用往返策略: 起终点距离 ${fmt(startEndDistance)}m > ${ROUND_TRIP_THRESHOLD_M.toInt()}m")
            return buildRoundTrip(detailed, singleLoop, compensatedTarget)
        }

        log(LogLevel.INFO, "采用环路策略: 起终点距离 ${fmt(startEndDistance)}m <= ${ROUND_TRIP_THRESHOLD_M.toInt()}m")
        return buildLoops(detailed, singleLoop, compensatedTarget)
    }

    private fun interpolateBetween(a: LngLat, b: LngLat, interval: Double): List<LngLat> {
        val total = GpsUtil.haversineDistance(a.second, a.first, b.second, b.first)
        if (total <= 0.0 || interval <= 0.0) return emptyList()
        val count = (total / interval).toInt()
        if (count <= 0) return emptyList()
        return (1..count).map { i ->
            val f = i.toDouble() / (count + 1)
            (a.first + f * (b.first - a.first)) to (a.second + f * (b.second - a.second))
        }
    }

    /** 沿路径累积到 targetDistance 后截断，末段按比例插值。 */
    private fun truncateTo(path: List<LngLat>, targetDistance: Double): List<LngLat> {
        val result = mutableListOf(path.first())
        var acc = 0.0
        for (i in 0 until path.size - 1) {
            val seg = GpsUtil.haversineDistance(
                path[i].second, path[i].first, path[i + 1].second, path[i + 1].first
            )
            if (acc + seg > targetDistance) {
                val remaining = targetDistance - acc
                if (seg > 0) {
                    val f = remaining / seg
                    result.add(
                        (path[i].first + f * (path[i + 1].first - path[i].first)) to
                            (path[i].second + f * (path[i + 1].second - path[i].second))
                    )
                }
                acc = targetDistance
                break
            }
            if (result.last() != path[i]) result.add(path[i])
            acc += seg
        }
        return result
    }

    /** 沿路径累积到 targetDistance，用于余量部分。 */
    private fun partialForward(path: List<LngLat>, targetDistance: Double): List<LngLat> {
        val partial = mutableListOf<LngLat>()
        var acc = 0.0
        for (i in 0 until path.size - 1) {
            val seg = GpsUtil.haversineDistance(
                path[i].second, path[i].first, path[i + 1].second, path[i + 1].first
            )
            if (acc + seg > targetDistance) {
                val remaining = targetDistance - acc
                if (seg > 0) {
                    val f = remaining / seg
                    partial.add(
                        (path[i].first + f * (path[i + 1].first - path[i].first)) to
                            (path[i].second + f * (path[i + 1].second - path[i].second))
                    )
                }
                break
            }
            if (partial.isEmpty() || partial.last() != path[i]) partial.add(path[i])
            acc += seg
        }
        return partial
    }

    private fun appendAvoidingDuplicate(target: MutableList<LngLat>, source: List<LngLat>) {
        if (source.isEmpty()) return
        val skip = if (target.isNotEmpty() && target.last() == source.first()) 1 else 0
        target.addAll(source.drop(skip))
    }

    private fun buildRoundTrip(
        detailed: List<LngLat>,
        singleLoop: Double,
        compensatedTarget: Double
    ): List<LngLat> {
        val roundTrip = singleLoop * 2
        if (roundTrip <= 0.0) {
            return truncateTo(repeatForward(detailed, compensatedTarget), compensatedTarget)
        }

        val result = mutableListOf<LngLat>()
        val completeTrips = (compensatedTarget / roundTrip).toInt()
        val remaining = compensatedTarget - completeTrips * roundTrip

        repeat(completeTrips) {
            result.addAll(detailed)
            appendAvoidingDuplicate(result, detailed.asReversed())
        }

        if (remaining > 0) {
            val ratio = remaining / roundTrip
            if (ratio <= 0.5) {
                appendAvoidingDuplicate(result, partialForward(detailed, remaining))
            } else {
                appendAvoidingDuplicate(result, detailed)
                val backwardRemaining = remaining - singleLoop
                if (backwardRemaining > 0) {
                    appendAvoidingDuplicate(result, partialForward(detailed.asReversed(), backwardRemaining))
                }
            }
        }
        return result
    }

    private fun buildLoops(
        detailed: List<LngLat>,
        singleLoop: Double,
        compensatedTarget: Double
    ): List<LngLat> {
        if (singleLoop <= 0.0) return truncateTo(repeatForward(detailed, compensatedTarget), compensatedTarget)

        val result = mutableListOf<LngLat>()
        val completeLoops = (compensatedTarget / singleLoop).toInt()
        val remaining = compensatedTarget - completeLoops * singleLoop

        repeat(completeLoops) { result.addAll(detailed) }
        if (remaining > 0) appendAvoidingDuplicate(result, partialForward(detailed, remaining))
        return result
    }

    private fun repeatForward(path: List<LngLat>, targetDistance: Double): List<LngLat> {
        val result = mutableListOf<LngLat>()
        val loopDistance = routeDistance(path)
        if (loopDistance <= 0.0) {
            result.addAll(path)
            return result
        }
        val loops = max(1, ceil(targetDistance / loopDistance).toInt())
        repeat(loops) { result.addAll(path) }
        return result
    }

    private fun generateTrackPoints(
        coords: List<LngLat>,
        startTimeMs: Long,
        speedMps: Double
    ): List<Triple<Double, Double, Long>> {
        val points = mutableListOf<Triple<Double, Double, Long>>()
        var cumDist = 0.0
        coords.forEachIndexed { i, (lon, lat) ->
            if (i > 0) {
                val prev = coords[i - 1]
                cumDist += GpsUtil.haversineDistance(prev.second, prev.first, lat, lon)
            }
            val elapsed = if (speedMps > 0) cumDist / speedMps else 0.0
            points.add(Triple(lon, lat, startTimeMs + (elapsed * 1000).toLong()))
        }
        return points
    }

    private fun splitIntoSegments(
        allPoints: List<Triple<Double, Double, Long>>,
        totalDurationSec: Int,
        log: (LogLevel, String) -> Unit
    ): List<Map<String, Any>> {
        val tracks = mutableListOf<Map<String, Any>>()
        var idx = 0
        while (idx < allPoints.size) {
            val remainingPoints = allPoints.size - idx
            val segmentLength = if (remainingPoints <= MIN_SEGMENT_POINTS) {
                remainingPoints
            } else {
                max(MIN_SEGMENT_POINTS, Random.nextInt(MIN_SEGMENT_POINTS, max(MIN_SEGMENT_POINTS + 1, remainingPoints / 3 + 1)))
            }
            val segment = allPoints.subList(idx, min(idx + segmentLength, allPoints.size))
            idx += segment.size
            if (segment.isEmpty()) continue

            val rand = Random.nextDouble()
            val status = when {
                rand < 0.8 -> "normal"
                rand < 0.9 -> "invalid"
                else -> "stop"
            }
            val tstate = if (status == "invalid") "2" else "0"

            val segmentDistance = segment.zipWithNext { a, b ->
                GpsUtil.haversineDistance(a.second, a.first, b.second, b.first)
            }.sum()

            val startTime = segment.first().third
            val endTime = segment.last().third
            val duration = ceil((endTime - startTime) / 1000.0).toInt()

            tracks.add(
                mapOf(
                    "counts" to segment.size,
                    "distance" to segmentDistance,
                    "duration" to duration,
                    "points" to segment.map {
                        mapOf(
                            "latLng" to mapOf(
                                "latitude" to roundTrack(it.second),
                                "longitude" to roundTrack(it.first)
                            ),
                            "location" to "${fmtTrack(it.first)},${fmtTrack(it.second)}",
                            "step" to 0,
                            "locatetime" to it.third
                        )
                    },
                    "status" to status,
                    "trid" to UUID.randomUUID().toString(),
                    "tstate" to tstate,
                    "stime" to (startTime / 1000).toInt(),
                    "etime" to (endTime / 1000).toInt()
                )
            )
        }
        if (totalDurationSec <= 0) log(LogLevel.WARN, "轨迹总时长异常")
        return tracks
    }

    private fun fmt(value: Double) = String.format("%.2f", value)
    private fun fmt1(value: Double) = String.format("%.1f", value)
    private fun fmtTrack(value: Double) = String.format("%.${TRACK_POINT_DECIMAL_PLACES}f", value)

    private fun roundTrack(value: Double): Double =
        (value * 10.0.pow(TRACK_POINT_DECIMAL_PLACES)).roundToLong() / 10.0.pow(TRACK_POINT_DECIMAL_PLACES)
}
