# 测试指南

## 现状

| 平台 | 自动化测试 | 说明 |
| --- | --- | --- |
| 安卓 | ✅ 有 | 单元测试 + 仪器测试，见下 |
| 电脑版 | ❌ 无 | 目前只能手动验收 |
| iOS | ❌ 无 | 无测试 target |
| 鸿蒙 | ❌ 无 | — |
| CI | ❌ 不跑测试 | `release.yml` 只做打包 |

**补充测试是欢迎的** —— 新增测试的约定见 [CONTRIBUTING.md](../CONTRIBUTING.md)。

---

## 安卓

### 单元测试（不需要设备）

```bash
cd apps/android
./gradlew test
```

报告：`app/build/reports/tests/testDebugUnitTest/index.html`

现有用例：

| 文件 | 覆盖内容 |
| --- | --- |
| `app/src/test/java/com/sjtu/runner/data/DataGeneratorTest.kt` | 路线距离校正、截断模式达标、整圈模式、过长路线截断、配速钳制在规则范围内、payload 匹配上传 schema |
| `app/src/test/java/com/sjtu/runner/update/UpdateCheckerTest.kt` | 版本号解析（前缀 / 预发布）、新版本识别、旧版本与相等版本忽略 |

### 仪器测试（需要设备或模拟器）

```bash
cd apps/android
./gradlew connectedAndroidTest
```

用例：`app/src/androidTest/java/com/sjtu/runner/AppInstrumentedTest.kt`。

### 加一条测试

- 纯逻辑（数据生成、版本比较、坐标换算）→ 放 `app/src/test/`，用 JUnit 4；
- 需要 Context / UI 的 → 放 `app/src/androidTest/`，用 AndroidX JUnit + Espresso / Compose Test。

---

## 电脑版手动验收清单

改完电脑版代码，按下面逐项过一遍再提交：

**启动**

- [ ] `python qtui.py` 能起界面，无异常栈
- [ ] 承诺书弹窗出现，勾选后才能继续
- [ ] 背景图与主题正常（无花屏、无缺字）

**参数校验**

- [ ] 跑步次数输 `0` 或 `31` 被拒
- [ ] 时间输 `8:00`（非 `HH:MM:SS`）被拒
- [ ] 日期输 `2026/05/07` 被拒
- [ ] 距离输 `6` 被拒，输 `5` 通过

**路线**

- [ ] 「预设路线」下拉能选到 `default.txt`
- [ ] 「自定义...」能导入一个 txt，导入后出现在下拉里
- [ ] 重启程序后导入的路线仍在
- [ ] 滚轮划过下拉不会误切路线
- [ ] 「删除」能删掉导入的路线，但删不掉 `default.txt`

**预览**

- [ ] 「预览路线」在**未登录**状态下也能出图
- [ ] 浏览器打开的地图里有连线、绿起点、红终点
- [ ] 人为构造一条有 >150 m 跳段的路线，确认该段被标红

**上传**

- [ ] 1 次、2 km 的配置能跑完并显示成功
- [ ] 「停止任务」能中断，界面不卡死
- [ ] 日志区逐条输出，无未捕获异常

**打包**

- [ ] `python -m PyInstaller --clean --noconfirm ./main.spec` 成功
- [ ] 产物图标正确（不是 PyInstaller 默认图标）
- [ ] 产物能启动，`assets/` 资源正常加载

---

## iOS 手动验收清单

- [ ] 工程能在 Xcode 中无警告地编译
- [ ] 登录页能拉起 JAccount OAuth2
- [ ] 参数设置后点「开始任务」能获取 UID
- [ ] 日志实时滚动，逐条显示上传进度
- [ ] 会话过期时出现「重新登录」按钮且可用

---

## 提交前

```bash
# 安卓：确保单元测试通过
cd apps/android && ./gradlew test

# 通用：确认没有把构建产物或本地配置带进提交
git status
```

详见 [CONTRIBUTING.md](../CONTRIBUTING.md) 的 PR 自检清单。
