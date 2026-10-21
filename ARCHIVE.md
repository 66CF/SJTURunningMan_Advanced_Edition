# 旧 Python / Qt 版归档

本分支是最后一版 Python + Qt 桌面客户端的**只读归档**：永久保留，不再维护、不再发布，也不要合并回 `master`。

- **快照提交**：`cfef7e7` —— 删除 `qtui.py` 的那次提交 `1371de8` 之前的最后一个提交
- **对应 tag**：`python-qtui-final`，指向本分支顶端；分支被误删时也能取回这份快照
- **本分支相对快照只多了两件事**：这份 `ARCHIVE.md`，以及 `README.md` 顶部的归档提示；代码与素材未作任何改动
- **归档内容**：`qtui.py`（Qt 界面入口）、`main.spec`（PyInstaller 配置，入口就是 `qtui.py`）、`src/` 全部业务模块、`utils/auxiliary_util.py`、`requirements.txt`、`assets/resources_rc.py` 与旧素材

## 为什么保留

这套实现是 Rust 桌面端的行为基线：登录与二次验证参数、图形验证码识别与提交间隔、轨迹生成、坐标换算、路线规划器 HTML 都是从它移植过去的。留一份完整快照便于比对旧版行为 —— 例如二次验证提交时的 `trust` 参数：旧版 `src/login.py:124` 发送 `trust=true`，Rust 迁移时写反成 `false`，导致每次登录都重新触发二次验证（修复见 #69）。

## 运行归档版本

```bash
python3 -m venv .venv
source .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r requirements.txt
python qtui.py
```

`requirements.txt` 是当时的版本约束，没有在新 Python 上重新验证过。界面依赖 PySide6；`src/info_dialog.py` 直接 `import assets.resources_rc`，该生成文件是内嵌资源的唯一来源，没有对应的 `.qrc` 源。

## 与现版的差异

- **版本号来源**：旧客户端读 `src/config.py` 的 `global_version`；现在的电脑版读 `desktop/Cargo.toml` 的 `version`。
- **登录会话**：旧客户端每次登录新建 `requests.Session()`，Cookie 不落盘；可信设备由服务端记录（提交二次验证码时发送 `trust=true`）。
- **请求头**：旧客户端用 `session.headers = {...}` 整体替换请求头，实际发出的 User-Agent 是 `python-urllib3/<版本>`。
- **发布**：现在的发布产物由 `release.yml` 用 Rust 构建，不再包含 Python；`ci-python.yml` 也不再检查这套代码，它只在 `master` 上触发，而 `master` 已没有 `qtui.py`。

## 不要做的事

- 不要在本分支上修 bug、加功能或升级依赖。
- 不要把本分支合并或 cherry-pick 回 `master`。
- 不要删除本分支；需要取回时用 tag `python-qtui-final`。
