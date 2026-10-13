//
//  DataGenerator.swift
//  SJTURunningMan
//
//  轨迹数据生成算法，行为与电脑版 / 安卓版保持一致：
//  坐标校正 → 目标速度补点 → +200m 补偿 → 往返/环路策略 → 截断 → 分段。
//

import Foundation

enum DataGenerator {

    enum GeneratorError: LocalizedError {
        case insufficientPoints

        var errorDescription: String? { "路线坐标点不足，至少需要 2 个点" }
    }

    // MARK: - 常量

    static let offsetLon = -0.00651271494735 + 0.000094
    static let offsetLat = -0.00560888976477 - 0.000700

    /// 与电脑版一致：目标距离额外补偿 200m，避免末段不足。
    private static let distanceCompensationM = 200.0

    /// 起终点直线距离超过该值时使用往返策略，否则使用环路策略。
    private static let roundTripThresholdM = 15.0

    /// 超过该长度的相邻轨迹点视为跳段，用于预览高亮。
    static let jumpThresholdM = 150.0

    /// 与电脑版一致：上传坐标保留 7 位小数。
    private static let trackPointDecimalPlaces = 7

    private static let minSegmentPoints = 5

    // MARK: - 坐标工具

    static func correct(_ coordinates: [Coord]) -> [Coord] {
        coordinates.map { Coord(lon: $0.lon + offsetLon, lat: $0.lat + offsetLat) }
    }

    static func routeDistance(_ coordinates: [Coord]) -> Double {
        guard coordinates.count >= 2 else { return 0 }
        var total = 0.0
        for i in 0..<(coordinates.count - 1) {
            total += GpsUtil.haversineDistance(
                lat1: coordinates[i].lat, lon1: coordinates[i].lon,
                lat2: coordinates[i + 1].lat, lon2: coordinates[i + 1].lon
            )
        }
        return total
    }

    /// 校正后的单圈距离，用于上传前的路线长度提醒。
    static func correctedRouteDistance(_ coordinates: [Coord]) -> Double {
        routeDistance(correct(coordinates))
    }

    // MARK: - 生成

    static func generate(
        coordinates: [Coord],
        userId: String,
        targetDistanceM: Double,
        startTimeMs: Int64,
        intervalSeconds: Int = 3,
        rules: PointRules = .default,
        truncateToTarget: Bool = true,
        log: (LogLevel, String) -> Void
    ) throws -> RunOutcome {
        guard coordinates.count >= 2 else { throw GeneratorError.insufficientPoints }

        let corrected = correct(coordinates)
        log(.info, "坐标已校正，共 \(corrected.count) 个点")

        let singleLoopDistance = routeDistance(corrected)
        log(.info, "单圈距离: \(fmt(singleLoopDistance))m")

        var targetDistanceM = targetDistanceM
        if !truncateToTarget && singleLoopDistance > targetDistanceM {
            targetDistanceM = singleLoopDistance
            log(.info, "按完整路线跑步，目标距离调整为 \(fmt(targetDistanceM / 1000.0)) km")
        }

        // 配速随机化：4-6 分钟/公里
        let randomPaceMinPerKm = Double.random(in: 4.0..<6.0)
        let totalDurationSec = max(1, Int((randomPaceMinPerKm * 60.0 * targetDistanceM / 1000.0).rounded()))
        let targetSpeedMps = targetDistanceM / Double(totalDurationSec)
        log(.info, "使用随机配速: \(fmt1(randomPaceMinPerKm)) 分钟/公里 (\(Int(randomPaceMinPerKm * 60)) 秒/公里)")
        log(.info, "目标速度: \(fmt(targetSpeedMps)) m/s，目标距离: \(fmt(targetDistanceM))m")

        let adjusted = adjustPath(corrected, speedMps: targetSpeedMps, targetDistanceM: targetDistanceM, intervalSec: intervalSeconds, log: log)
        log(.info, "调整后路径点数: \(adjusted.count)")

        let points = generateTrackPoints(adjusted, startTimeMs: startTimeMs, speedMps: targetSpeedMps)
        log(.info, "生成轨迹点 \(points.count) 个")

        let actualDistance = routeDistance(points.map { Coord(lon: $0.lon, lat: $0.lat) })
        let actualDurationSec = points.count >= 2
            ? max(1, Int(((Double(points.last!.timeMs - points.first!.timeMs)) / 1000.0).rounded()))
            : 1

        let tracks = splitIntoSegments(points, totalDurationSec: actualDurationSec, log: log)
        log(.info, "拆分为 \(tracks.count) 个轨迹段")

        var maxSegmentM = 0.0
        var jumpCount = 0
        for i in 0..<max(0, points.count - 1) {
            let d = GpsUtil.haversineDistance(
                lat1: points[i].lat, lon1: points[i].lon,
                lat2: points[i + 1].lat, lon2: points[i + 1].lon
            )
            maxSegmentM = max(maxSegmentM, d)
            if d > jumpThresholdM { jumpCount += 1 }
        }

        let startEndDistanceM = points.count >= 2
            ? GpsUtil.haversineDistance(lat1: points.first!.lat, lon1: points.first!.lon,
                                        lat2: points.last!.lat, lon2: points.last!.lon)
            : 0.0

        let spAvg = computeSpAvg(distanceM: actualDistance, durationSec: actualDurationSec, rules: rules, log: log)

        let payload: [[String: Any]] = [[
            "fravg": 0,
            "id": rules.id,
            "sid": UUID().uuidString,
            "signpoints": [Any](),
            "spavg": spAvg,
            "state": "0",
            "tracks": tracks,
            "userId": userId
        ]]

        let samples = points.map { TrackSample(longitude: $0.lon, latitude: $0.lat, timeMs: $0.timeMs) }

        return RunOutcome(
            payload: payload,
            distanceM: actualDistance,
            durationSec: actualDurationSec,
            pointCount: points.count,
            segmentCount: tracks.count,
            maxSegmentM: maxSegmentM,
            jumpCount: jumpCount,
            startEndDistanceM: startEndDistanceM,
            samples: samples,
            paceMinPerKm: actualDistance > 0 ? Double(actualDurationSec) / (actualDistance / 1000.0) / 60.0 : 0.0
        )
    }

