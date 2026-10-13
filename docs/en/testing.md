# Testing Guide

## Current state

| Platform | Automated tests | Notes |
| --- | --- | --- |
| Android | ✅ Yes | Unit + instrumented tests, below |
| Desktop | ❌ None | Manual acceptance only |
| iOS | ❌ None | No test target |
| HarmonyOS | ❌ None | — |
| CI | ❌ No tests | `release.yml` only packages |

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

### Instrumented tests (device or emulator)

```bash
cd apps/android
./gradlew connectedAndroidTest
```

Case: `app/src/androidTest/java/com/sjtu/runner/AppInstrumentedTest.kt`.

### Adding a test

- Pure logic (data generation, version compare, coordinate math) → `app/src/test/`, JUnit 4.
- Anything needing a Context or UI → `app/src/androidTest/`, AndroidX JUnit + Espresso / Compose Test.

---

## Desktop manual checklist

Walk through this before submitting any desktop change.

**Startup**

- [ ] `python qtui.py` opens without a stack trace
- [ ] The commitment dialog appears and blocks until accepted
- [ ] Background and theme render correctly (no garbled text)

**Validation**

- [ ] Count `0` or `31` is rejected
- [ ] Time `8:00` (not `HH:MM:SS`) is rejected
- [ ] Date `2026/05/07` is rejected
- [ ] Distance `6` is rejected, `5` accepted

**Routes**

- [ ] The preset dropdown lists `default.txt`
- [ ] "Custom…" imports a txt and it appears in the dropdown
- [ ] Imported routes survive a restart
- [ ] Scrolling over the dropdown does not switch routes
- [ ] "Delete" removes imported routes but not `default.txt`

**Preview**

- [ ] "Preview route" works while **logged out**
- [ ] The browser map shows the track, green start and red finish
- [ ] A route with a > 150 m jump is highlighted red

**Upload**

- [ ] A 1-run, 2 km configuration completes successfully
- [ ] "Stop" interrupts without freezing the UI
- [ ] Logs stream per record, no uncaught exceptions

**Packaging**

- [ ] `python -m PyInstaller --clean --noconfirm ./main.spec` succeeds
- [ ] The artifact has the correct icon (not the PyInstaller default)
- [ ] The artifact launches and loads `assets/` correctly

---

## iOS manual checklist

- [ ] The project compiles in Xcode without warnings
- [ ] The login page brings up JAccount OAuth2
- [ ] "Start task" after configuring parameters fetches the UID
- [ ] The log scrolls live with per-record progress
- [ ] An expired session shows a working "re-login" button

---

## Before committing

```bash
# Android: make sure unit tests pass
cd apps/android && ./gradlew test

# General: confirm no build artifacts or local config got staged
git status
```

See the PR checklist in [CONTRIBUTING.md](../../CONTRIBUTING.md).
