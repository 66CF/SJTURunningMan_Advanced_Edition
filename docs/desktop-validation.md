# Rust + Slint 桌面迁移验证

日期：2026-10-16。验证环境：Windows x86_64，Rust 1.95.0（MSYS2 GNU），Slint 1.15.1。

## 已验证

- `cargo fmt --all -- --check`、`cargo clippy --workspace --all-targets --locked -- -D warnings`。
- `cargo test --workspace --locked`：26 项离线 Rust 测试，另有 1 项按需执行的真实验证码测试。覆盖参数边界、路线解析/重复导入/持久化/受保护删除、坐标校准、连续采样与拐点、payload 时间顺序、版本比较、HTTP 认证/上传/失败脱敏、认证中途取消、/jaccount 路径作用域下的认证 Cookie，以及 AJAX 密码登录、跳转与二次验证分支。地图服务覆盖本机绑定、来源策略、Host / 路径隔离、页面更新、HEAD / 非 GET 处理与关闭释放端口。
- `python -m unittest discover -s scripts -p 'test_*.py'`：3 项打包布局测试。macOS plist、Applications 链接与签名/DMG 命令，以及 Linux deb 的 control/desktop 文件通过 mock 验证。
- `build_windows.ps1`：构建实际 Release exe，内嵌版本与图标。
- 将 exe 单独复制到隔离目录，工作目录没有 assets；将 PATH 限制为 Windows 系统目录后运行 `--smoke-test`。退出码为 0，离线预览和错误参数断言通过，生成 28 张截图，覆盖 4 页面 × 2 主题 × 2 窗口尺寸，以及弹窗、运行状态、空/长日志、长提示和下拉框。鼠标打开、键盘选择/确认、禁用选择器与清空日志断言通过。
- 检查 PE 导入表：只依赖 Windows 系统 DLL；没有 Python、Qt 或 MSYS2 DLL。
- 工作流 YAML 可解析；安卓构建作业及其后的发布作业与迁移前逐字一致，`apps/` 下无改动。

## 体积与启动

本次界面重构后的 Windows Release：**10,208,256 字节（9.735 MiB）**，产品版本 `4.5.0`，SHA256：`11c4934652c337823c14a684ef0f496c122a20d3fe343f52f1805662e9367c2e`。离线 GUI 检查退出码为 0，生成 28 张截图。此前登录修复阶段的实际 exe 曾通过匿名在线验证码检查，收到 110×40 图片并自动回填识别结果，账号实测单独记录于下文。初次迁移构建曾测得输入队列就绪 753 / 715 / 731 ms；这组数字为历史记录，本次界面重构未重新进行启动性能对照。

启动计时口径为 PowerShell `Start-Process` 到 `WaitForInputIdle`；不是跨机器基准，也不等于冷缓存首帧。`smoke-result.json` 中的窗口计时还包含人工设置的 300 ms 截图延迟，不能直接当作纯启动耗时。

没有构建旧 Qt 版本作同条件比较，因此不宣称具体的缩小百分比或启动加速倍数。体积减负来自移除 Python/Qt 运行时和旧背景资源，而非压缩整个旧包。

## 尚未验证

- Windows MSVC 构建和不同 Windows 系统版本。
- macOS Intel / Apple Silicon 的真实构建、签名、DMG 安装和运行；Linux amd64 / arm64、X11 / Wayland 的真实构建、deb 安装和文件选择器。
- 运动记录上传，以及邮箱 / 短信二次验证。账号登录、交我办二次验证与只读授权已单独实测，本地 HTTP mock 仍不能代替其他真实业务验收。
- 未测量测试覆盖率，不宣称达到 80%。

新增 `desktop-check.yml` 配置三系统编译、测试与静态检查；Windows/Linux 运行截图 smoke test。对应 CI 尚未实际触发。

## 迁移注意事项

- 登录保留旧版自动识别图形验证码；识别服务异常时才显示手动备用输入。
- 保留旧客户端坐标校准、固定 200 m 距离补偿、开始时间波动及 payload 字段；采样器重新实现，并保留拐点避免距离缩短。
- 长于目标的路线自动截取；旧 Qt 的「沿完整路线」选择不再出现。实际生成长度会显示在预览中。
- 旧路线需通过「导入 TXT」迁移到用户数据目录；旧 Python 模块 `src/`、`utils/` 和真实依赖 `assets/resources_rc.py` 仍保留（旧 Qt 入口已移除）。

## 验证码回归修复

