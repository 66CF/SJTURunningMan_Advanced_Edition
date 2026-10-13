//
//  Components.swift
//  SJTURunningMan
//
//  可复用 UI 组件，结构对齐安卓版 ui/components/。
//  卡片与按钮使用 Liquid Glass，适配 iOS 26/27 的新外观。
//

import SwiftUI

// MARK: - 卡片

/// 统一的分区卡片：可选标题行（图标 + 标题 + 副标题 + 尾部操作）与内容区。
struct SectionCard<Content: View, Action: View>: View {
    private let title: String?
    private let icon: String?
    private let supporting: String?
    private let elevated: Bool
    private let action: (() -> Action)?
    private let content: () -> Content

    init(
        title: String? = nil,
        icon: String? = "info.circle",
        supporting: String? = nil,
        elevated: Bool = false,
        @ViewBuilder action: @escaping () -> Action,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.title = title
        self.icon = icon
        self.supporting = supporting
        self.elevated = elevated
        self.action = action
        self.content = content
    }

    init(
        title: String? = nil,
        icon: String? = "info.circle",
        supporting: String? = nil,
        elevated: Bool = false,
        @ViewBuilder content: @escaping () -> Content
    ) where Action == EmptyView {
        self.init(
            title: title,
            icon: icon,
            supporting: supporting,
            elevated: elevated,
            action: { EmptyView() },
            content: content
        )
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let title {
                HStack(spacing: 10) {
                    if let icon {
                        Image(systemName: icon)
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(AppTheme.primary)
                            .frame(width: 20)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(title)
                            .font(.headline)
                            .foregroundStyle(AppTheme.onSurface)
                        if let supporting {
                            Text(supporting)
                                .font(.caption)
                                .foregroundStyle(AppTheme.onSurfaceVariant)
                        }
                    }
                    Spacer(minLength: 0)
                    if let action {
                        action()
                    }
                }
            }
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 18)
        .padding(.top, title == nil ? 18 : 16)
        .padding(.bottom, 18)
        .glassEffect(.regular, in: RoundedRectangle(cornerRadius: AppTheme.sectionCornerRadius))
        .shadow(color: .black.opacity(elevated ? 0.16 : 0), radius: elevated ? 12 : 0, y: elevated ? 5 : 0)
    }
}

/// 卡片内部的分隔线。
struct SectionDivider: View {
    var body: some View {
        Rectangle()
            .fill(AppTheme.outlineVariant)
            .frame(maxWidth: .infinity)
            .frame(height: 1)
    }
}

// MARK: - 状态徽标

struct StatusPill: View {
    let text: String
    let color: Color

    var body: some View {
        Text(text)
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(color.opacity(0.16), in: RoundedRectangle(cornerRadius: 8))
            .frame(height: 24)
            .fixedSize()
    }
}

// MARK: - 状态卡片

struct StatItem: Identifiable {
    let id = UUID()
    let label: String
    let value: String

    init(_ label: String, _ value: String) {
        self.label = label
        self.value = value
    }
}

struct StatusCard: View {
    let progress: TaskProgress
    let headline: String
    let stats: [StatItem]

    var body: some View {
        SectionCard(elevated: true) {
            HStack(spacing: 12) {
                if progress.status.isActive {
                    ProgressView()
                        .controlSize(.regular)
                        .tint(AppTheme.primary)
                        .frame(width: 28, height: 28)
                }
                VStack(alignment: .leading, spacing: 2) {
                    Text(headline)
                        .font(.title2.weight(.semibold))
                        .foregroundStyle(AppTheme.onSurface)
                    Text(progress.status.label)
                        .font(.caption)
                        .foregroundStyle(AppTheme.onSurfaceVariant)
                }
                Spacer(minLength: 0)
                if progress.total > 0 {
                    Text("\(progress.current)/\(progress.total)")
                        .font(.headline)
                        .foregroundStyle(AppTheme.primary)
                }
            }

            if progress.total > 0 {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(AppTheme.cardLow)
                        Capsule()
                            .fill(AppTheme.primary)
                            .frame(width: max(0, geo.size.width * progress.fraction))
                    }
                }
                .frame(height: 8)
            }

