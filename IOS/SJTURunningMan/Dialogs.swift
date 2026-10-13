//
//  Dialogs.swift
//  SJTURunningMan
//
//  弹出对话框，行为与文案对齐安卓版 ui/components/Dialogs.kt。
//  采用居中玻璃卡片 + 半透明遮罩，呈现 iOS 26/27 Liquid Glass 外观。
//

import SwiftUI

// MARK: - 通用对话框容器

/// 居中玻璃卡片 + 半透明遮罩，观感对齐安卓版 AlertDialog。
struct DialogScrim<Content: View>: View {
    var onTapOutside: (() -> Void)?
    private let content: Content

    init(onTapOutside: (() -> Void)? = nil, @ViewBuilder content: () -> Content) {
        self.onTapOutside = onTapOutside
        self.content = content()
    }

    var body: some View {
        ZStack {
            Color.black.opacity(0.42)
                .ignoresSafeArea()
                .onTapGesture { onTapOutside?() }

            content
                .frame(maxWidth: 380)
                .padding(22)
                .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 26))
                .padding(24)
        }
        .transition(.opacity)
    }
}

// MARK: - 使用承诺书

/// 首次启动时的使用承诺书，勾选后方可进入。
struct CommitmentDialog: View {
    let onAccept: () -> Void
    let onDecline: () -> Void

    @State private var accepted = false

    var body: some View {
        DialogScrim(onTapOutside: onDecline) {
            VStack(alignment: .leading, spacing: 16) {
                Text("使用承诺书")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(AppTheme.onSurface)

                ScrollView {
                    Text(AppConfig.commitmentText)
                        .font(.callout)
                        .foregroundStyle(AppTheme.onSurfaceVariant)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .frame(maxHeight: 300)

                Button {
                    accepted.toggle()
                } label: {
                    HStack(alignment: .top, spacing: 10) {
                        Image(systemName: accepted ? "checkmark.square.fill" : "square")
                            .font(.title3)
                            .foregroundStyle(accepted ? AppTheme.primary : AppTheme.onSurfaceVariant)
                        Text("我已阅读以上内容，并承诺不会将本软件用于任何违规操作。")
                            .font(.callout)
                            .foregroundStyle(AppTheme.onSurface)
                            .multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)

                HStack(spacing: 12) {
                    Button(role: .destructive) {
                        onDecline()
                    } label: {
                        Text("退出").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glass)

                    Button {
                        onAccept()
                    } label: {
                        Text("我承诺，进入软件").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glassProminent)
                    .disabled(!accepted)
                }
            }
        }
    }
}

// MARK: - 路线距离提醒

/// 路线长度超过目标距离时征询处理方式，对齐安卓版 RouteConflictDialog。
struct RouteConflictDialog: View {
    let conflict: RouteConflict
    let onDecision: (RouteDecision) -> Void

    var body: some View {
        DialogScrim(onTapOutside: { onDecision(.cancel) }) {
            VStack(alignment: .leading, spacing: 16) {
                Text("路线距离提醒")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(AppTheme.onSurface)

                Text(message)
                    .font(.callout)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)

                VStack(spacing: 10) {
                    Button {
                        onDecision(.truncate)
                    } label: {
                        Text("削减路线").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.glassProminent)

                    HStack(spacing: 10) {
                        Button {
                            onDecision(.cancel)
                        } label: {
                            Text("取消任务").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.glass)

                        Button {
                            onDecision(.fullRoute)
                        } label: {
                            Text("完整路线").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.glass)
                    }
                }
            }
        }
    }

    private var message: String {
        let routeKm = String(format: "%.2f", conflict.routeDistanceM / 1000.0)
        let targetKm = String(format: "%.2f", conflict.targetDistanceM / 1000.0)
        return "当前路线「\(conflict.routeName)」长度为 \(routeKm)km，超过了您选择的 \(targetKm)km。\n\n"
            + "削减：自动截断路线至目标距离\n完整路线：按整条路线跑步并同步目标距离"
    }
}

// MARK: - 关于与检查更新

/// 关于对话框：版本、免责声明、贡献者、检查更新与相关链接。
struct AboutDialog: View {
    let onDismiss: () -> Void

    @State private var result: UpdateResult?
    @State private var checking = false

    var body: some View {
        DialogScrim(onTapOutside: onDismiss) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 10) {
                    Text("交我润")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(AppTheme.onSurface)
                    StatusPill(text: "v\(AppConfig.versionName)", color: AppTheme.primary)
                    Spacer(minLength: 0)
                }

                Text(AppConfig.disclaimerText)
                    .font(.footnote)
                    .foregroundStyle(AppTheme.onSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)

                Text(AppConfig.creditsText)
                    .font(.footnote)
                    .foregroundStyle(AppTheme.onSurface)

                if let result {
                    statusView(result)
                }

                HStack(spacing: 10) {
                    linkButton("仓库", "chevron.left.forwardslash.chevron.right", AppConfig.githubRepoURL)
                    linkButton("水源", "arrow.up.right.square", AppConfig.shuiyuanTopicURL)
                    if case .updateAvailable(_, let latest) = result {
                        linkButton("下载", "arrow.down.circle", latest.url)
                    }
                }

                HStack(spacing: 10) {
                    Button {
                        checking = true
                    } label: {
                        if checking {
                            HStack(spacing: 8) {
                                ProgressView().controlSize(.small)
                                Text("检查中…")
                            }
                        } else {
                            Text("检查更新")
                        }
                    }
                    .buttonStyle(.glass)
                    .disabled(checking)

                    Spacer(minLength: 0)

                    Button {
                        onDismiss()
                    } label: {
                        Text("知道了")
                    }
                    .buttonStyle(.glassProminent)
                }
            }
        }
        .task(id: checking) {
            guard checking else { return }
            result = await UpdateChecker.checkForUpdates()
            checking = false
        }
    }

    @ViewBuilder
    private func statusView(_ result: UpdateResult) -> some View {
        switch result {
        case .upToDate(_, let latestTag):
            Text("已是最新版本 \(latestTag)")
                .font(.footnote)
                .foregroundStyle(AppTheme.success)
        case .updateAvailable(_, let latest):
            Text("发现新版本 \(latest.tag)")
                .font(.footnote)
                .foregroundStyle(AppTheme.success)
        case .failed(let reason):
            Text("检查更新失败：\(reason)")
                .font(.footnote)
                .foregroundStyle(AppTheme.error)
        }
    }

    @ViewBuilder
    private func linkButton(_ title: String, _ systemImage: String, _ urlString: String) -> some View {
        if let url = URL(string: urlString), !urlString.isEmpty {
            Link(destination: url) {
                Label(title, systemImage: systemImage)
                    .font(.footnote.weight(.medium))
            }
            .buttonStyle(.glass)
        }
    }
}
