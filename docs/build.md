# 从源码构建

四个平台各自独立构建，互不依赖。改哪个平台就构建哪个。

| 平台 | 目录 | 工具链 | 产物 |
| --- | --- | --- | --- |
| 电脑版 | 仓库根 | Python + PyInstaller | `.exe` / `.dmg` / `.deb` |
| 安卓 | `apps/android/` | Gradle 9.4.1 + AGP 8.13.0 + Kotlin 2.2.10 | `.apk` / `.aab` |
| iOS | `apps/ios/` | Xcode 16+ | `.ipa` |
| 鸿蒙 | `apps/harmonyos/` | DevEco Studio / hvigor | `.hap` |

---

## 电脑版

### 准备环境

```bash
python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

### 直接运行

```bash
python qtui.py
```

### 打包

**Windows**

```powershell
powershell -ExecutionPolicy Bypass -File .\build_windows.ps1
```

**全平台（等价命令）**

```bash
python -m PyInstaller --clean --noconfirm ./main.spec
```

`main.spec` 要点：

- 入口 `qtui.py`，`assets/` 整目录打进程序；
- 图标 Windows 用 `assets/SJTURM.ico`，macOS 用 `assets/SJTURM.icns`；
- macOS 产物是 `universal2` 架构（Intel + Apple Silicon 通用）；
- 版本号从环境变量 `APP_VERSION` 注入，本地缺省 `4.5.0`。

> ⚠️ 不要直接 `pyinstaller qtui.py` —— 那会丢失图标与 `assets/` 资源。

### 自动发布流水线

[`.github/workflows/release.yml`](../.github/workflows/release.yml) 在推送 tag（`4.5.0` 与 `v4.5.0` 两种写法都认）或手动 `workflow_dispatch` 时触发，并行构建五类产物 + 一份校验文件，合成草稿 Release：

| 产物 | 运行环境 |
| --- | --- |
| `SJTURunningMan_<ver>_windows.exe` | windows-latest |
| `SJTURunningMan_<ver>_macOS.dmg` | macos-latest（universal2） |
| `SJTURunningMan_<ver>_linux_amd64.deb` | ubuntu-24.04 |
| `SJTURunningMan_<ver>_linux_arm64.deb` | ubuntu-24.04-arm |
| `SJTURunningMan_<ver>_android.apk` | ubuntu-latest（自签名） |
| `SHA256SUMS.txt` | release 作业汇总生成 |

Python 版本固定 3.14.6。两个 deb 都构建在 Ubuntu 24.04 上 —— PySide6 的 aarch64 wheel 是 `manylinux_2_39`，要求 glibc ≥ 2.39，22.04 装不上。

**正确的发布顺序**：推 tag → 工作流构建 → 生成草稿并挂上全部产物 → 人工审核 → 点发布。不要反过来先手动建 release 再推 tag，`action-gh-release` 会去更新已存在的 release，而 `draft: true` 会把已发布的打回草稿。

#### 产物校验

每个构建作业都会用 `actions/attest-build-provenance` 给产物生成**来源证明**：证书由 GitHub OIDC 签发，绑定本次工作流运行与提交，**本机无法伪造**。用户侧可以验证：

```bash
# 需要 gh CLI 2.49 及以上
gh attestation verify SJTURunningMan_<ver>_windows.exe --repo JackyTJie/SJTURunningMan_Advanced_Edition
```

注意这只是**构建来源**的证明，不等于软件可信 —— 能触发这个工作流的人同样能签出有效证明。安卓 APK 另有自签名，是另一个层面的保证。

#### 权限

工作流全局只有 `contents: read`，写权限按作业提升：`release` 作业要 `contents: write`；五个构建作业各要 `id-token: write`（换 OIDC token）与 `attestations: write`（存证）。这样敏感的 OIDC 权限不会落在 `meta` 这类不需要它的作业上。

Release 正文由 `release` 作业动态生成，含各平台产物的下载链接与 SHA256 表格，GitHub 自动生成的提交列表追加在后面。

### 发布前需要配置的 Secrets

安卓产物是**自签名**的，签名材料必须放在仓库 Secrets 里，否则 `build-android` 作业会直接失败（不会退化成未签名包 —— 那种包装不上，发出去更糟）：

| Secret | 内容 |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | keystore 文件的 base64（**单行，不要换行**） |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 口令 |
| `ANDROID_KEY_ALIAS` | 密钥别名 |
| `ANDROID_KEY_PASSWORD` | 密钥口令 |

生成并配置：

```bash
keytool -genkeypair -v \
  -keystore release.jks -alias sjturunningman \
  -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12

base64 -i release.jks | tr -d '\n' | gh secret set ANDROID_KEYSTORE_BASE64
gh secret set ANDROID_KEYSTORE_PASSWORD
gh secret set ANDROID_KEY_ALIAS
gh secret set ANDROID_KEY_PASSWORD
```

> ⚠️ **务必留存 `release.jks` 与两个口令。** 换了 keystore 就等于换了签名，之后发的 APK 无法覆盖安装到已有用户的设备上，只能卸载重装。

---

## 安卓

```bash
cd apps/android

# 调试包
./gradlew assembleDebug

# 发布包（需配置签名）
./gradlew assembleRelease
```

产物在 `app/build/outputs/apk/`。

- 包名 `com.sjtu.runner`，`minSdk = 26`、`targetSdk = 34`、`compileSdk = 34`；
- 版本号在 `app/build.gradle.kts` 的 `versionCode` / `versionName`；
- 未配置签名时 `assembleRelease` 产出的是未签名包。签名需要 `keystore` 相关属性（见 `app/build.gradle.kts` 的 `signingConfigs`）。

> 依赖版本集中在 `gradle/libs.versions.toml`（版本目录），改版本优先改那里。

## iOS

```bash
cd apps/ios
open SJTURunningMan.xcodeproj
```

在 Xcode 中配置 Team ID 与 Bundle ID 后构建，步骤详见 [ios.md](./ios.md#3-配置签名)。

命令行检查工程与 scheme：

```bash
xcodebuild -project apps/ios/SJTURunningMan.xcodeproj -list
```

## 鸿蒙

用 **DevEco Studio** 打开 `apps/harmonyos/` 工程，选择合适的签名后构建 HAP。命令行可用 hvigor：

```bash
cd apps/harmonyos
hvigorw assembleHap
```

- Bundle Name `com.sjtu.runner`，版本在 `AppScope/app.json5`；
- 未做开发者认证时，调试证书有效期很短，安装前请先完成认证（见 [harmonyos.md](./harmonyos.md#1-安装-hap-包)）。

---

## 清理本地构建残留

构建产物、虚拟环境等已在 `.gitignore` 中，但会占本地磁盘（`.venv/` 约 1.2 GB、`dist/` 约 238 MB、`build/` 约 86 MB、`apps/android/app/build/` 约 107 MB）。清理前**先 dry-run 确认要删什么**：

```bash
git clean -Xdn     # 预览：只列被 .gitignore 忽略的文件
git clean -Xdf     # 执行删除（不可恢复，确认无误再跑）
```

`-X` 只删被忽略的文件，不会碰你的源码改动。