            HStack(spacing: 10) {
                ForEach(stats) { item in
                    StatCell(label: item.label, value: item.value)
                }
            }
        }
    }
}

struct StatCell: View {
    let label: String
    let value: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label)
                .font(.caption2)
                .foregroundStyle(AppTheme.onSurfaceVariant)
            Text(value)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(AppTheme.onSurface)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(AppTheme.cardLow, in: RoundedRectangle(cornerRadius: AppTheme.sectionCornerRadius))
    }
}

// MARK: - 控制台日志

struct ConsoleLog: View {
    let entries: [LogEntry]
    var placeholder: String = "就绪。点击「开始任务」启动。"

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                if entries.isEmpty {
                    Text(placeholder)
                        .font(AppTheme.consoleFont)
                        .foregroundStyle(AppTheme.consoleOn.opacity(0.55))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(14)
                } else {
                    VStack(alignment: .leading, spacing: 3) {
                        ForEach(entries) { entry in
                            HStack(alignment: .top, spacing: 8) {
                                Text(entry.time)
                                    .font(AppTheme.consoleFont)
                                    .foregroundStyle(AppTheme.consoleOn.opacity(0.45))
                                Text(entry.message)
                                    .font(AppTheme.consoleFont)
                                    .foregroundStyle(color(for: entry.level))
                                    .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .id(entry.id)
                        }
                    }
                    .padding(14)
                }
            }
            .onChange(of: entries.count) { _, _ in
                guard let last = entries.last else { return }
                withAnimation(.easeOut(duration: 0.2)) {
                    proxy.scrollTo(last.id, anchor: .bottom)
                }
            }
        }
        .background(AppTheme.console)
        .clipShape(RoundedRectangle(cornerRadius: AppTheme.panelCornerRadius))
        .overlay(
            RoundedRectangle(cornerRadius: AppTheme.panelCornerRadius)
                .stroke(AppTheme.outlineStrong.opacity(0.35), lineWidth: 1)
        )
    }

    private func color(for level: LogLevel) -> Color {
        switch level {
        case .info: return AppTheme.consoleOn
        case .success: return AppTheme.success
        case .warn: return AppTheme.warning
        case .error: return AppTheme.error
        }
    }
}

// MARK: - 流式布局（用于标签换行）

struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var totalHeight: CGFloat = 0
        var rowWidth: CGFloat = 0
        var rowHeight: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            let needed = rowWidth == 0 ? size.width : rowWidth + spacing + size.width
            if needed > maxWidth, rowWidth > 0 {
                totalHeight += rowHeight + spacing
                rowWidth = size.width
                rowHeight = size.height
            } else {
                rowWidth = needed
                rowHeight = max(rowHeight, size.height)
            }
        }
        totalHeight += rowHeight
        return CGSize(width: maxWidth == .infinity ? rowWidth : maxWidth, height: totalHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout Void) {
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0
        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX, x > bounds.minX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            subview.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

// MARK: - 选择控件

struct ChoiceChips: View {
    let options: [String]
    let selected: String
    let onSelect: (String) -> Void

    var body: some View {
        FlowLayout(spacing: 8) {
            ForEach(options, id: \.self) { option in
                let isSelected = option == selected
                Button {
                    onSelect(option)
                } label: {
                    Text(option)
                        .font(.subheadline.weight(isSelected ? .semibold : .regular))
                        .foregroundStyle(isSelected ? AppTheme.primary : AppTheme.onSurface)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(
                            isSelected ? AppTheme.primary.opacity(0.14) : Color.clear,
                            in: RoundedRectangle(cornerRadius: AppTheme.sectionCornerRadius)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: AppTheme.sectionCornerRadius)
                                .stroke(isSelected ? AppTheme.primary : AppTheme.outlineVariant, lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
            }
        }
    }
}

struct NumberField: View {
    let value: String
    var label: String?
    var width: CGFloat = 64
    var maxLength: Int = 3
    let onChange: (String) -> Void

    @State private var text: String = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if let label {
                Text(label)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
            }
            TextField("", text: $text)
                .keyboardType(.numberPad)
                .multilineTextAlignment(.center)
                .font(.body.weight(.medium))
                .foregroundStyle(AppTheme.onSurface)
                .padding(.horizontal, 8)
                .padding(.vertical, 8)
                .frame(width: width)
                .background(AppTheme.cardLow, in: RoundedRectangle(cornerRadius: AppTheme.panelCornerRadius))
                .overlay(
                    RoundedRectangle(cornerRadius: AppTheme.panelCornerRadius)
                        .stroke(AppTheme.outlineVariant, lineWidth: 1)
                )
        }
        .onAppear { text = value }
        .onChange(of: text) { _, newValue in
            let filtered = String(newValue.filter(\.isNumber).prefix(maxLength))
            if filtered != newValue {
                text = filtered
            }
            onChange(filtered)
        }
        .onChange(of: value) { _, newValue in
            if newValue != text {
                text = newValue
            }
        }
    }
}

struct TimeField: View {
    let hour: Int
    let minute: Int
    let second: Int
    let onChange: (Int, Int, Int) -> Void

    var body: some View {
        HStack(spacing: 6) {
            NumberField(value: String(format: "%02d", hour), label: "时", width: 62, maxLength: 2) { value in
                onChange(clamp(value, fallback: hour, upper: 23), minute, second)
            }
            separator
            NumberField(value: String(format: "%02d", minute), label: "分", width: 62, maxLength: 2) { value in
                onChange(hour, clamp(value, fallback: minute, upper: 59), second)
            }
            separator
            NumberField(value: String(format: "%02d", second), label: "秒", width: 62, maxLength: 2) { value in
                onChange(hour, minute, clamp(value, fallback: second, upper: 59))
            }
        }
    }

    private var separator: some View {
        Text(":")
            .font(.headline)
            .foregroundStyle(AppTheme.onSurfaceVariant)
    }

    private func clamp(_ text: String, fallback: Int, upper: Int) -> Int {
        guard let parsed = Int(text) else { return fallback }
        return min(max(parsed, 0), upper)
    }
}

struct DistanceSlider: View {
    let distanceKm: Double
    let range: ClosedRange<Double>
    let onDistanceChange: (Double) -> Void

    var body: some View {
        VStack(spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text(String(format: "%.1f", distanceKm))
                    .font(.title2.weight(.bold))
                    .foregroundStyle(AppTheme.primary)
                Text("km")
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
                Spacer()
                Text("上限 \(Int(range.upperBound))km")
                    .font(.caption2)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
            }
            Slider(
                value: Binding(
                    get: { min(max(distanceKm, range.lowerBound), range.upperBound) },
                    set: { onDistanceChange(($0 * 10).rounded() / 10) }
                ),
                in: range,
                step: 0.1
            )
            .tint(AppTheme.primary)
        }
    }
}

struct RouteOption: Identifiable {
    let id: String
    let title: String
    let subtitle: String?
    let selected: Bool

    init(id: String, title: String, subtitle: String? = nil, selected: Bool = false) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.selected = selected
    }
}

struct MenuDropdown: View {
    let value: String
    var label: String?
    let options: [RouteOption]
    let onSelect: (RouteOption) -> Void

    var body: some View {
        Menu {
            ForEach(options) { option in
                Button {
                    onSelect(option)
                } label: {
                    if let subtitle = option.subtitle {
                        Text("\(option.title)（\(subtitle)）")
                    } else {
                        Text(option.title)
                    }
                }
            }
        } label: {
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    if let label {
                        Text(label)
                            .font(.caption2)
                            .foregroundStyle(AppTheme.onSurfaceVariant)
                    }
                    Text(value)
                        .font(.body.weight(.medium))
                        .foregroundStyle(AppTheme.onSurface)
                        .lineLimit(1)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.up.chevron.down")
                    .font(.caption)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .frame(maxWidth: .infinity)
            .background(AppTheme.cardLow, in: RoundedRectangle(cornerRadius: AppTheme.panelCornerRadius))
            .overlay(
                RoundedRectangle(cornerRadius: AppTheme.panelCornerRadius)
                    .stroke(AppTheme.outlineVariant, lineWidth: 1)
            )
        }
    }
}

