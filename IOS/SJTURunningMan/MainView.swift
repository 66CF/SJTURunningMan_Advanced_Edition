//
//  MainView.swift
//  SJTURunningMan
//
//  主界面：状态卡、跑步路线、任务设置、运行日志与底部操作栏。
//  结构对齐安卓版 ui/MainScreen.kt，卡片与按钮采用 iOS 26/27 Liquid Glass 外观。
//

import SwiftUI

struct MainView: View {
    @State private var viewModel = MainViewModel()

    @Environment(\.verticalSizeClass) private var verticalSizeClass

    // 弹窗与导航
    @State private var showLogin = false
    @State private var showRouteDesign = false
    @State private var showDatePicker = false
    @State private var showAbout = false
    @State private var showCommitment = false
    @State private var routeToDelete: RouteInfo?
    @State private var snackbarMessage: String?

    // 任务参数
    @State private var days = 1
    @State private var showCustomDays = false
    @State private var customDaysText = ""
    @State private var hour = AppConfig.defaultRunHour
    @State private var minute = AppConfig.defaultRunMinute
    @State private var second = AppConfig.defaultRunSecond
    @State private var distanceKm = AppConfig.defaultDistanceKm
    @State private var startDate = Calendar.current.date(byAdding: .day, value: -1, to: Date()) ?? Date()

    @AppStorage("commitment_accepted") private var commitmentAccepted = false

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    private var isCompact: Bool { verticalSizeClass == .compact }
    private var logHeight: CGFloat { isCompact ? 150 : 210 }
    private var previewHeight: CGFloat { isCompact ? 120 : 150 }

    private var dateText: String { Self.dateFormatter.string(from: startDate) }
    private var daysLabel: String { showCustomDays ? "自定义" : "\(days)" }

    private var config: RunConfig {
        RunConfig(days: days, distanceKm: distanceKm, hour: hour, minute: minute, second: second, startDate: startDate)
    }

    var body: some View {
        ZStack {
            NavigationStack {
                ScrollView {
                    VStack(spacing: 14) {
                        statusCard
                        if viewModel.reloginRequired {
                            reloginBanner
                        }
                        routeSection
                        settingsSection
                        logSection
                        DisclaimerNotice()
                        Spacer().frame(height: 8)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 8)
                }
                .background(backgroundGradient)
                .navigationTitle("交我润")
                .navigationSubtitle("v\(AppConfig.versionName)")
                .toolbarBackgroundVisibility(.hidden, for: .navigationBar)
                .toolbar { toolbarContent }
                .safeAreaInset(edge: .bottom) { bottomBar }
            }

            snackbarLayer
            dialogLayer
        }
        .animation(.easeInOut(duration: 0.2), value: showCommitment)
        .animation(.easeInOut(duration: 0.2), value: showAbout)
        .animation(.easeInOut(duration: 0.2), value: viewModel.routeConflict)
        .animation(.easeInOut(duration: 0.2), value: routeToDelete)
        .task(id: config) { viewModel.updateConfig(config) }
        .onAppear {
            if !commitmentAccepted {
                showCommitment = true
            }
        }
        .sheet(isPresented: $showLogin) {
            LoginView { showLogin = false }
        }
        .sheet(isPresented: $showRouteDesign, onDismiss: { viewModel.refreshRoutes() }) {
            RouteDesignView()
        }
        .sheet(isPresented: $showDatePicker) {
            DatePickerSheet(date: $startDate)
        }
    }

    // MARK: - 背景与工具栏

    private var backgroundGradient: some View {
        LinearGradient(
            colors: [AppTheme.gradientStart.opacity(0.55), AppTheme.background],
            startPoint: .top,
            endPoint: .bottom
        )
        .ignoresSafeArea()
    }

    @ToolbarContentBuilder
    private var toolbarContent: some ToolbarContent {
        ToolbarItem(placement: .topBarTrailing) {
            Button {
                showAbout = true
            } label: {
                Image(systemName: "info.circle")
            }
            .accessibilityLabel("关于与检查更新")
        }
        ToolbarItem(placement: .topBarTrailing) {
            Button {
                showLogin = true
            } label: {
                Image(systemName: "person.badge.key")
            }
            .accessibilityLabel("重新登录")
        }
    }

    // MARK: - 状态卡

    private var statusCard: some View {
        StatusCard(
            progress: viewModel.progress,
            headline: "\(days) 次 · \(String(format: "%.1f", distanceKm)) km",
            stats: [
                StatItem("次数", "\(days)"),
                StatItem("距离", String(format: "%.1f km", distanceKm)),
                StatItem("时间", String(format: "%02d:%02d:%02d", hour, minute, second))
            ]
        )
    }

    private var reloginBanner: some View {
        SectionCard(title: "Cookie 已过期", icon: "exclamationmark.triangle", elevated: true) {
            HStack(spacing: 10) {
                Text("登录状态失效，请重新登录后继续上传。")
                    .font(.callout)
                    .foregroundStyle(AppTheme.onSurface)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Button {
                    showLogin = true
                } label: {
                    Text("登录")
                }
                .buttonStyle(.glassProminent)
            }
        }
    }

