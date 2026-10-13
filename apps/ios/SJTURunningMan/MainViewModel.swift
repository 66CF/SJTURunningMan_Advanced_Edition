//
//  MainViewModel.swift
//  SJTURunningMan
//
//  任务编排与状态，行为对齐安卓版 MainViewModel.kt。
//

import SwiftUI
import Observation

@MainActor
@Observable
final class MainViewModel {

    // MARK: - 可观察状态

    var logs: [LogEntry] = []
    var running = false
    var progress = TaskProgress()
    var routes: [RouteInfo] = []
    var selectedRoute: RouteInfo?
    var preview: RoutePreview?
    var routeConflict: RouteConflict?
    var reloginRequired = false

    /// 记录当前 UI 配置，供路线冲突确认后复用。
    private(set) var currentConfig = RunConfig()

    // MARK: - 依赖

    private let api = ApiService()
    private let routeManager = RouteManager.shared
    private var job: Task<Void, Never>?
    private var previewJob: Task<Void, Never>?

    private static let maxLogEntries = 400

    init() {
        loadRoutes()
    }

    // MARK: - 路线

    func loadRoutes() {
        let allRoutes = routeManager.getAllRoutes()
        routes = allRoutes
        if selectedRoute == nil && !allRoutes.isEmpty {
            selectedRoute = allRoutes.first(where: { $0.isDefault }) ?? allRoutes.first
        }
        refreshPreviewForSelectedRoute()
    }

    func selectRoute(_ route: RouteInfo) {
        selectedRoute = route
        logMsg(.info, "已选择路线: \(route.name)")
        refreshPreviewForSelectedRoute()
    }

    func deleteRoute(_ route: RouteInfo) {
        if route.isDefault {
            logMsg(.warn, "默认路线不能删除")
            return
        }
        routeManager.deleteRoute(id: route.id)
        logMsg(.success, "已删除路线: \(route.name)")
        loadRoutes()
        if selectedRoute?.id == route.id {
            selectedRoute = routes.first(where: { $0.isDefault })
        }
    }

    func refreshRoutes() {
        loadRoutes()
    }

    // MARK: - 日志

    func clearLogs() {
        logs = []
    }

    private func logMsg(_ level: LogLevel, _ msg: String) {
        let entry = LogEntry(level: level, time: DateFormatter.timeOnly.string(from: Date()), message: msg)
        if logs.count >= Self.maxLogEntries {
            logs.removeFirst(logs.count - Self.maxLogEntries + 1)
        }
        logs.append(entry)
    }

    // MARK: - 校验

    func validate(_ config: RunConfig) -> String? {
        if !(1...AppConfig.maxRunTimes).contains(config.days) {
            return "跑步次数应在 1-\(AppConfig.maxRunTimes) 之间"
        }
        if config.distanceKm <= 0 || config.distanceKm > AppConfig.maxDistanceKm {
            return "距离应大于 0 且不超过 \(Int(AppConfig.maxDistanceKm))km"
        }
        if !(0...23).contains(config.hour) || !(0...59).contains(config.minute) || !(0...59).contains(config.second) {
            return "时间范围无效，小时 00-23，分钟和秒 00-59"
        }
        if selectedRoute == nil {
            return "未选择任何路线，请先选择路线"
        }
        return nil
    }

    func updateConfig(_ config: RunConfig) {
        currentConfig = config
    }

    // MARK: - 上传

    func startUpload(_ config: RunConfig) {
        if running { return }

        if let message = validate(config) {
            logMsg(.error, "配置错误: \(message)")
            return
        }

        guard let route = selectedRoute else { return }
        let targetDistanceM = config.distanceKm * 1000.0

        // 上传前检查路线长度，超过目标距离时先征询用户（与电脑版一致）
        if config.routeDecision == nil {
            let coordinates = currentRouteCoordinates()
            if coordinates.count < 2 {
                logMsg(.error, "路线坐标为空，请选择其他路线")
                return
            }
            let routeDistanceM = DataGenerator.correctedRouteDistance(coordinates)
            if routeDistanceM > targetDistanceM {
                routeConflict = RouteConflict(routeName: route.name, routeDistanceM: routeDistanceM, targetDistanceM: targetDistanceM)
            } else {
                runUpload(configWith(config, decision: .truncate))
            }
            return
        }

        if config.routeDecision == .cancel {
            routeConflict = nil
            logMsg(.warn, "已取消本次任务")
            return
        }

        runUpload(config)
    }

    func resolveRouteConflict(_ decision: RouteDecision) {
        guard let conflict = routeConflict else { return }
        routeConflict = nil
        switch decision {
        case .truncate: logMsg(.info, "用户选择自动削减路线至目标距离")
        case .fullRoute: logMsg(.info, "用户选择按照完整路线进行跑步")
        case .cancel: logMsg(.warn, "用户选择停止任务")
        }
        var config = currentConfig
        config.routeDecision = decision
        if decision == .fullRoute {
            config.distanceKm = conflict.routeDistanceM / 1000.0
        }
        startUpload(config)
    }