匿名真实请求复现：不带 Referer 时验证码接口返回 HTTP 403；恢复旧版来源页头后返回 HTTP 200 和 110×40 JPEG。Rust 的真实验证码获取与自动识别测试已通过，未提交账号或密码。新增回归测试验证来源页头、JPEG 校验、multipart 格式、识别请求不带登录凭据和无效结果处理。

可手动执行 `cargo test --locked -p sjturunningman live_captcha_can_be_loaded_and_recognized -- --ignored`；该测试只获取匿名验证码并调用原识别服务。打包程序的 `--login-smoke-test <输出目录>` 验证正常界面回调已收到图片和自动识别结果，不提交登录、不保存真实验证码图片或识别内容。

## 账号登录回归修复

经用户授权使用真实账号复现：原请求回到登录页；按学校页面补齐 `lt=p` 与 AJAX 请求头，并跟随 `errno=0` 响应的下一跳后，可以正确进入后续认证。立即提交验证码曾被服务端拒绝；恢复旧版识别后 1 秒的提交间隔后进入二次验证页面。失败提示只显示受控分类，不回显服务端原文或凭据。

同次测试成功发送交我办验证码，使用用户提供的验证码完成二次验证，确认认证 Cookie，并通过校园跑只读授权与规则查询。调用与正式程序相同的 `Session` 实现；临时测试入口已删除。没有上传运动记录，没有将账号、密码、验证码、Cookie 或 token 写入文件。正式窗口仍需由用户完成一次交互验收；本次真实账号测试验证的是共享网络实现。

回归测试覆盖表单登录类型、AJAX / Referer 请求头、返回地址跳转、二次验证分支、验证码与密码错误分类、缺失或不可信跳转地址，以及失败响应脱敏。二次验证发送、提交与授权重定向均恢复来源页头，并在完成后检查认证 Cookie，避免仅凭 HTTP 200 显示成功。

## 品牌与关于页修复

侧栏复用窗口的软件原图标；此前的手写 SVG 字标已在本次视觉重构中替换为系统无衬线字体的 `RunningMan`。「关于软件」提供旧版水源社区讨论帖与 GitHub 入口，并复用已有图标；暗色主题使用相应的图标颜色。链接沿用旧版的目标地址，通过系统浏览器打开。检查更新提示仍说明从 GitHub 的 Releases 下载。

实际 Release 截图检查覆盖字标、原图标、链接图标、按钮垂直对齐及关于页在最小窗口下的深浅色显示。仅检查链接绑定与目标地址，没有代替用户登录水源社区。macOS / Linux 的视觉表现仍需真机验收。

## 浏览器地图 403 修复

