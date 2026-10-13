# iOS Guide

Version **`4.5.0`** (`MARKETING_VERSION`, kept in sync with `versionName` in `apps/ios/SJTURunningMan/AppConfig.swift`), bundle ID `SJTU.SJTURunningMan`. Project path: `apps/ios/`.

There is **no prebuilt package** for iOS — you build and sign it yourself with Xcode.

![iOS main screen](../images/ios-main.webp)

## Contents

- [1. Requirements](#1-requirements)
- [2. Clone and open](#2-clone-and-open)
- [3. Signing](#3-signing)
- [4. Build and run](#4-build-and-run)
- [5. Usage](#5-usage)
- [6. File structure](#6-file-structure)

## 1. Requirements

- **macOS** with **Xcode 16+**
- An **Apple Developer account** (a free one works)
- An **iPhone** at or above `IPHONEOS_DEPLOYMENT_TARGET = 26.5`

## 2. Clone and open

```bash
git clone https://github.com/JackyTJie/SJTURunningMan_Advanced_Edition.git
cd SJTURunningMan_Advanced_Edition/apps/ios
open SJTURunningMan.xcodeproj
```

## 3. Signing

### Set your Team ID

1. In Xcode, select the top-level **SJTURunningMan** project in the navigator.
2. Open the **Signing & Capabilities** tab.
3. Pick your team under **Team** — Xcode fills in the Team ID.

Alternatively, edit `SJTURunningMan.xcodeproj/project.pbxproj` and replace `YOUR_TEAM_ID` with your 10-character Apple Developer Team ID.

> **Where to find your Team ID**: [developer.apple.com/account](https://developer.apple.com/account) → **Membership** → **Team ID** at the top.

### Set a bundle identifier (optional)

The default is `SJTU.SJTURunningMan`. With a free developer account you usually need something unique, e.g. `com.yourname.SJTURunningMan`:

1. Select the project → **Signing & Capabilities**.
2. Change **Bundle Identifier** to a unique value.
3. Xcode generates a provisioning profile automatically.

## 4. Build and run

### Option A: straight from Xcode

1. Connect your iPhone over USB.
2. Select your iPhone as the build destination in the toolbar.
3. Click **Play ▶** (or `Cmd+R`).

On first run you may hit:

- **"Enable Developer Mode on this iPhone?"** — tap OK, then go to **Settings → Privacy & Security → Developer Mode**, enable it and restart the phone.
- **"Failed to register bundle identifier"** (free account) — make the bundle ID unique as above.
- **Untrusted developer** — on the iPhone, go to **Settings → General → VPN & Device Management → Developer App → Trust**.

### Option B: archive and sideload

1. **Product → Archive** in Xcode (`Cmd+Shift+K` first if needed).
2. In the Archives organizer, select the archive and click **Distribute App**.
3. Choose **Custom → Export as .ipa**.
4. Save the `.ipa` and install it with a sideloading tool such as AltStore or SideStore.

## 5. Usage

1. **Login** — the app opens the JAccount OAuth2 page; log in with your SJTU credentials.
2. **Set parameters** — count, time, date, distance (1–5 km, default 4 km).
3. **Start** — tap "开始任务". The app will:
   - fetch your UID from the PE platform;
   - generate GPS track data from the route coordinates;
   - randomize pace between 4–6 min/km;
   - upload each day's track one by one.
4. **Monitor** — the live log shows progress.
5. **Re-login** — when the session expires the app prompts you to log in again.

## 6. File structure

```
apps/ios/
├── SJTURunningMan/
│   ├── SJTURunningManApp.swift      App entry point
│   ├── LoginView.swift              JAccount OAuth2 WebView
│   ├── MainView.swift               Parameter settings & UI
│   ├── MainViewModel.swift          Upload logic & state
│   ├── ApiService.swift             HTTP client (fetch UID, upload results)
│   ├── DataGenerator.swift          GPS track generator
│   ├── GpsUtil.swift                Coordinate utilities
│   ├── RouteManager.swift           Route management
│   ├── RouteDesignView.swift        Route design screen
│   └── route_coordinates            Pre-recorded SJTU campus route points
├── SJTURunningMan.xcodeproj/        Xcode project
└── README.md                        Short in-directory pointer
```

Troubleshooting: [faq.md](./faq.md). Building and other platforms: [build.md](./build.md).