    private func configWith(_ config: RunConfig, decision: RouteDecision) -> RunConfig {
        var copy = config
        copy.routeDecision = decision
        return copy
    }

    private func runUpload(_ config: RunConfig) {
        reloginRequired = false
        job = Task { [weak self] in
            guard let self else { return }
            self.running = true
            self.progress = TaskProgress(current: 0, total: config.days, status: .preparing)

            do {
                guard let route = self.selectedRoute else { throw DataGenerator.GeneratorError.insufficientPoints }
                self.logMsg(.info, "使用路线: \(route.name)")

                self.logMsg(.info, "步骤 1/2: 获取认证...")
                let coordinates = self.currentRouteCoordinates()
                if coordinates.count < 2 {
                    self.logMsg(.error, "路线坐标为空，请选择其他路线")
                    self.progress = TaskProgress(current: 0, total: config.days, status: .failed)
                    self.running = false
                    return
                }
                self.logMsg(.info, "路线点数量: \(coordinates.count)")

                let uid = await self.api.getUid()
                if uid == nil {
                    self.logMsg(.error, "UID 获取失败，cookie 可能已过期")
                    self.progress = TaskProgress(current: 0, total: config.days, status: .failed)
                    self.triggerRelogin()
                    self.running = false
                    return
                }
                self.logMsg(.success, "UID 获取成功")

                self.progress = TaskProgress(current: 0, total: config.days, status: .authenticating)
                let rules = await self.api.getPointRules(
                    authToken: uid!,
                    longitude: AppConfig.startLongitude,
                    latitude: AppConfig.startLatitude
                )
                self.logMsg(.info, "打卡规则: id=\(rules.id) 配速 \(rules.spmin / 60)-\(rules.spmax / 60) 分钟/公里")

                self.logMsg(.info, "步骤 2/2: 生成并上传跑步数据...")

                let targetDistanceM = config.distanceKm * 1000.0
                var successCount = 0
                var failCount = 0

                for i in 0..<config.days {
                    if !self.running { break }

                    let startDate = self.baseStartDateTime(config, index: i)
                    self.logMsg(.info, "生成第 \(i + 1)/\(config.days) 条数据 (\(DateFormatter.full.string(from: startDate)))")

                    self.progress = TaskProgress(current: i, total: config.days, status: .generating)
                    let outcome = try DataGenerator.generate(
                        coordinates: coordinates,
                        userId: uid!,
                        targetDistanceM: targetDistanceM,
                        startTimeMs: Int64(startDate.timeIntervalSince1970 * 1000),
                        intervalSeconds: AppConfig.intervalSeconds,
                        rules: rules,
                        truncateToTarget: config.routeDecision != .fullRoute,
                        log: { [weak self] level, msg in self?.logMsg(level, msg) }
                    )
                    self.publishPreview(outcome, routeName: route.name, status: "待上传", index: i + 1, total: config.days)
                    self.logMsg(.info, "实际距离 " + String(format: "%.2f", outcome.distanceM / 1000.0) +
                                "km / \(outcome.durationSec / 60)min / \(outcome.pointCount)点")

                    self.progress = TaskProgress(current: i, total: config.days, status: .uploading)
                    let result = await self.api.upload(authToken: uid!, payload: outcome.payload)
                    if result.success {
                        successCount += 1
                        self.logMsg(.success, "第 \(i + 1)/\(config.days) 条上传成功")
                        self.publishPreview(outcome, routeName: route.name, status: "已上传", index: i + 1, total: config.days)
                    } else if result.needRelogin {
                        self.logMsg(.error, "上传失败: \(result.message)")
                        self.progress = TaskProgress(current: i + 1, total: config.days, status: .failed)
                        self.triggerRelogin()
                        break
                    } else {
                        failCount += 1
                        self.logMsg(.error, "第 \(i + 1) 条失败: \(result.message)")
                        self.publishPreview(outcome, routeName: route.name, status: "上传失败", index: i + 1, total: config.days)
                    }

                    self.progress = TaskProgress(current: i + 1, total: config.days, status: .uploading)
                }

                if self.running {
                    let finished = failCount == 0
                    self.progress = TaskProgress(current: successCount + failCount, total: config.days,
                                                 status: finished ? .finished : .failed)
                    self.logMsg(finished ? .success : .warn, "任务完成: 成功 \(successCount) 条，失败 \(failCount) 条")
                } else {
                    self.logMsg(.warn, "任务已手动停止")
                }
            } catch is CancellationError {
                self.logMsg(.warn, "任务已手动停止")
            } catch {
                self.logMsg(.error, "错误: \(error.localizedDescription)")
                self.progress.status = .failed
            }
            self.running = false
        }
    }

