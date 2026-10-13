# AGENTS.md

给 AI 编码助手（Claude Code、Cursor、Copilot 等）的仓库地图。面向人类贡献者的规范见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 项目是什么

SJTU 一键校园跑工具 —— 用于研究运动数据记录与上传流程。**四个平台各自独立实现同一套业务逻辑，彼此没有代码复用。**

## 仓库布局

```
qtui.py                电脑版 GUI 入口（唯一入口，约 88 KB）
src/                   电脑版业务模块
  api_client.py        上传 / 认证 HTTP（含伪装 UA）
  data_generator.py    轨迹生成 + 内嵌路线规划器 HTML
  login.py             登录与验证码
  route_preview.py     本地预览生成
  config.py            全局版本号 global_version
  update_checker.py    更新检查
  info_dialog.py       说明弹窗（import assets.resources_rc）
utils/auxiliary_util.py
assets/                图标、背景、内置路线（assets/Routes/）
main.spec              PyInstaller 配置
build_windows.ps1      Windows 打包脚本
route_planner.html     电脑版路线规划器（Leaflet + OSM）
apps/android/          安卓（Kotlin + Compose）
apps/ios/              iOS（Swift + SwiftUI）
apps/harmonyos/        鸿蒙（ArkTS）
docs/                  用户文档（中文主 + en/ 镜像）
.github/workflows/release.yml   发布流水线
```

## 改动边界（重要）

1. **四个平台互不引用。** 全仓验证过：根目录 Python 代码、`release.yml`、`main.spec`、`build_windows.ps1` 都**不引用** `apps/` 下的任何路径。改一个平台不要顺手改另一个。
2. **`src/config.py` 的 `global_version` 只代表电脑版**，与移动端版本号无关。各平台版本号位置见 [CONTRIBUTING.md](CONTRIBUTING.md#版本号)。
3. **`assets/resources_rc.py` 是真实依赖**，不是可删的构建产物 —— `src/info_dialog.py` 直接 import 它，`main.spec` 也把它列进 hiddenimports。没有对应的 `.qrc` 源文件，这个生成文件就是唯一来源。
4. **`assets/Routes/NeedsReview/` 是隔离区。** 里面的路线有跳段 / 长度 / 起终点问题，不要直接把它们加回内置列表。
5. **路线坐标系**：路线文件存 BD09，OSM 瓦片是 WGS84，上传前由客户端转 GCJ02。见 [docs/routes.md](docs/routes.md)。
6. **内层路径随目录整体搬迁后仍成立**（Gradle、Xcode pbxproj、hvigor 都是相对引用）。移动 `apps/<平台>/` 只需同步 `.gitignore` 里的前缀规则。

## 四套实现的对应关系

同一份逻辑在每个平台各写一遍，按名字找对应文件：

| 职责 | 电脑版 | 安卓 | iOS | 鸿蒙 |
| --- | --- | --- | --- | --- |
| 轨迹生成 | `src/data_generator.py` | `data/DataGenerator.kt` | `DataGenerator.swift` | `service/DataGenerator.ets` |
| 网络层 | `src/api_client.py` | `network/ApiService.kt` | `ApiService.swift` | `service/ApiService.ets` |
| 路线管理 | `src/config.py` + `route_planner.html` | `route/RouteManager.kt` | `RouteManager.swift` | `service/RouteManager.ets` |
| 常量配置 | `src/config.py` | `config/AppConfig.kt` | `AppConfig.swift` | 分散在 `view/RunScreen.ets` |
| 登录 | `src/login.py` | `login/LoginActivity.kt` | `LoginView.swift` | `pages/Login.ets` |
| 坐标工具 | `src/data_generator.py` | `utils/GpsUtil.kt` | `GpsUtil.swift` | `service/GpsUtil.ets` |

安卓与 iOS 的参数常量基本一致：`maxRunTimes = 30`、`minDistanceKm = 1.0`、`maxDistanceKm = 5.0`、`intervalSeconds = 3`。

## 构建与测试命令

```bash
# 电脑版
python qtui.py
python -m PyInstaller --clean --noconfirm ./main.spec

# 安卓
cd apps/android && ./gradlew assembleDebug
cd apps/android && ./gradlew test                 # 单元测试（唯一有自动化测试的平台）

# iOS
xcodebuild -project apps/ios/SJTURunningMan.xcodeproj -list

# 鸿蒙
cd apps/harmonyos && hvigorw assembleHap
```

## 测试现状

只有安卓有自动化测试（`apps/android/app/src/test/` 的 `DataGeneratorTest`、`UpdateCheckerTest`，以及 `androidTest/` 的仪器测试）。电脑版 / iOS / 鸿蒙无测试，改动后按 [docs/testing.md](docs/testing.md) 的手动清单验收。CI 不跑测试。

## 修改约定

- 用户可见的行为变更 → 同步更新 `docs/` 下对应文档；中文改了 `docs/en/` 镜像也要改。
- 不要把构建产物（`build/`、`dist/`、`apps/android/app/build/`）或 `.venv/`、`.DS_Store`、IDE 配置提交进来。
- **不要把凭据写进代码、日志或测试夹具**：Cookie、密码、验证码端点、keystore。
- 不要引入超过 5 MB 的单个文件（历史上曾有一个 12.6 MB 的 GIF 彩蛋，已移除）。
