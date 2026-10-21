# Testing Guide

## Current state

| Platform | Automated tests | Notes |
| --- | --- | --- |
| Android | ✅ Yes | Unit + instrumented tests, below |
| Desktop | ✅ Yes | Rust unit tests, local HTTP mocks, Slint screenshot smoke tests |
| iOS | ❌ None | No test target |
| HarmonyOS | ❌ None | Manual acceptance only (see below) |
| CI | ✅ Desktop tests | `desktop-check.yml` runs fmt / clippy / unit tests on Ubuntu; installers and smoke checks run only in the release workflow |

**Additional tests are welcome** — see [CONTRIBUTING.md](../../CONTRIBUTING.md).

---

## Android

### Unit tests (no device needed)

```bash
cd apps/android
./gradlew test
```

Report: `app/build/reports/tests/testDebugUnitTest/index.html`

Existing cases:

| File | Coverage |
| --- | --- |
| `app/src/test/java/com/sjtu/runner/data/DataGeneratorTest.kt` | Route distance correction, truncate mode reaching target, full-loop mode, over-long route truncation, pace clamped to rules, payload matching the upload schema |
| `app/src/test/java/com/sjtu/runner/update/UpdateCheckerTest.kt` | Version parsing (prefix / prerelease), newer tag detected, older/equal ignored |
| `app/src/test/java/com/sjtu/runner/stats/SemesterStatsTest.kt` | Semester range validity, calendar week splitting (Monday-first, month title only on the first row), counted / invalid / no-run states and "counted is never overwritten", completion-rate and kilometre formatting, plan derived from the semester gap |
| `app/src/test/java/com/sjtu/runner/stats/RunScheduleTest.kt` | Continuous backwards generation when skipping is off, already-recorded days moved earlier with the count unchanged when it is on, the 120-day and semester-start bounds, skipped-day log wording |

### Instrumented tests (device or emulator)

```bash
cd apps/android
./gradlew connectedAndroidTest
```

Case: `app/src/androidTest/java/com/sjtu/runner/AppInstrumentedTest.kt`.

### Adding a test

- Pure logic (data generation, version compare, coordinate math) → `app/src/test/`, JUnit 4.
- Anything needing a Context or UI → `app/src/androidTest/`, AndroidX JUnit + Espresso / Compose Test.

### Semester data manual acceptance (device, valid account required)

Automated tests only cover pure logic (semester range, calendar states, date scheduling). Check these on a device:

- [ ] Not logged in: "本学期进度" shows `--` and "学期日历" is a placeholder calendar saying "服务器未返回本学期起止日期。"
- [ ] After a successful login both cards sync automatically with no manual refresh; after logging out they return to `--` and the placeholder calendar
- [ ] The calendar distinguishes counted (solid green), invalid (outlined) and no-run (grey) days, days outside the semester are blank cells, weeks start on Monday
- [ ] A failed semester sync logs one line, leaves the login state untouched and never logs you out
- [ ] The first sync after login fills "run count × distance per run" from the semester gap; once you edit either value by hand the plan is no longer rearranged and the log says why
- [ ] Auto-filled count and distance survive a restart
- [ ] With "skip days that already have a record" on, recorded days move to earlier dates and the count still equals what was configured; with it off, dates are generated continuously backwards from the end date
- [ ] Overlay notices collapse after 5 seconds without pushing content below down; the same text stays in the Task log
- [ ] On other tabs the compact running bar can stop the task, and nothing further is sent after stopping
- [ ] Contrast of the three calendar states and card colors is correct in both light and dark themes
- [ ] Logs and preferences contain no cookie, uid or server response body

---

## Desktop manual checklist

Run `cargo test --workspace --locked`, `cargo fmt --all -- --check`, and `cargo clippy --workspace --all-targets --locked -- -D warnings`.

Run the Release executable with `--smoke-test target/smoke` to verify offline preview, invalid input, clearing logs, and opening a dropdown by mouse, selecting with arrow keys, confirming, and disabling it. It saves 30 screenshots: 16 combinations of four pages, two themes, and normal/minimum window sizes, plus consent, four modal types, progress/cross-page Stop, empty/long logs, wrapped notices, dropdown interactions, and the semester progress card and semester calendar under a statistics fixture (counts toward score, invalid record, rest day, and blank out-of-range cells). The statistics and calendar use fixed fixture values rather than real account data. It uses isolated local data, never logs in, and never uploads; `smoke-result.json` records the actual screenshot count and fixture row count.

For online maps, run `--map-smoke-test target/map-smoke`. Production UI callbacks prepare the default track and planner, writing `preview-url.txt` and `planner-url.txt` to that directory. Keep the test window running and open the URLs in a browser. Check tile HTTP 200, the actual loopback Referer, attribution, and TXT export. In an isolated browser, intercept tiles with HTTP 403: a failure message must appear while retaining the track, without retry loops. This mode never logs in or uploads. Closing the window stops the service; old URLs no longer reload.

