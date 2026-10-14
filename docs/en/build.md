# Building from Source

The four platforms build independently. Build only the one you changed.

| Platform | Directory | Toolchain | Artifact |
| --- | --- | --- | --- |
| Desktop | repository root | Python + PyInstaller | `.exe` / `.dmg` / `.deb` |
| Android | `apps/android/` | Gradle 9.4.1 + AGP 8.13.0 + Kotlin 2.2.10 | `.apk` / `.aab` |
| iOS | `apps/ios/` | Xcode 16+ | `.ipa` |
| HarmonyOS | `apps/harmonyos/` | DevEco Studio / hvigor | `.hap` |

---

## Desktop

### Environment

```bash
python -m venv .venv
source .venv/bin/activate        # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

### Run directly

```bash
python qtui.py
```

### Package

**Windows**

```powershell
powershell -ExecutionPolicy Bypass -File .\build_windows.ps1
```

**All platforms (equivalent)**

```bash
python -m PyInstaller --clean --noconfirm ./main.spec
```

`main.spec` notes:

- Entry point `qtui.py`; the whole `assets/` directory is bundled.
- Icon is `assets/SJTURM.ico` on Windows, `assets/SJTURM.icns` on macOS.
- The macOS artifact is `universal2` (Intel + Apple Silicon).
- Version comes from the `APP_VERSION` environment variable, defaulting to `4.5.0` locally.

> ⚠️ Do not run `pyinstaller qtui.py` directly — you lose the icon and bundled assets.

### Automated release

[`.github/workflows/release.yml`](../../.github/workflows/release.yml) runs on tag pushes (both `4.5.0` and `v4.5.0` work) or manual `workflow_dispatch`, building five artifacts plus a checksum file in parallel into a draft Release:

| Artifact | Runner |
| --- | --- |
| `SJTURunningMan_<ver>_windows.exe` | windows-latest |
| `SJTURunningMan_<ver>_macOS.dmg` | macos-latest (universal2) |
| `SJTURunningMan_<ver>_linux_amd64.deb` | ubuntu-24.04 |
| `SJTURunningMan_<ver>_linux_arm64.deb` | ubuntu-24.04-arm |
| `SJTURunningMan_<ver>_android.apk` | ubuntu-latest (self-signed) |
| `SHA256SUMS.txt` | generated in the release job |

Python is pinned to 3.14.6. Both debs are built on Ubuntu 24.04 — PySide6's aarch64 wheel is `manylinux_2_39`, requiring glibc ≥ 2.39, which 22.04 does not provide.

> **iOS and HarmonyOS NEXT are not part of this pipeline**, and Releases ships no prebuilt packages for them — both are built by hand today:
>
> - **iOS** — technically automatable on a macOS runner, but requires an Apple signing identity (certificate + provisioning profile); an unsigned build cannot be installed, so it is not done for now.
> - **HarmonyOS NEXT** — Huawei's command-line tools must be downloaded behind a Huawei account and the links are short-lived, which rules out unattended CI; signing additionally needs the keystore / certificate / profile triplet issued through AppGallery Connect.

**Correct order**: push tag → workflow builds → draft release created with all artifacts → human review → publish. Do not create the release manually first and then push the tag: `action-gh-release` would update the existing release, and `draft: true` would flip a published one back to draft.

#### Verifying artifacts

Every build job generates a **provenance attestation** via `actions/attest-build-provenance`. The certificate is issued by GitHub OIDC and bound to this workflow run and commit, so it **cannot be forged locally**. Users can verify with:

```bash
# requires gh CLI 2.49+
gh attestation verify SJTURunningMan_<ver>_windows.exe --repo JackyTJie/SJTURunningMan_Advanced_Edition
```

Note this proves **build provenance**, not trustworthiness — anyone able to trigger the workflow can also produce a valid attestation. The Android APK additionally carries a self-signature, which is a separate guarantee.

#### Permissions

The workflow default is `contents: read`; write access is elevated per job. The `release` job needs `contents: write`; the five build jobs each need `id-token: write` (to mint the OIDC token) and `attestations: write` (to store the attestation). This keeps the sensitive OIDC permission off jobs like `meta` that don't need it.

#### Auto-generated release body

After a tag push, the draft's body is assembled by the `release` job with a fixed structure:

| Section | Source |
| --- | --- |
| Title, release date | Date comes from the **tag's commit time**, which is closer to the intended meaning than the workflow run time |
| Platforms and versions | Desktop uses the tag version; Android / HarmonyOS are read from their own project files (`versionName` in `build.gradle.kts` and `app.json5`) — if unreadable the field shows `?` rather than failing |
| Collapsible **What's Changed** | Generated via GitHub's `releases/generate-notes` API |
| **New features / Improvements / Fixes / Credits** | **Empty sections with `<!-- hints -->`** for the maintainer to fill in before publishing |
| **Download table** | Per-platform links and SHA256, plus verification instructions |

Two details:

- `generate_release_notes` is **off** — we fetch What's Changed ourselves and place it inside `<details>`; leaving it on would append a second copy at the end;
- `append_body` defaults to off, so re-running **overwrites** the body instead of accumulating, which is why a re-run won't produce duplicate tables.

### Secrets required before releasing

The Android artifact is **self-signed**, so the signing material must live in repository Secrets. Without them the `build-android` job fails outright (it does not fall back to an unsigned APK — that cannot be installed, which is worse than a failed build):

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | base64 of the keystore file (**single line, no wrapping**) |
| `ANDROID_KEYSTORE_PASSWORD` | keystore password |
| `ANDROID_KEY_ALIAS` | key alias |
| `ANDROID_KEY_PASSWORD` | key password |

Set them up with:

```bash
keytool -genkeypair -v \
  -keystore release.jks -alias sjturunningman \
  -keyalg RSA -keysize 4096 -validity 10000 -storetype PKCS12

