# 电脑版（Windows / macOS / Linux）

电脑版使用 **Rust + Slint** 原生界面，主入口是 `desktop/src/main.rs`。不依赖 Python、Qt 或 WebView；路线与规划器资源编译进可执行文件。旧版 `qtui.py` 与 `src/` 保留供迁移对照，默认构建和发布已切换到 Rust。

## 启动与使用

下载对应系统的安装包。Windows 直接运行 exe；macOS 将应用拖入 Applications；Linux 安装对应架构的 deb。首次启动必须阅读并勾选使用承诺书。

工作区分为「跑步工作台」「路线管理」「任务日志」「关于软件」。右上角可以切换深浅色外观。外观、跑步参数、路线选择保存在系统用户数据目录，不要求程序安装目录可写。

侧栏使用软件原图标与简洁的 `RunningMan` 字标，导航选中项以柔和背景区分。「关于软件」保留检查更新，并提供带图标的 GitHub 仓库和水源社区讨论帖入口；链接通过系统浏览器打开。

表单与下拉框统一使用紧凑的圆角控件。任务日志的说明位于标题下方，右侧独立提供「清空日志」；空日志显示占位提示，长日志可滚动查看。

1. 配置跑步次数（1–30）、单次距离（大于 0 且不超过 5 km）、结束日期（`YYYY-MM-DD`）与时间（`HH:MM:SS`）。默认日期为昨天，默认 1 次、4 km、08:00:00，按日期向前生成。
2. 选择内置 `default`，或通过「导入 TXT」添加 BD09 路线。文本为 UTF-8，每行 `longitude,latitude`，至少 2 个不同点。文件上限 2 MB、30,000 个点。导入保留独立副本；删除只影响导入副本，内置路线不能删除。
3. 点击「预览实际轨迹」，无需登录或网络即可生成并查看目标距离的轨迹。红色起终点之外的红色线段表示超过 150 m 的跳段。原始路线超过目标距离时自动截取；较短的开放路线往返，闭合路线循环。
4. 「在地图中打开」通过系统浏览器查看当前预览；尚未生成预览时显示原始路线。地图使用仅监听 `127.0.0.1` 的本机 HTTP 页面，**默认百度地图**，可在页面内切换 OpenStreetMap。在线底图需要网络，加载失败会提示；OpenStreetMap 需要 Leaflet，切到该引擎时才从 unpkg 加载。界面内的离线轨迹始终可用。刷新地图时需保持客户端运行；重启客户端后请从按钮重新打开地图。
5. 「设计路线」在浏览器打开路线规划器，**默认百度地图**，页面内可切换 OpenStreetMap；导出 TXT 后通过「导入 TXT」添加。`NeedsReview/` 隔离路线不参与内置列表。
6. 点击「登录账号」，填写 JAccount 账号和密码，图形验证码沿用旧版服务**自动识别**。识别服务异常时可刷新重试，或使用手动备用输入。按需完成交我办 / 邮箱 / 短信二次验证。识别请求仅发送验证码图片，不附带账号、密码或登录 Cookie；图片与识别结果不写磁盘。
7. 工作台底部提供进度与任务操作；其他页面仅在任务运行时显示状态及停止按钮。预览并检查参数后，点击「开始任务」并确认。任务在后台线程运行；日志与进度实时更新。实际上传生成的 payload 会更新界面预览。POST 失败不自动重试，避免重复记录。
8. 「停止任务」阻止后续上传；正在进行的 HTTP 请求最多等待 15 秒。任务中关闭窗口会先停止，等待当前请求结束后退出。

## 数据与隐私

账号、密码、验证码与 Cookie / token 只保存在进程内存中，退出账号或关闭程序后清除；日志只保留最近 250 行，不写磁盘。偏好设置不包含账号凭据。

用户数据目录：

- Windows：`%LOCALAPPDATA%\sjtu\SJTURunningMan\data`。
- macOS：`~/Library/Application Support/cn.sjtu.SJTURunningMan`。
- Linux：`${XDG_DATA_HOME:-~/.local/share}/sjturunningman`。

该目录包含 `settings.json` 与 `routes/`。地图 HTML 只保存在内存中，由按需启动的本机服务提供，不开放文件目录；服务随客户端退出而关闭。旧版程序目录中的 `Routes/`、`user.txt` 和 `ui_settings.json` 不会自动迁移；通过导入 TXT 迁移路线即可。

## 构建与验证

```bash
cargo run --locked -p sjturunningman
cargo test --workspace --locked
cargo build --release --locked
# 自动进行离线预览、参数校验，并截取各页面后退出
# Windows 文件名增加 .exe
./target/release/sjturunningman --smoke-test target/smoke
```

Windows 可运行 `./build_windows.ps1`，产物为 `dist/SJTURunningMan.exe`。三系统打包见 [build.md](build.md)。自动测试不会提交真实账号或运动记录；Windows 下另经授权实测了账号登录、交我办二次验证与校园跑只读授权，未测试上传。详见 [验证记录](desktop-validation.md) 与 [验收清单](testing.md)。

## 轻量化设计

仅启用 Slint `winit` 窗口后端与软件渲染；不携带 Qt、Skia、Chromium、Python 或整套背景资源。路线视图本地绘制，网络操作按用户动作触发。Release 使用体积优化、LTO 和符号裁剪。体积和启动耗时应以各系统的实际 Release 构建测量，不将 Debug 产物或旧版本下载包视为同条件基准。

轨迹采样保留原始拐点，避免跨弯道导致距离变短。当前实现保留旧桌面端的坐标校准、固定 200 m 距离补偿与 payload 字段，并重写了路线采样器；不是与 Python 逐位相同的随机轨迹。手机端未修改。

本轮构建、体积、启动计时与验证边界见 [迁移验证记录](desktop-validation.md)。