    // MARK: - 跑步路线

    private var routeSection: some View {
        SectionCard(
            title: "跑步路线",
            icon: "point.topleft.down.curvedto.point.bottomright.up",
            supporting: viewModel.selectedRoute.map { "\($0.pointCount) 个坐标点" } ?? "请选择路线",
            action: {
                Button {
                    showRouteDesign = true
                } label: {
                    Image(systemName: "plus")
                }
                .accessibilityLabel("设计新路线")
            }
        ) {
            VStack(alignment: .leading, spacing: 12) {
                MenuDropdown(
                    value: viewModel.selectedRoute?.name ?? "请选择路线",
                    label: "预设路线",
                    options: viewModel.routes.map { route in
                        RouteOption(
                            id: route.id,
                            title: route.name,
                            subtitle: "\(route.pointCount) 个点" + (route.isDefault ? " · 内置" : ""),
                            selected: viewModel.selectedRoute?.id == route.id
                        )
                    },
                    onSelect: { option in
                        if let route = viewModel.routes.first(where: { $0.id == option.id }) {
                            viewModel.selectRoute(route)
                        }
                    }
                )

                RoutePreviewCanvas(
                    samples: viewModel.preview?.samples ?? [],
                    jumpCount: viewModel.preview?.jumpCount ?? 0
                )
                .frame(maxWidth: .infinity)
                .frame(height: previewHeight)
                .background(AppTheme.cardLow)
                .clipShape(RoundedRectangle(cornerRadius: AppTheme.panelCornerRadius))

                HStack(alignment: .center, spacing: 10) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(viewModel.preview?.title ?? "路线预览")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(AppTheme.onSurface)
                        Text(viewModel.preview?.summary ?? "点击「预览路线」按当前设置离线生成")
                            .font(.caption)
                            .foregroundStyle(AppTheme.onSurfaceVariant)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)

                    let jumps = viewModel.preview?.jumpCount ?? 0
                    if jumps > 0 {
                        StatusPill(text: "\(jumps) 处跳段", color: AppTheme.error)
                    }
                }

                SectionDivider()

                HStack(spacing: 8) {
                    Button {
                        viewModel.previewRoute()
                    } label: {
                        Label("预览路线", systemImage: "map")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glass)
                    .disabled(viewModel.running || viewModel.selectedRoute == nil)

                    Button {
                        routeToDelete = viewModel.selectedRoute
                    } label: {
                        Label("删除路线", systemImage: "trash")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glass)
                    .tint(AppTheme.error)
                    .disabled(viewModel.running || viewModel.selectedRoute == nil || viewModel.selectedRoute?.isDefault == true)
                }
            }
        }
    }

    // MARK: - 任务设置

    private var settingsSection: some View {
        SectionCard(title: "任务设置", icon: "calendar") {
            VStack(alignment: .leading, spacing: 12) {
                VStack(alignment: .leading, spacing: 8) {
                    Text("跑步次数")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(AppTheme.onSurface)

                    if showCustomDays {
                        HStack(spacing: 8) {
                            NumberField(value: customDaysText, label: "次数 1-30", width: 140, maxLength: 2) { value in
                                customDaysText = value
                            }
                            Button {
                                if let value = Int(customDaysText) {
                                    days = min(max(value, 1), AppConfig.maxRunTimes)
                                }
                                showCustomDays = false
                            } label: {
                                Text("确定")
                            }
                            .buttonStyle(.glass)
                        }
                    } else {
                        ChoiceChips(
                            options: ["1", "3", "5", "7", "10", "15", "30", "自定义"],
                            selected: daysLabel,
                            onSelect: { option in
                                if option == "自定义" {
                                    showCustomDays = true
                                } else if let value = Int(option) {
                                    days = value
                                }
                            }
                        )
                    }
                }

                SectionDivider()

                VStack(alignment: .leading, spacing: 6) {
                    Text("开始时间")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(AppTheme.onSurface)
                    TimeField(hour: hour, minute: minute, second: second) { h, m, s in
                        hour = h
                        minute = m
                        second = s
                    }
                    Text("每次跑步自动加入 ±10 分钟 / ±30 秒随机波动")
                        .font(.caption)
                        .foregroundStyle(AppTheme.onSurfaceVariant)
                }

                SectionDivider()

                VStack(alignment: .leading, spacing: 6) {
                    Text("结束日期")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(AppTheme.onSurface)

                    Button {
                        if !viewModel.running {
                            showDatePicker = true
                        }
                    } label: {
                        HStack {
                            Text(dateText)
                                .font(.body)
                                .foregroundStyle(AppTheme.onSurface)
                            Spacer(minLength: 0)
                            Image(systemName: "calendar")
                                .foregroundStyle(AppTheme.primary)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        .frame(maxWidth: .infinity)
                        .background(AppTheme.cardLow, in: RoundedRectangle(cornerRadius: AppTheme.panelCornerRadius))
                        .overlay(
                            RoundedRectangle(cornerRadius: AppTheme.panelCornerRadius)
                                .stroke(AppTheme.outlineVariant, lineWidth: 1)
                        )
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .disabled(viewModel.running)
                }

                SectionDivider()

                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 8) {
                        Image(systemName: "ruler")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(AppTheme.primary)
                        Text("目标距离")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(AppTheme.onSurface)
                    }
                    DistanceSlider(
                        distanceKm: distanceKm,
                        range: AppConfig.minDistanceKm...AppConfig.maxDistanceKm
                    ) { value in
                        distanceKm = value
                    }
                }
            }
        }
    }

    // MARK: - 运行日志

    private var logSection: some View {
        SectionCard(
            title: "运行日志",
            icon: "terminal",
            supporting: viewModel.running ? "任务运行中" : "共 \(viewModel.logs.count) 条记录",
            action: {
                if !viewModel.running && !viewModel.logs.isEmpty {
                    Button {
                        viewModel.clearLogs()
                    } label: {
                        Text("清空")
                    }
                    .buttonStyle(.glass)
                }
            }
        ) {
            ConsoleLog(entries: viewModel.logs)
                .frame(maxWidth: .infinity)
                .frame(height: logHeight)
                .clipShape(RoundedRectangle(cornerRadius: AppTheme.panelCornerRadius))
        }
    }

    // MARK: - 底部操作栏

    private var bottomBar: some View {
        HStack(spacing: 10) {
            Button {
                viewModel.previewRoute()
            } label: {
                Label("预览", systemImage: "map")
                    .frame(maxWidth: .infinity, minHeight: 50)
            }
            .buttonStyle(.glass)
            .disabled(viewModel.running)

            if viewModel.running {
                Button(role: .destructive) {
                    viewModel.stopUpload()
                } label: {
                    Label("停止任务", systemImage: "stop.fill")
                        .frame(maxWidth: .infinity, minHeight: 50)
                }
                .buttonStyle(.glassProminent)
                .tint(AppTheme.error)
            } else {
                Button {
                    startTask()
                } label: {
                    Label("开始任务", systemImage: "play.fill")
                        .frame(maxWidth: .infinity, minHeight: 50)
                }
                .buttonStyle(.glassProminent)
                .disabled(viewModel.selectedRoute == nil)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
    }

    // MARK: - 提示条

    @ViewBuilder
    private var snackbarLayer: some View {
        if let snackbarMessage {
            VStack {
                Spacer()
                Text(snackbarMessage)
                    .font(.callout)
                    .foregroundStyle(AppTheme.onSurface)
                    .padding(.horizontal, 18)
                    .padding(.vertical, 12)
                    .glassEffect(.regular, in: Capsule())
                    .padding(.bottom, 96)
            }
            .transition(.opacity)
            .task(id: snackbarMessage) {
                try? await Task.sleep(nanoseconds: 3_000_000_000)
                withAnimation { self.snackbarMessage = nil }
            }
        }
    }

    // MARK: - 弹窗

    @ViewBuilder
    private var dialogLayer: some View {
        if showCommitment {
            CommitmentDialog(
                onAccept: {
                    commitmentAccepted = true
                    showCommitment = false
                },
                onDecline: {
                    // 对齐安卓：拒绝承诺书则退出程序
                    exit(0)
                }
            )
            .zIndex(1)
        }

        if let conflict = viewModel.routeConflict {
            RouteConflictDialog(conflict: conflict) { decision in
                viewModel.resolveRouteConflict(decision)
            }
            .zIndex(2)
        }

        if showAbout {
            AboutDialog { showAbout = false }
                .zIndex(3)
        }

        if let route = routeToDelete {
            deleteRouteDialog(route)
                .zIndex(4)
        }
    }

    private func deleteRouteDialog(_ route: RouteInfo) -> some View {
        DialogScrim(onTapOutside: { routeToDelete = nil }) {
            VStack(alignment: .leading, spacing: 16) {
                Text("删除路线")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(AppTheme.onSurface)
                Text("确定要删除路线「\(route.name)」吗？该操作不可撤销。")
                    .font(.callout)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)

                HStack(spacing: 10) {
                    Button {
                        routeToDelete = nil
                    } label: {
                        Text("取消").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glass)

                    Button {
                        viewModel.deleteRoute(route)
                        routeToDelete = nil
                    } label: {
                        Text("删除").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glassProminent)
                    .tint(AppTheme.error)
                }
            }
        }
    }

    // MARK: - 行为

    private func startTask() {
        if let error = viewModel.validate(config) {
            withAnimation { snackbarMessage = error }
        } else {
            viewModel.startUpload(config)
        }
    }
}

// MARK: - 日期选择

struct DatePickerSheet: View {
    @Binding var date: Date
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            DatePicker("选择日期", selection: $date, displayedComponents: .date)
                .datePickerStyle(.graphical)
                .padding()
                .navigationTitle("结束日期")
                #if os(iOS)
                .navigationBarTitleDisplayMode(.inline)
                #endif
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("确定") { dismiss() }
                    }
                }
        }
    }
}