原实现从 `file://` 打开地图，浏览器瓦片请求缺少 HTTP Referer，规划器还设置了 `no-referrer`；与 [OSM 瓦片使用要求](https://operations.osmfoundation.org/policies/tiles/) 冲突。现按需启动仅绑定 `127.0.0.1` 的内存页面服务，使用每次运行独立的页面路径，设置 `strict-origin-when-cross-origin`；预览与规划器均使用官方 `https://tile.openstreetmap.org/{z}/{x}/{y}.png`。不代理瓦片、不伪造来源、不批量预取；浏览器正常处理服务器缓存头。

实际 Release 的 `--map-smoke-test` 经正式界面回调启动，Chrome 可见窗口验证：预览与规划器均显示底图，采样瓦片为 HTTP 200，实际 Referer 为本机 HTTP origin，版权链接可见。规划器选择 2 点后成功导出有效 BD09 TXT。另在隔离浏览器中拦截瓦片模拟 HTTP 403，两页均显示失败提示，预览保留轨迹；模拟用例没有向 OSM 发送瓦片请求。截图与导出位于本地忽略目录 `output/playwright/`。未登录账号、未上传记录；macOS / Linux 本轮未实测。

## 界面降噪与布局重构

移除页面宣传语、全大写分组与装饰编号；顶部改为 22 px 中文标题，Header 行收敛为 32 px。侧栏导航为 40 px，使用柔和选中背景；关于页改为应用信息、更新与社区、使用说明三张紧凑卡片。保留森林绿主题、原图标和社区链接。

共享表单与下拉选择器统一为 38 px / 1 px 柔和边框 / 8 px 圆角，工具栏 34 px、次级按钮 36 px；清空日志为自适应宽度的 32 px 按钮。日志说明移到卡片标题下方，正文左上对齐，空日志显示占位。计划卡片按内容高度排列，底部提示与操作固定间距。

任务栏仅在工作台常驻，进度轨道为 6 px 并从左端填充；其他页面仅在运行中显示紧凑状态与停止按钮。长通知随文字换行调整高度。Release 的 28 张截图由实际软件渲染生成，人工检查了上述页面矩阵、长文本与弹窗；仅使用离线状态，不调用账号登录或上传。截图与 smoke 断言保存在本地忽略目录 `target/ui-modernization/release/`。macOS / Linux 视觉表现仍待真机验证。

## macOS 中文渲染修复

新增的 macOS 运行验收（`desktop-check.yml` 在打包后实跑 `--smoke-test`，见下节）第一次拿到 macOS 上的实际截图，发现**界面里所有汉字都不渲染**：同一版本的 Windows / Linux 截图正常，macOS 只剩数字和拉丁字母 —— 例如「跑步次数 / 次」只剩「 / 」，「单次距离 / km」只剩「 / km」，承诺书弹窗标题与 4 条正文全空白、只剩序号 `1.`–`4.`，而 `RunningMan`、`v4.5.0`、`BD09` 这类拉丁文字正常。

像素检查显示汉字的推进宽度为 0（斜杠紧贴标签左端），即所选字体根本没有这些字形，系统 fallback 也没有补上：界面字体此前只按**名字**指定（macOS 为 `PingFang SC`），该名字在无人会话环境里解析不到，Slint 便退回一个只含拉丁字形的字体。

修法：启动时按「家族名 + 系统字体文件路径」候选列表读取字体文件，用 `slint::fontique_07::shared_collection()` 注册进 Slint 自己的字体库，把第一个注册成功的家族设为界面字体，并追加为汉字（`Hani`）的 fallback。候选：macOS `PingFang.ttc` / `Hiragino Sans GB.ttc` / `STHeiti Medium.ttc` / `Arial Unicode.ttf`，Windows `msyh.ttc` / `simhei.ttf`，Linux Noto Sans CJK / WenQuanYi Zen Hei；全部不可用时回退到原来的系统字体名。注册与 fallback 结果写进 `smoke-result.json` 的 `ui_font` 与 `font_candidates`，便于排查同类问题。该改动同时消除了对系统字体枚举行为的依赖，Windows / Linux 未发现视觉回归。

当时 `slint` 因此启用了 `unstable-fontique-07`（Slint 官方提供的运行时字体注册接口，见 `fontique_07` 模块文档）。真机 macOS 仍需人工确认一次中文显示。

## macOS 标题栏修复（2026-10-17）

项目原先锁定 Slint 1.15.1。该版本在 macOS 上可能使原生标题栏（包括红绿灯区域）保持空白或透明；界面文件中的不透明窗口背景不能修复系统标题栏本身。桌面版已升级到 Slint 1.18.0，并将字体注册接口同步为 `unstable-fontique-011` / `fontique_011`。Slint 1.18.0 的 macOS 修复覆盖原生标题栏的透明判定，见 [Slint 1.18.0 release notes](https://github.com/slint-ui/slint/blob/v1.18.0/CHANGELOG.md)。

本次已用临时 Rust 1.99.1 工具链完成 `cargo check --workspace --locked` 与 `cargo test --workspace --locked`：28 项测试通过，1 项真实验证码测试忽略；macOS 字体测试因 winit 要求测试运行在主线程而忽略。尚未启动 macOS Release 窗口，因此红绿灯区域仍需在实际应用窗口中人工验收。

## 百度 / OpenStreetMap 双引擎（2026-10-17）

用户实测 OSM 在当前网络不可用，而 Rust 版把旧 Qt 的百度底图删掉了。本轮把 provider 选择放回 HTML 页面：预览页与规划器均**默认百度地图**，页面内可切 OpenStreetMap，不改 Slint 应用界面。坐标契约不变：规划器统一存 BD09，百度点击直接入库，OSM 点击 WGS84 → BD09；预览页收到 GCJ02，显示时转百度 BD09 或 OSM WGS84。

本机 macOS 27 arm64、Rust 1.99.1、Release 构建，`--map-smoke-test` 生成两页后用 Headless Chrome 154 + CDP 实测：

- 两页默认百度均显示「百度地图已加载」，底图瓦片正常；百度 AK 接受本机 loopback 页面。规划器点击地图后 `coordinates` 得到 BD09 坐标（中心附近 `121.442938, 31.031568`）。
- 切到 OSM 后采集点保留（切换前后均为 2 点），Leaflet 从 unpkg 按需加载成功；当前网络下 OSM 瓦片返回 `ERR_CONNECTION_RESET`，页面按预期显示底图失败提示，轨迹与采集仍可用。这与用户报告的 OSM 不可用一致，也说明百度默认值的必要性。
- OSM 模式下点击同样写入 BD09（`121.444655, 31.030156`），与百度模式坐标范围一致。来回切换 provider 后地图重建、标记数量正确。
- 规划的标记删除验证通过：百度模式下点击标记后点数从 1 变 0，不会再被冒泡的 map click 补回；随后点击地图恢复为 1。OSM 模式标记删除同样为 1 → 0 → 1。
- Baidu BMap 初始化需要 `'unsafe-eval'`。CSP 已在 `script-src` 放行它，并限定百度域名（`api.map.baidu.com`、`*.baidu.com`、`*.bdimg.com`、`*.bdstatic.com`，http/https）；OSM/unpkg 源保持原样，表单、base-uri 与 frame-ancestors 仍关闭。
- 百度 loader 带 `referrerpolicy="no-referrer"`，页面本身仍保留 `strict-origin-when-cross-origin` 供 OSM 使用。

尚未验收：Windows / Linux 真机上的百度与 OSM 渲染、Baidu AK 的其他网络环境、真实上传。`--map-smoke-test` 只证明页面可启动与坐标契约，地图渲染由上述浏览器检查覆盖。

## 学期统计与学期日历（2026-10-21）

工作台新增只读「本学期进度」卡片（达标公里、完成率），「任务日志」改为左侧学期日历 + 右侧运行日志。改动只在 `desktop/`，移动端与旧 Python 模块未变。

- 统计由五条固定顺序的只读请求组成：`GET /sports/my/data` → `GET /sports/my/semesterTarget` → `GET /sports/running/semester/list?type=1` → `POST /sports/ranking/getUserInfo`（空 JSON body）→ `GET /sports/running/result/list?selectId=<semesterId>&type=1`。这些接口只需要 `pe.sjtu.edu.cn` 的会话 Cookie，不需要 `/sports/my/uid` 换取的 token。跳转到 `/login`、返回非 JSON、或 `code != 0` 一律识别为「登录状态已过期」，错误信息只带受控分类，不带响应正文。学期列表缺少 `semesterId` 时直接失败，不再请求记录列表。
- 纯逻辑放在新模块 `desktop/src/stats.rs`：学期区间防御（`end < start` 或跨度超过 400 天返回空）、周一起始的按月日历网格、月首尾空白格、同日多记录时「计入成绩」优先于无效记录、`--` 与一位小数的百分比格式化。网络解析在 `Session::semester_stats`。界面用扁平行模型加 7 个显式 `DayCell`，避开 Slint 在重复行内再嵌 `if` / `for` 的限制。
- 刷新时机：登录成功、二次验证成功、任务有成功上传之后。每次登录或刷新递增会话序号，换账号或退出登录后到达的迟到响应直接丢弃；同步失败只写一行日志，登录状态不变。
- 自动化：`cargo test --workspace --locked` 为 44 项通过、1 项真实验证码测试忽略（另有 2 项二进制测试），新增覆盖请求顺序、登录重定向、`code != 0`、缺失 `semesterId`、同日有效+无效、缺失 `percent` 时的推导，以及日历跨月/跨年、补齐空白格、区间外日期与占位区间。`cargo fmt --all -- --check` 与 `cargo clippy --workspace --all-targets --locked -- -D warnings` 在 Rust 1.99.1 下干净。
- `--smoke-test` 新增两个确定性夹具：已登录状态下的首页统计数值，以及固定学期范围（2026-09-14 起）的日历，含计入成绩、无效记录与落在区间外的日期。截图从 28 张增加到 30 张，`snapshot_count` 改为按输出目录内实际 PNG 数量推导，不再沿用硬编码值；报告另记 `semester_stats_placeholder` 与 `semester_stats_fixture_rows`。本次构建的像素统计（浅色主题、1493×1093 窗口、日历配色每通道容差 ±3、强调色 ±6）确认第 30 张截图同时出现三种日历配色：计入成绩 2609 px、无效记录 548 px、未跑 33787 px；第 29 张首页截图的强调色 `#236849` 共 14741 px、左半区 4232 px，而占位截图 `02-screen.png` 为 10611 px、左半区仅 134 px，说明统计卡片确实渲染了夹具数值。夹具数值与日期均为本地常量，测试不登录、不上传。
- 尚未验证：真实账号登录后的达标公里与完成率数值、无效记录在真实数据下的颜色与日期归属、macOS / Linux 真机上卡片与两栏布局的视觉表现，以及 1040×700 最小窗口下两栏是否溢出。上一节的 28 张截图记录属于此前的构建，保持不变。