    // MARK: - 配速收敛

    /// 平均配速按平台规则 (spmin/spmax) 收敛，单位分钟/公里。
    private static func computeSpAvg(
        distanceM: Double,
        durationSec: Int,
        rules: PointRules,
        log: (LogLevel, String) -> Void
    ) -> Int {
        if distanceM <= 0 || durationSec <= 0 { return 0 }
        var spAvgMinPerKm = Int((Double(durationSec) / (distanceM / 1000.0) / 60.0).rounded())
        let spAvgSecPerKm = spAvgMinPerKm * 60
        if spAvgSecPerKm < rules.spmin {
            log(.warn, "配速 \(spAvgMinPerKm) 分钟/公里快于下限 \(rules.spmin / 60) 分钟/公里，已调整至下限")
            spAvgMinPerKm = Int(ceil(Double(rules.spmin) / 60.0))
        } else if spAvgSecPerKm > rules.spmax {
            log(.warn, "配速 \(spAvgMinPerKm) 分钟/公里慢于上限 \(rules.spmax / 60) 分钟/公里，已调整至上限")
            spAvgMinPerKm = Int(floor(Double(rules.spmax) / 60.0))
        }
        return spAvgMinPerKm
    }

    // MARK: - 路径构建

    /// 按目标速度补点，并按目标距离裁剪/扩展路径。与电脑版 adjust_path_for_speed 一致。
    private static func adjustPath(
        _ original: [Coord],
        speedMps: Double,
        targetDistanceM: Double,
        intervalSec: Int,
        log: (LogLevel, String) -> Void
    ) -> [Coord] {
        let interval = max(0.1, speedMps * Double(max(1, intervalSec)))
        let compensatedTarget = targetDistanceM + distanceCompensationM

        var detailed: [Coord] = [original.first!]
        for i in 0..<(original.count - 1) {
            detailed.append(contentsOf: interpolateBetween(original[i], original[i + 1], interval: interval))
            detailed.append(original[i + 1])
        }

        let singleLoop = routeDistance(detailed)
        let startEndDistance = GpsUtil.haversineDistance(
            lat1: detailed.first!.lat, lon1: detailed.first!.lon,
            lat2: detailed.last!.lat, lon2: detailed.last!.lon
        )

        if singleLoop > compensatedTarget {
            log(.warn, "单次路径长度 \(fmt(singleLoop))m 超过目标 \(fmt(targetDistanceM))m，自动削减至目标距离")
            return truncateTo(detailed, targetDistance: compensatedTarget)
        }

        if startEndDistance > roundTripThresholdM {
            log(.info, "采用往返策略: 起终点距离 \(fmt(startEndDistance))m > \(Int(roundTripThresholdM))m")
            return buildRoundTrip(detailed, singleLoop: singleLoop, compensatedTarget: compensatedTarget)
        }

        log(.info, "采用环路策略: 起终点距离 \(fmt(startEndDistance))m <= \(Int(roundTripThresholdM))m")
        return buildLoops(detailed, singleLoop: singleLoop, compensatedTarget: compensatedTarget)
    }