    func stopUpload() {
        guard running else { return }
        progress.status = .stopping
        logMsg(.warn, "正在停止任务...")
        job?.cancel()
        running = false
        progress.status = .idle
    }

    // MARK: - 预览

    /// 离线预览：不登录，按当前设置本地跑一遍生成算法。
    func previewRoute() {
        guard previewJob == nil else { return }
        guard let route = selectedRoute else {
            logMsg(.error, "未选择路线，无法预览")
            return
        }
        if let error = validate(currentConfig) {
            logMsg(.error, "配置错误: \(error)")
            return
        }
        previewJob = Task { [weak self] in
            guard let self else { return }
            self.logMsg(.info, "正在离线预览路线…")
            defer { self.previewJob = nil }
            let coordinates = self.currentRouteCoordinates()
            if coordinates.count < 2 {
                self.logMsg(.error, "路线坐标为空，无法预览")
                return
            }
            do {
                let outcome = try DataGenerator.generate(
                    coordinates: coordinates,
                    userId: "preview",
                    targetDistanceM: self.currentConfig.distanceKm * 1000.0,
                    startTimeMs: Int64(Date().timeIntervalSince1970 * 1000),
                    intervalSeconds: AppConfig.intervalSeconds,
                    truncateToTarget: true,
                    log: { [weak self] level, msg in self?.logMsg(level, msg) }
                )
                self.publishPreview(outcome, routeName: route.name, status: "离线预览", index: 1, total: 1)
                self.logMsg(.success, "预览生成完成: " + String(format: "%.2fkm / %d点 / %d处跳段",
                                                              outcome.distanceM / 1000.0, outcome.pointCount, outcome.jumpCount))
            } catch {
                self.logMsg(.error, "预览失败: \(error.localizedDescription)")
            }
        }
    }

    private func refreshPreviewForSelectedRoute() {
        guard let route = selectedRoute else { return }
        let coordinates = currentRouteCoordinates()
        guard coordinates.count >= 2 else { return }
        let corrected = DataGenerator.correct(coordinates)
        let distanceM = DataGenerator.routeDistance(corrected)

        var maxSegmentM = 0.0
        for i in 0..<(corrected.count - 1) {
            maxSegmentM = max(maxSegmentM, GpsUtil.haversineDistance(
                lat1: corrected[i].lat, lon1: corrected[i].lon,
                lat2: corrected[i + 1].lat, lon2: corrected[i + 1].lon
            ))
        }
        let startEndDistanceM = GpsUtil.haversineDistance(
            lat1: corrected.first!.lat, lon1: corrected.first!.lon,
            lat2: corrected.last!.lat, lon2: corrected.last!.lon
        )

        preview = RoutePreview(
            title: route.name,
            status: "原始路线",
            distanceKm: distanceM / 1000.0,
            durationMin: 0,
            pointCount: corrected.count,
            segmentCount: 0,
            jumpCount: 0,
            maxSegmentM: maxSegmentM,
            startEndDistanceM: startEndDistanceM,
            samples: corrected.map { TrackSample(longitude: $0.lon, latitude: $0.lat, timeMs: 0) }
        )
    }

    private func publishPreview(_ outcome: RunOutcome, routeName: String, status: String, index: Int, total: Int) {
        preview = RoutePreview(
            title: total > 1 ? "\(routeName) · 第 \(index)/\(total) 条" : routeName,
            status: status,
            distanceKm: outcome.distanceM / 1000.0,
            durationMin: Double(outcome.durationSec) / 60.0,
            pointCount: outcome.pointCount,
            segmentCount: outcome.segmentCount,
            jumpCount: outcome.jumpCount,
            maxSegmentM: outcome.maxSegmentM,
            startEndDistanceM: outcome.startEndDistanceM,
            samples: outcome.samples
        )
    }

    // MARK: - 辅助

    private func currentRouteCoordinates() -> [Coord] {
        guard let route = selectedRoute else { return [] }
        return routeManager.getRouteCoordinates(for: route)
    }

    /// 以所选日期为最新一条记录向前生成，并加入 ±10 分钟 / ±30 秒随机波动。
    private func baseStartDateTime(_ config: RunConfig, index: Int) -> Date {
        let calendar = Calendar.current
        var comps = calendar.dateComponents([.year, .month, .day], from: config.startDate)
        comps.hour = config.hour
        comps.minute = config.minute
        comps.second = config.second
        let base = calendar.date(from: comps) ?? config.startDate
        let withOffset = base
            .addingTimeInterval(TimeInterval(Int.random(in: -10...10) * 60))
            .addingTimeInterval(TimeInterval(Int.random(in: -30...30)))
        return calendar.date(byAdding: .day, value: -index, to: withOffset) ?? withOffset
    }

    private func triggerRelogin() {
        reloginRequired = true
        api.clearCookies()
    }
}

extension DateFormatter {
    static let timeOnly: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss"
        return f
    }()

    static let full: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return f
    }()
}
