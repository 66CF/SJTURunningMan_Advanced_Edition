# 安卓版使用指南

版本 **`4.5.0`**（`apps/android/app/build.gradle.kts` 的 `versionName`，`versionCode = 40500`）。工程路径 `apps/android/`。

![安卓主界面](./images/android-main.png)

## 目录

- [1. 安装](#1-安装)
- [2. 登录](#2-登录)
- [3. 参数配置](#3-参数配置)
- [4. 开始上传](#4-开始上传)
- [5. 自定义路线](#5-自定义路线)
- [6. 权限说明](#6-权限说明)

## 1. 安装

1. 到 [Releases](https://github.com/JackyTJie/SJTURunningMan_Advanced_Edition/releases) 下载 APK；
2. 把 APK 传到手机上（数据线、微信文件传输、网盘均可）；
3. 点击安装。系统可能提示「未知来源应用」——在提示中允许当前来源安装即可。

> 系统要求：Android 8.0（API 26）及以上。`minSdk = 26`、`targetSdk = 34`。

## 2. 登录

1. 应用启动后自动打开 JAccount 登录页获取 Cookie，可手动登录，也可走「交我办」一键登录；
2. 应用会自动检测 Cookie 有效性。失效时自动重新登录；
3. 若反复失败，去系统设置里**清除应用数据**后重来。

## 3. 参数配置

| 参数 | 范围 | 默认 | 说明 |
| --- | --- | --- | --- |
| 上传次数 | 1–30 次 | 1 | 生成多少条记录。首次建议选 1 次先验证可用性 |
| 跑步时间 | 06:00–23:00 | 08:00 | 每条会加随机波动 |
| 结束日期 | 日期选择器 | 昨天 | 以此为最新一条向前生成 |
| 跑步距离 | 1–5 km | 4 km | 单条目标距离 |
| 自定义路线 | 见下 | 思源湖浪漫跑步路线 | 可在新页面连点设置 |

参数范围来自 `apps/android/app/src/main/java/com/sjtu/runner/config/AppConfig.kt`。

## 4. 开始上传

1. 点「一键跑步」按钮；
2. 观察进度条与状态提示；
3. 完成后显示成功 / 失败统计。

运行日志区会逐条打印生成与上传进度，多条之间默认间隔数秒。

## 5. 自定义路线

1. 进入路线设计页，在地图上**依次连点**选择坐标点；
2. 点越多越贴合，**注意点是有顺序的，且起始点与终止点越接近越好**；
3. 保存后回到主界面重新开跑。

![路线设计](./images/android-route.png)

具体的坐标格式、坐标系差异，见 [routes.md](./routes.md)。

## 6. 权限说明

`AndroidManifest.xml` 申请：

| 权限 | 用途 |
| --- | --- |
| `INTERNET` | 访问 pe.sjtu.edu.cn 上传数据、检查更新 |
| `ACCESS_NETWORK_STATE` | 判断网络可用性 |
| `READ_PHONE_STATE` | 读取设备标识（用于请求头构造） |

应用开启了 `usesCleartextTraffic`（校园网环境下偶尔需要明文 HTTP）。

故障排查见 [faq.md](./faq.md)；自行编译见 [build.md](./build.md)。