    private static func interpolateBetween(_ a: Coord, _ b: Coord, interval: Double) -> [Coord] {
        let total = GpsUtil.haversineDistance(lat1: a.lat, lon1: a.lon, lat2: b.lat, lon2: b.lon)
        if total <= 0.0 || interval <= 0.0 { return [] }
        let count = Int(total / interval)
        if count <= 0 { return [] }
        return (1...count).map { i in
            let f = Double(i) / Double(count + 1)
            return Coord(lon: a.lon + f * (b.lon - a.lon), lat: a.lat + f * (b.lat - a.lat))
        }
    }

    /// 沿路径累积到 targetDistance 后截断，末段按比例插值。
    private static func truncateTo(_ path: [Coord], targetDistance: Double) -> [Coord] {
        var result: [Coord] = [path.first!]
        var acc = 0.0
        for i in 0..<(path.count - 1) {
            let seg = GpsUtil.haversineDistance(
                lat1: path[i].lat, lon1: path[i].lon,
                lat2: path[i + 1].lat, lon2: path[i + 1].lon
            )
            if acc + seg > targetDistance {
                let remaining = targetDistance - acc
                if seg > 0 {
                    let f = remaining / seg
                    result.append(Coord(
                        lon: path[i].lon + f * (path[i + 1].lon - path[i].lon),
                        lat: path[i].lat + f * (path[i + 1].lat - path[i].lat)
                    ))
                }
                acc = targetDistance
                break
            }
            if result.last! != path[i] { result.append(path[i]) }
            acc += seg
        }
        return result
    }

    /// 沿路径累积到 targetDistance，用于余量部分。
    private static func partialForward(_ path: [Coord], targetDistance: Double) -> [Coord] {
        var partial: [Coord] = []
        var acc = 0.0
        for i in 0..<(path.count - 1) {
            let seg = GpsUtil.haversineDistance(
                lat1: path[i].lat, lon1: path[i].lon,
                lat2: path[i + 1].lat, lon2: path[i + 1].lon
            )
            if acc + seg > targetDistance {
                let remaining = targetDistance - acc
                if seg > 0 {
                    let f = remaining / seg
                    partial.append(Coord(
                        lon: path[i].lon + f * (path[i + 1].lon - path[i].lon),
                        lat: path[i].lat + f * (path[i + 1].lat - path[i].lat)
                    ))
                }
                break
            }
            if partial.isEmpty || partial.last! != path[i] { partial.append(path[i]) }
            acc += seg
        }
        return partial
    }

    private static func appendAvoidingDuplicate(_ target: inout [Coord], _ source: [Coord]) {
        if source.isEmpty { return }
        let skip = (!target.isEmpty && target.last! == source.first!) ? 1 : 0
        target.append(contentsOf: source.dropFirst(skip))
    }

    private static func buildRoundTrip(_ detailed: [Coord], singleLoop: Double, compensatedTarget: Double) -> [Coord] {
        let roundTrip = singleLoop * 2
        if roundTrip <= 0.0 {
            return truncateTo(repeatForward(detailed, targetDistance: compensatedTarget), targetDistance: compensatedTarget)
        }

        var result: [Coord] = []
        let completeTrips = Int(compensatedTarget / roundTrip)
        let remaining = compensatedTarget - Double(completeTrips) * roundTrip

        for _ in 0..<completeTrips {
            result.append(contentsOf: detailed)
            appendAvoidingDuplicate(&result, detailed.reversed())
        }

        if remaining > 0 {
            let ratio = remaining / roundTrip
            if ratio <= 0.5 {
                appendAvoidingDuplicate(&result, partialForward(detailed, targetDistance: remaining))
            } else {
                appendAvoidingDuplicate(&result, detailed)
                let backwardRemaining = remaining - singleLoop
                if backwardRemaining > 0 {
                    appendAvoidingDuplicate(&result, partialForward(Array(detailed.reversed()), targetDistance: backwardRemaining))
                }
            }
        }
        return result
    }

    private static func buildLoops(_ detailed: [Coord], singleLoop: Double, compensatedTarget: Double) -> [Coord] {
        if singleLoop <= 0.0 {
            return truncateTo(repeatForward(detailed, targetDistance: compensatedTarget), targetDistance: compensatedTarget)
        }

        var result: [Coord] = []
        let completeLoops = Int(compensatedTarget / singleLoop)
        let remaining = compensatedTarget - Double(completeLoops) * singleLoop

        for _ in 0..<completeLoops { result.append(contentsOf: detailed) }
        if remaining > 0 { appendAvoidingDuplicate(&result, partialForward(detailed, targetDistance: remaining)) }
        return result
    }