base64 -i release.jks | tr -d '\n' | gh secret set ANDROID_KEYSTORE_BASE64
gh secret set ANDROID_KEYSTORE_PASSWORD
gh secret set ANDROID_KEY_ALIAS
gh secret set ANDROID_KEY_PASSWORD
```

> ⚠️ **Keep `release.jks` and both passwords safe.** Changing the keystore changes the signature, and APKs signed with the new key cannot be installed over an existing one — users would have to uninstall first.

---

## Android

```bash
cd apps/android

# Debug build
./gradlew assembleDebug

# Release build (needs signing config)
./gradlew assembleRelease
```

Artifacts land in `app/build/outputs/apk/`.

- Package `com.sjtu.runner`, `minSdk = 26`, `targetSdk = 34`, `compileSdk = 34`.
- Version lives in `app/build.gradle.kts` (`versionCode` / `versionName`).
- Without a signing config, `assembleRelease` produces an unsigned package. Signing needs the keystore properties referenced in the `signingConfigs` block.

> Dependency versions are centralized in `gradle/libs.versions.toml` (version catalog) — bump them there.

## iOS

```bash
cd apps/ios
open SJTURunningMan.xcodeproj
```

Configure the Team ID and Bundle ID in Xcode, then build — see [ios.md](./ios.md#3-signing).

List the project and schemes from the command line:

```bash
xcodebuild -project apps/ios/SJTURunningMan.xcodeproj -list
```

## HarmonyOS

Open `apps/harmonyos/` in **DevEco Studio**, pick a signing profile and build the HAP. From the command line:

```bash
cd apps/harmonyos
hvigorw assembleHap
```

- Bundle name `com.sjtu.runner`; version in `AppScope/app.json5`.
- Without developer verification the debug certificate expires quickly — see [harmonyos.md](./harmonyos.md#1-installing-the-hap).

---

## Cleaning local build residue

Build outputs and the virtualenv are already gitignored, but they take local disk space (`.venv/` ~1.2 GB, `dist/` ~238 MB, `build/` ~86 MB, `apps/android/app/build/` ~107 MB). **Dry-run first:**

```bash
git clean -Xdn     # preview: lists only gitignored files
git clean -Xdf     # delete (irreversible — confirm before running)
```

`-X` only removes ignored files, so your source changes are untouched.
