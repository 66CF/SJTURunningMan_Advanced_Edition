# HarmonyOS NEXT 端使用指南

版本 **`4.2.0`**，Bundle Name `com.sjtu.runner`（`apps/harmonyos/AppScope/app.json5`）。工程路径 `apps/harmonyos/`。

> **未特别说明的内容与安卓端一致** —— 参数范围、上传流程、日志行为都对齐安卓版；差异仅在安装方式与登录入口。

鸿蒙版由 [@AetherWish](https://github.com/AetherWish) 贡献，工程由 Android 原生工程迁移而来（见 `apps/harmonyos/oh-package.json5`）。

![鸿蒙主界面](./images/harmonyos-main.webp)

## 目录

- [1. 安装 HAP 包](#1-安装-hap-包)
- [2. 登录](#2-登录)
- [3. 与其他端的差异](#3-与其他端的差异)

## 1. 安装 HAP 包

> ⚠️ **强烈建议提前完成华为账号的开发者认证**，以延长调试证书有效期。未认证时调试证书有效期很短，装完没多久就会失效需要重装。

1. 到 [Releases](https://github.com/JackyTJie/SJTURunningMan_Advanced_Edition/releases) 下载 HAP 文件；
2. 手机上：**设置 → 关于本机 → 连点「软件版本」7 次**，重启后启用开发者选项；
3. 进入 **设置 → 系统 → 开发者选项 → 无线调试**，打开并记录显示的 IP 与端口；
4. PC 与手机连同一网络，下载 [小白调试助手](https://github.com/likuai2010/auto-installer) 并运行，按软件内指引配置安装。

## 2. 登录

进入底部的**账号**一级页面，点击 **JAccount 账号**即可跳转到登录页。

> ⚠️ 鸿蒙端**无法拉起「交我办」快捷登录**，只能走 JAccount 网页登录。

## 3. 与其他端的差异

| 项 | 安卓 | 鸿蒙 |
| --- | --- | --- |
| 安装方式 | 直接装 APK | 需 HAP + 调试证书（有效期有限） |
| 快捷登录 | 支持交我办 | 不支持，仅 JAccount 网页登录 |
| 参数范围 | 1–30 次 / 1–5 km | 同安卓 |
| 路线资源 | `route_design.html`（百度地图） | 同安卓，另存一份 |

自行编译与打包见 [build.md](./build.md)；路线坐标系说明见 [routes.md](./routes.md)。