    private static func repeatForward(_ path: [Coord], targetDistance: Double) -> [Coord] {
        var result: [Coord] = []
        let loopDistance = routeDistance(path)
        if loopDistance <= 0.0 {
            result.append(contentsOf: path)
            return result
        }
        let loops = max(1, Int(ceil(targetDistance / loopDistance)))
        for _ in 0..<loops { result.append(contentsOf: path) }
        return result
    }

    // MARK: - 轨迹点

    private struct GeneratedPoint {
        let lon: Double
        let lat: Double
        let timeMs: Int64
    }

    private static func generateTrackPoints(_ coords: [Coord], startTimeMs: Int64, speedMps: Double) -> [GeneratedPoint] {
        var points: [GeneratedPoint] = []
        var cumDist = 0.0
        for (i, coord) in coords.enumerated() {
            if i > 0 {
                let prev = coords[i - 1]
                cumDist += GpsUtil.haversineDistance(lat1: prev.lat, lon1: prev.lon, lat2: coord.lat, lon2: coord.lon)
            }
            let elapsed = speedMps > 0 ? cumDist / speedMps : 0.0
            points.append(GeneratedPoint(lon: coord.lon, lat: coord.lat, timeMs: startTimeMs + Int64(elapsed * 1000)))
        }
        return points
    }

    private static func splitIntoSegments(
        _ allPoints: [GeneratedPoint],
        totalDurationSec: Int,
        log: (LogLevel, String) -> Void
    ) -> [[String: Any]] {
        var tracks: [[String: Any]] = []
        var idx = 0
        while idx < allPoints.count {
            let remainingPoints = allPoints.count - idx
            let segmentLength: Int
            if remainingPoints <= minSegmentPoints {
                segmentLength = remainingPoints
            } else {
                let upper = max(minSegmentPoints + 1, remainingPoints / 3 + 1)
                segmentLength = max(minSegmentPoints, Int.random(in: minSegmentPoints..<upper))
            }
            let segment = Array(allPoints[idx..<min(idx + segmentLength, allPoints.count)])
            idx += segment.count
            if segment.isEmpty { continue }

            let rand = Double.random(in: 0..<1)
            let status: String
            if rand < 0.8 {
                status = "normal"
            } else if rand < 0.9 {
                status = "invalid"
            } else {
                status = "stop"
            }
            let tstate = status == "invalid" ? "2" : "0"

            var segmentDistance = 0.0
            if segment.count >= 2 {
                for i in 0..<(segment.count - 1) {
                    segmentDistance += GpsUtil.haversineDistance(
                        lat1: segment[i].lat, lon1: segment[i].lon,
                        lat2: segment[i + 1].lat, lon2: segment[i + 1].lon
                    )
                }
            }

            let startTime = segment.first!.timeMs
            let endTime = segment.last!.timeMs
            let duration = Int(ceil(Double(endTime - startTime) / 1000.0))

            let points: [[String: Any]] = segment.map { p in
                [
                    "latLng": [
                        "latitude": roundTrack(p.lat),
                        "longitude": roundTrack(p.lon)
                    ],
                    "location": "\(fmtTrack(p.lon)),\(fmtTrack(p.lat))",
                    "step": 0,
                    "locatetime": p.timeMs
                ]
            }

            let segmentDict: [String: Any] = [
                "counts": segment.count,
                "distance": segmentDistance,
                "duration": duration,
                "points": points,
                "status": status,
                "trid": UUID().uuidString,
                "tstate": tstate,
                "stime": Int(startTime / 1000),
                "etime": Int(endTime / 1000)
            ]
            tracks.append(segmentDict)
        }
        if totalDurationSec <= 0 { log(.warn, "轨迹总时长异常") }
        return tracks
    }

    // MARK: - 格式化

    private static func fmt(_ value: Double) -> String { String(format: "%.2f", value) }
    private static func fmt1(_ value: Double) -> String { String(format: "%.1f", value) }
    private static func fmtTrack(_ value: Double) -> String { String(format: "%.\(trackPointDecimalPlaces)f", value) }

    private static func roundTrack(_ value: Double) -> Double {
        let factor = pow(10.0, Double(trackPointDecimalPlaces))
        return (value * factor).rounded() / factor
    }
}
