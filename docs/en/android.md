# Android Guide

Version **`4.5.0`** (`versionName` in `apps/android/app/build.gradle.kts`, `versionCode = 40500`). Project path: `apps/android/`.

![Android main screen](../images/android-main.webp)

## Contents

- [1. Install](#1-install)
- [2. Login](#2-login)
- [3. Parameters](#3-parameters)
- [4. Uploading](#4-uploading)
- [5. Custom routes](#5-custom-routes)
- [6. Permissions](#6-permissions)

## 1. Install

1. Download the APK from [Releases](https://github.com/JackyTJie/SJTURunningMan_Advanced_Edition/releases).
2. Transfer it to your phone.
3. Tap to install. Android may warn about unknown sources — allow installation from that source.

> Requires Android 8.0 (API 26) or later. `minSdk = 26`, `targetSdk = 34`.

## 2. Login

1. On launch the app opens the JAccount login page to obtain a cookie. You can log in manually or use the one-tap "交我办" flow.
2. The cookie is validated automatically and re-acquired when it expires.
3. If it keeps failing, clear the app data in system settings and start over.

## 3. Parameters

| Parameter | Range | Default | Notes |
| --- | --- | --- | --- |
| Count | 1–30 | 1 | First run: use 1 to verify |
| Time | 06:00–23:00 | 08:00 | Randomized per record |
| End date | date picker | yesterday | Earlier records generated backwards |
| Distance | 1–5 km | 4 km | Target per record |
| Custom route | see below | Siyuan Lake route | Draw on a separate page |

Ranges come from `apps/android/app/src/main/java/com/sjtu/runner/config/AppConfig.kt`.

## 4. Uploading

1. Tap "一键跑步" (one-tap run).
2. Watch the progress bar and status text.
3. Success/failure counts appear when it finishes.

The log panel prints generation and upload progress per record, with a few seconds between records.

## 5. Custom routes

1. Open the route design page and tap points on the map **in order**.
2. More points give a better fit. **Order matters, and the start should be close to the finish.**
3. Save, return to the main screen and run again.

![Route design](../images/android-route.webp)

Coordinate format and system differences: [routes.md](./routes.md).

## 6. Permissions

Declared in `AndroidManifest.xml`:

| Permission | Purpose |
| --- | --- |
| `INTERNET` | Upload to pe.sjtu.edu.cn and check for updates |
| `ACCESS_NETWORK_STATE` | Network availability checks |
| `READ_PHONE_STATE` | Device identifier used in request headers |

Cleartext traffic is enabled (`usesCleartextTraffic`).

Troubleshooting: [faq.md](./faq.md). Building yourself: [build.md](./build.md).