// MARK: - 路线预览画布

/// 按路线坐标绘制等比轮廓，跳段用红色高亮，对齐安卓版 RoutePreviewCanvas。
struct RoutePreviewCanvas: View {
    let samples: [TrackSample]
    var jumpCount: Int = 0

    var body: some View {
        ZStack {
            if samples.count < 2 {
                Text("暂无可预览路线")
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(AppTheme.onSurfaceVariant)
            } else {
                Canvas { context, size in
                    draw(in: &context, size: size)
                }
            }
        }
    }

    private func draw(in context: inout GraphicsContext, size: CGSize) {
        let lons = samples.map(\.longitude)
        let lats = samples.map(\.latitude)
        guard let minLon = lons.min(), let maxLon = lons.max(),
              let minLat = lats.min(), let maxLat = lats.max() else { return }

        let spanLon = max(maxLon - minLon, 1e-6)
        let spanLat = max(maxLat - minLat, 1e-6)
        // 纬度方向按 cos(lat) 压缩，保证形状比例正确
        let latScale = cos((minLat + maxLat) / 2 * .pi / 180)

        let inset: Double = 12
        let availableW = Double(size.width) - inset * 2
        let availableH = Double(size.height) - inset * 2
        let scale = min(availableW / spanLon, availableH / (spanLat * latScale))

        let drawnW = spanLon * scale
        let drawnH = spanLat * latScale * scale
        let offsetX = (Double(size.width) - drawnW) / 2
        let offsetY = (Double(size.height) - drawnH) / 2

        func point(_ sample: TrackSample) -> CGPoint {
            CGPoint(
                x: offsetX + (sample.longitude - minLon) * scale,
                y: offsetY + (maxLat - sample.latitude) * scale * latScale
            )
        }

        var path = Path()
        path.move(to: point(samples[0]))
        for sample in samples.dropFirst() {
            path.addLine(to: point(sample))
        }
        context.stroke(
            path,
            with: .color(AppTheme.primary.opacity(0.85)),
            style: StrokeStyle(lineWidth: 2.5, lineCap: .round, dash: [6, 3])
        )

        if jumpCount > 0 {
            for i in 0..<(samples.count - 1) {
                let a = samples[i]
                let b = samples[i + 1]
                let distance = GpsUtil.haversineDistance(
                    lat1: a.latitude, lon1: a.longitude,
                    lat2: b.latitude, lon2: b.longitude
                )
                if distance > DataGenerator.jumpThresholdM {
                    var segment = Path()
                    segment.move(to: point(a))
                    segment.addLine(to: point(b))
                    context.stroke(
                        segment,
                        with: .color(AppTheme.error.opacity(0.9)),
                        style: StrokeStyle(lineWidth: 3, lineCap: .round)
                    )
                }
            }
        }

        let start = point(samples[0])
        let end = point(samples[samples.count - 1])
        let ring = { (center: CGPoint) in
            Path(ellipseIn: CGRect(x: center.x - 9, y: center.y - 9, width: 18, height: 18))
        }
        let dot = { (center: CGPoint) in
            Path(ellipseIn: CGRect(x: center.x - 6, y: center.y - 6, width: 12, height: 12))
        }
        context.stroke(ring(start), with: .color(AppTheme.outlineVariant.opacity(0.6)), lineWidth: 1)
        context.stroke(ring(end), with: .color(AppTheme.outlineVariant.opacity(0.6)), lineWidth: 1)
        context.fill(dot(start), with: .color(AppTheme.success))
        context.fill(dot(end), with: .color(AppTheme.tertiary))
    }
}

// MARK: - 底部免责声明

struct DisclaimerNotice: View {
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 16))
                .foregroundStyle(AppTheme.warning)
            Text(AppConfig.disclaimerShortText)
                .font(.footnote)
                .foregroundStyle(AppTheme.onSurface)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(AppTheme.warning.opacity(0.12), in: RoundedRectangle(cornerRadius: AppTheme.panelCornerRadius))
    }
}