- [ ] Commitment gates entry until checked; Exit closes the app.
- [ ] Invalid runs, NaN/zero/excessive distance, malformed date/time are rejected.
- [ ] Chinese text, light/dark appearance, Tab/Enter/Space, display scaling, and small-window scrolling work.
- [ ] Default route, import, duplicate names, persistence, and copy-only deletion work; the source remains untouched.
- [ ] Offline actual-track preview includes the legacy 200 m compensation; open routes reverse, closed routes loop; original gaps over 150 m are flagged.
- [ ] Browser-map endpoints match the preview; planner-exported TXT can be imported.
- [ ] With a valid account, automatic CAPTCHA recognition and app/email/SMS verification work, including refresh and manual fallback after recognition failure.
- [ ] After login, the semester progress card and semester calendar refresh automatically; while logged out or after a failed refresh both stay on placeholders, and logout restores them.
- [ ] The semester calendar distinguishes records that count toward the score (solid green), invalid records (outlined), and rest days (grey); dates outside the semester are blank cells, and the calendar/log halves do not overflow at the 1040×700 minimum window.
- [ ] A failed semester-data refresh reports one log line only and leaves the login state unchanged.
- [ ] During an authorized live acceptance run, progress and outcomes are accurate; failed POST requests are not retried automatically.
- [ ] Stop and close prevent further records; an in-flight HTTP request can take up to 15 seconds; the UI remains responsive.
- [ ] Logout clears the session; preferences, logs, and screenshots contain no real credentials.
- [ ] Logs and `smoke-result.json` from the semester statistics contain no cookies, uid, or server response bodies.
- [ ] Updates are checked on demand; network failures are reported.
- [ ] Windows exe has the expected icon/version, works away from assets, and requires no Python/Qt/MSYS2 DLLs.
- [ ] macOS Intel/ARM targets combine into a universal app; DMG contains app and Applications link; validate on both architectures.
- [ ] Chinese text is fully visible on macOS (navigation, form labels, log, full consent text), not just digits and Latin letters -- a failed font registration blanks all CJK, see [validation record](desktop-validation.md).
- [ ] Linux amd64/arm64 debs install on matching Ubuntu 24.04 hosts; validate X11, Wayland, and file picker.

Windows checks do not replace macOS/Linux validation. Offline/local HTTP mock tests cannot establish live authentication, two-factor, or upload success.

---

## iOS manual checklist

- [ ] The project compiles in Xcode without warnings
- [ ] The login page brings up JAccount OAuth2
- [ ] "Start task" after configuring parameters fetches the UID
- [ ] The log scrolls live with per-record progress
- [ ] An expired session shows a working "re-login" button

---

## HarmonyOS manual checklist

After changing HarmonyOS code, build once with hvigor (see [build.md](./build.md#harmonyos)), then walk through this list:

**Startup and gate**

- [ ] `assembleHap` succeeds; the installed app starts with no jscrash (in particular, `class constructor cannot called without 'new'` means a custom component was written inside an arrow function — see [harmonyos.md](./harmonyos.md#8-known-arkts-toolchain-traps))
- [ ] First launch shows the full-screen "使用承诺书" card; "我承诺，进入软件" is disabled until the checkbox is ticked
- [ ] "退出" ends the app immediately; after accepting, a restart does not show the gate again

**Login**

- [ ] The main screen has no bottom tab bar; the title "开润" sits on the same row as the account/about icon buttons
- [ ] Tapping the top-right account icon (labelled "登录" when signed out) opens the login page; the Web page loads with a progress bar
- [ ] After login it returns automatically and the status card reads "已登录，点击可退出"; the account icon label becomes "账号"
- [ ] While signed in, tapping the account icon opens the "退出账号" confirmation; confirming returns to the logged-out state
- [ ] Tapping the top-right "关于" icon opens the standalone About page

**Parameters**

- [ ] The Runs dropdown offers 1/3/5/7/10/15/30; picking "自定义…" and typing `0` / `31` clamps back into 1–30
- [ ] After picking "自定义…" the dropdown itself reads 自定义 and the number field appears next to it (no extra empty button)
- [ ] Tapping the start time opens a 24-hour hour/minute/second picker; changing a value and confirming fills it back in (hour caps at 23, minute and second at 59)
- [ ] Tapping the ⓘ icon next to the start-time label explains the jitter rule
- [ ] The whole end-date row is tappable, opens the date picker, and defaults to yesterday
- [ ] Distance slider spans 1–5 km; its value sits on the same line as the "目标距离" label and updates live while dragging, and the "距离" stat card reflects the final value
- [ ] A scrollbar appears on the home page and the About page while scrolling and fades out when idle

**Routes**

- [ ] The dropdown lists the built-in route plus custom routes (with point count and the "内置" marker)
- [ ] "设计新路线" opens the map page; after tapping points and saving it returns to the main page and a toast shows the point count
- [ ] Deleting a non-built-in route asks for confirmation; the built-in route cannot be deleted
- [ ] "预览路线" renders without being logged in (scatter plot plus distance / point / jump-count summary)

**Upload**

- [ ] A 1-run, 4 km configuration finishes and shows "任务完成: 成功 1 条，失败 0 条"
- [ ] The status card steps through 准备中 → 获取认证 → 生成数据 → 上传中 → 已完成 with the progress bar advancing
- [ ] "停止任务" interrupts a running task and the UI does not freeze
- [ ] The log is colour-coded per level (INFO/WARN/ERROR/SUCCESS), auto-scrolls to the bottom, and "清空" works
- [ ] When the selected route is longer than the target distance, "路线距离提醒" appears and all three buttons behave as their hint text says
- [ ] An expired cookie shows the top re-login banner; "登录" returns to the login page

**About and updates**

- [ ] The About page shows version `v4.5.0`, the disclaimer and the project credits (including "鸿蒙版维护：Github@AetherWish")
- [ ] Entering the About page does not jam the content against the back bar: there is a safe area below the back icon and the "关于" title, and the first screen is not covered by the title bar
- [ ] "检查更新" reports either "已是最新版本" or "发现新版本"; on a weak network it shows "检查更新失败：…"
- [ ] The "仓库" and "水源" buttons open the browser

**Theme**

- [ ] After switching the system to dark mode, background / card / text contrast is correct with no white-on-white or black-on-black mismatches

---

## Before committing

```bash
# Android: make sure unit tests pass
cd apps/android && ./gradlew test

# General: confirm no build artifacts or local config got staged
git status
```

See the PR checklist in [CONTRIBUTING.md](../../CONTRIBUTING.md).
