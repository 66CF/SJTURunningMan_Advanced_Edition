//
//  TaskModels.swift
//  SJTURunningMan
//
//  任务 / 日志 / 进度 / 预览等共享模型（对齐安卓版 TaskModels.kt）。
//

import Foundation

// MARK: - 日志

enum LogLevel {
    case info
    case warn
    case error
    case success
}

struct LogEntry: Identifiable, Equatable {
    let id = UUID()
    let level: LogLevel
    let time: String
    let message: String

    static func == (lhs: LogEntry, rhs: LogEntry) -> Bool {
        lhs.id == rhs.id
    }
}

// MARK: - 任务状态

enum TaskStatus: Equatable {
    case idle
    case preparing
    case authenticating
    case generating
    case uploading
    case stopping
    case finished
    case failed

    var label: String {
        switch self {
        case .idle: return "空闲"
        case .preparing: return "准备中"
        case .authenticating: return "获取认证"
        case .generating: return "生成数据"
        case .uploading: return "上传中"
        case .stopping: return "正在停止"
        case .finished: return "已完成"
        case .failed: return "失败"
        }
    }

    /// 是否处于进行中（用于旋转动画与配色）。
    var isActive: Bool {
        switch self {
        case .idle, .finished, .failed: return false
        default: return true
        }
    }

    var isSuccess: Bool { self == .finished }
    var isFailure: Bool { self == .failed }
}

struct TaskProgress: Equatable {
    var current: Int = 0
    var total: Int = 0
    var status: TaskStatus = .idle

    var fraction: Double {
        guard total > 0 else { return 0 }
        return min(max(Double(current) / Double(total), 0), 1)
    }
}

// MARK: - 路线冲突

enum RouteDecision {
    case truncate
    case fullRoute
    case cancel
}

struct RouteConflict: Identifiable, Equatable {
    let id = UUID()
    let routeName: String
    let routeDistanceM: Double
    let targetDistanceM: Double

    static func == (lhs: RouteConflict, rhs: RouteConflict) -> Bool {
        lhs.id == rhs.id
    }
}

// MARK: - 路线预览

struct RoutePreview: Equatable {
    var title: String
    var status: String
    var distanceKm: Double
    var durationMin: Double
    var pointCount: Int
    var segmentCount: Int
    var jumpCount: Int
    var maxSegmentM: Double
    var startEndDistanceM: Double
    var samples: [TrackSample]

    static func == (lhs: RoutePreview, rhs: RoutePreview) -> Bool {
        lhs.title == rhs.title && lhs.status == rhs.status && lhs.pointCount == rhs.pointCount
    }

    var summary: String {
        String(format: "%.2fkm / %d点 / %d处跳段 / %@", distanceKm, pointCount, jumpCount, status)
    }
}

// MARK: - 运行配置

struct RunConfig: Equatable {
    var days: Int = 1
    var distanceKm: Double = AppConfig.defaultDistanceKm
    var hour: Int = AppConfig.defaultRunHour
    var minute: Int = AppConfig.defaultRunMinute
    var second: Int = AppConfig.defaultRunSecond
    var startDate: Date = Calendar.current.startOfDay(for: Date())
    var routeDecision: RouteDecision?

    static func == (lhs: RunConfig, rhs: RunConfig) -> Bool {
        lhs.days == rhs.days &&
        lhs.distanceKm == rhs.distanceKm &&
        lhs.hour == rhs.hour &&
        lhs.minute == rhs.minute &&
        lhs.second == rhs.second &&
        lhs.startDate == rhs.startDate &&
        lhs.routeDecision == rhs.routeDecision
    }
}

// MARK: - 打卡规则

struct PointRules: Equatable {
    var id: Int
    var spmin: Int
    var spmax: Int

    static let `default` = PointRules(id: 9, spmin: 180, spmax: 540)
}

// MARK: - 生成结果

struct TrackSample: Equatable {
    let longitude: Double
    let latitude: Double
    let timeMs: Int64
}

struct RunOutcome {
    var payload: [[String: Any]]
    var distanceM: Double
    var durationSec: Int
    var pointCount: Int
    var segmentCount: Int
    var maxSegmentM: Double
    var jumpCount: Int
    var startEndDistanceM: Double
    var samples: [TrackSample]
    var paceMinPerKm: Double

    var distanceKm: Double { distanceM / 1000.0 }
}
