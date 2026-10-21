# Desktop (Windows / macOS / Linux)

The desktop client now uses **Rust + Slint**, with `desktop/src/main.rs` as its entry point. It needs no Python, Qt, or WebView. Routes and the planner are embedded in the executable. The legacy Python modules `src/` and `utils/` remain as migration references (the old Qt entry point `qtui.py` has been removed); default builds and releases use Rust.

## Using the client

Run the Windows exe, drag the macOS application into Applications, or install the Linux deb for your architecture. Read and accept the usage commitment on each launch.

The sidebar contains Workspace, Routes, Logs, and About. The interface currently uses Chinese labels. Switch light/dark appearance at the top right. Preferences (including the skip-existing option) and routes live in the OS user data directory, so the installation directory need not be writable. On the workspace, below the run-plan card, a read-only "本学期进度" (this semester's progress) card shows the current semester's qualifying distance and completion rate. Both show `--` until you log in, then refresh automatically.

Results such as "二次验证成功", "登录成功" or "路线已导入" appear as a floating banner at the top of the workspace, stay for 5 seconds, then disappear without pushing the content below down; the same text always remains in the Logs page.

The sidebar uses the original application icon and a clean sans-serif `RunningMan` wordmark, with a soft background marking the active navigation item. About retains the update check and provides GitHub and Shuiyuan discussion links with their icons, opening in the system browser.

Inputs and dropdowns share compact rounded styling. The Logs page splits into two equal halves: a semester calendar on the left, listing this semester's run days week by week (solid green counts toward the score, outlined circles are invalid records, grey is a rest day), and the existing run log on the right. The log description sits below its title, separate from the Clear button. Empty logs show a placeholder; long logs scroll.

1. Set 1–30 runs, a distance greater than 0 and at most 5 km, an end date (`YYYY-MM-DD`), and a time (`HH:MM:SS`). Defaults are yesterday, 1 run, 4 km, and 08:00:00. Dates count backward, one record per day.
   "Skip days that already have a record" is on by default: days already present in the semester calendar move to earlier dates, and the upload count still matches your setting; turning it off restores plain backward generation. After you log in, the client sets runs and per-run distance from this semester's remaining distance, and leaves them untouched if you have changed them yourself.
2. Select `default` or import a UTF-8 BD09 TXT route with one `longitude,latitude` pair per line and at least two distinct points. Limits: 2 MB and 30,000 points. Imports create independent copies. Only imported copies can be deleted.
3. Generate the actual-track preview without logging in or connecting to the network. Red line segments indicate gaps over 150 m. Routes longer than the target are truncated; shorter open routes go back and forth, while closed routes loop.
4. Open the current preview in the system browser. The map **defaults to Baidu Maps** and offers an in-page switch to OpenStreetMap; before generation it shows the original route. Pages are served over HTTP on `127.0.0.1`, which also lets OpenStreetMap receive its required referrer. Online basemaps need network access; failures are reported. Leaflet is loaded from unpkg only when OpenStreetMap is selected. The in-app track view remains available offline. Keep the client running to reload the page, and reopen maps from the app after restarting it.
5. Open the route planner in the browser. It **defaults to Baidu Maps** with an in-page OpenStreetMap switch. Export TXT, then import it. Quarantined `NeedsReview/` routes remain excluded.
6. Log in with JAccount and password. The graphical CAPTCHA is **automatically recognized using the legacy service**. If recognition fails, refresh to retry or use the manual fallback. Complete app/email/SMS verification if requested. The recognition request contains only the image, with no account, password, or login cookies. Images and recognition results stay in memory. After a successful login the client fetches this semester's data (qualifying distance, completion rate, run dates) in the background; no manual refresh is needed.
7. Progress and task controls appear on the workspace. Other pages show a compact status and Stop control only while a task is running. Preview and review settings, then start and confirm the upload. Background threads update progress and logs. Each generated upload payload updates the displayed preview. Failed POST requests are not retried automatically, to avoid duplicate records. When a task uploads at least one record, the progress card and semester calendar refresh once so the new day appears.
8. Stop prevents subsequent uploads. An HTTP request already in flight can take up to 15 seconds to finish. Closing during a task stops it and waits for the current request before exiting.

## Local data and credentials

Passwords, account identifiers, CAPTCHA codes, cookies, and tokens remain in process memory. Log out or exit to clear the session. Logs retain only the latest 250 lines in memory; preferences contain no credentials. Semester statistics and run dates are likewise memory-only for display, and revert to placeholders on logout. A failed statistics refresh writes one log line and never logs you out.

User data locations:

- Windows: `%LOCALAPPDATA%\sjtu\SJTURunningMan\data`.
- macOS: `~/Library/Application Support/cn.sjtu.SJTURunningMan`.
- Linux: `${XDG_DATA_HOME:-~/.local/share}/sjturunningman`.

The directory holds `settings.json` and `routes/`. Map HTML stays in memory, served on demand over loopback without exposing a file directory; the service stops when the client exits. Legacy `Routes/`, `user.txt`, and `ui_settings.json` are not migrated automatically. Import existing route TXT files to migrate them.

## Build and verify

To run the source directly without an installer, clone the repository and use `cargo run` (Rust 1.95+; see [build.md](build.md)).

```bash
cargo run --locked -p sjturunningman
cargo test --workspace --locked
cargo build --release --locked
# Offline preview, input checks, page screenshots, then exit.
# Add .exe on Windows.
./target/release/sjturunningman --smoke-test target/smoke
```

On Windows, `./build_windows.ps1` creates `dist/SJTURunningMan.exe`. See [build.md](build.md) for packaging. Automated tests submit no real credentials or exercise records. A separately authorized Windows check completed account login, My SJTU app verification, and read-only portal authorization; uploads were not tested. See the [verification record](desktop-validation.md) and [acceptance checklist](testing.md).

## Size and startup

Only Slint's winit backend and software renderer are enabled. The app does not bundle Qt, Skia, Chromium, Python, or the old background assets. The track is rendered locally and network access is triggered by user actions. Release builds use size optimization, LTO, and stripped symbols. Measure actual Release builds on each OS; Debug output and older downloaded releases are not equivalent baselines.

Sampling preserves corner points to avoid shortening the measured distance. The migration preserves the desktop coordinate calibration, fixed 200 m distance compensation, and payload fields, while rewriting route sampling. Random tracks are not bit-for-bit identical to Python. Mobile implementations are unchanged.

See the [migration verification record](desktop-validation.md) for build checks, measurements, and verification limits.
