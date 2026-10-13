# 更新日志

本文件记录各平台的版本变更。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

> **注意**：各平台的版本号互相独立，同一个版本号不代表功能对等。电脑版版本号见 `src/config.py`，安卓见 `apps/android/app/build.gradle.kts`，鸿蒙见 `apps/harmonyos/AppScope/app.json5`，iOS 见 `apps/ios/SJTURunningMan.xcodeproj/project.pbxproj`。

## 电脑版

### v4.5.0

#### 变更

- **版本号对齐** —— 电脑版由 `v4.4.0` 提升到 `v4.5.0`，与安卓、iOS 处于同一版号。本次为纯版本号变更，不包含功能改动。

### v4.4.0

#### 变更

- **移除客户端轨迹风险指数自检** —— 上传前不再做风险打分和高风险弹窗；上传流程收敛为「获取认证 → 生成并上传」两步。
- **路线预览上传前可用** —— 「预览路线」按钮按当前设置离线补点生成并在浏览器 OpenStreetMap 中预览，无需登录；上传中/后显示实际上传轨迹。
- **路线预览框分栏** —— 左侧摘要与按钮，右侧按所选路线坐标绘制等比轮廓小图（无地图瓦片，自动缩放）。
- **删除已导入路线** —— 「预设路线」下拉旁的「删除」按钮可移除 `Routes/` 中已导入的路线；内置 `default.txt` 与旧版 `user.txt` 受保护。
- **距离上限提升至 5km** —— 校验与提示文案同步更新，默认仍为 4km。
- **界面层级重调** —— 背景暗化为渐变遮罩、字体整体加大、留白收紧、用户名/密码同行、左右两列底边对齐。
- **参数校验** —— 跑步次数 1–30 条，时间 `HH:MM:SS`，日期 `YYYY-MM-DD`，距离最多 5km。
- **保留能力** —— 手动登录、异地登录二次验证、跑步时间随机化和路线规划功能保持可用。

### v4.3.x 及更早

历史版本变更请见仓库的 [Releases](https://github.com/JackyTJie/SJTURunningMan_Advanced_Edition/releases) 页面。

## 安卓

当前版本 `4.5.0`。详见 [Releases](https://github.com/JackyTJie/SJTURunningMan_Advanced_Edition/releases)。

## iOS

当前版本 `4.5.0`。此前 `MARKETING_VERSION` 为 `1.0`，与 `AppConfig.swift` 中已显示为 `4.5.0` 的 `versionName` 不一致，现已统一。

## 鸿蒙 NEXT

当前版本 `4.2.0`，由 Android 原生工程迁移而来。

---

## 关于本文件

- 新增条目请加在对应平台的**顶部**；
- 每条用「新增 / 变更 / 修复 / 移除」归类，一句说清**做了什么**，必要时补一句**为什么**；
- 详细的界面与参数说明不放这里，放对应的 `docs/` 文档。
