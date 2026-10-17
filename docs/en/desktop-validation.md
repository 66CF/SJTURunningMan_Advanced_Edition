# Rust + Slint desktop migration verification

Date: 2026-10-16. Environment: Windows x86_64, Rust 1.95.0 (MSYS2 GNU), Slint 1.15.1.

## Verified

- Formatting and Clippy with warnings denied.
- 26 offline Rust tests plus one opt-in live CAPTCHA test covering input boundaries, routes and sampling, payload timestamps, versions, HTTP authentication/upload/error redaction, cancellation, path-scoped cookies, and AJAX login. Map-service tests cover loopback binding, referrer policy, Host/path isolation, updates, HEAD/non-GET handling, and releasing the port on shutdown.
- 3 Python packaging-layout tests, mocking system packaging commands to validate macOS plist/link/signing/DMG layout and Linux deb control/desktop files.
- Actual Release exe built by `build_windows.ps1`, with embedded icon and version.
- Standalone copy with no assets directory and PATH restricted to Windows system directories: offline smoke test exits with code 0 and produces 28 screenshots covering four pages × two themes × two window sizes, modals, running states, empty/long logs, wrapped notices, and dropdowns. Assertions cover mouse opening, keyboard selection/confirmation, disabled selection, and clearing logs.
- PE import table contains only Windows system DLLs, with no Python, Qt, or MSYS2 DLL dependency.
- Workflow YAML parses. Android and subsequent release jobs are byte-for-byte unchanged; no mobile code changes.

## Size and startup

This UI revision’s Windows Release: **10,208,256 bytes (9.735 MiB)**, product version `4.5.0`. SHA256: `11c4934652c337823c14a684ef0f496c122a20d3fe343f52f1805662e9367c2e`. The offline GUI check exited with code 0 and produced 28 screenshots. During the earlier login fix, the packaged anonymous CAPTCHA check received a 110×40 image and an automatically recognized code; the separate live account check is described below. Earlier migration builds reached input-idle in 753 / 715 / 731 ms; these are historical measurements, not a new timing run for this UI revision.

Startup is measured from PowerShell Start-Process to WaitForInputIdle, not a cross-machine or cold-cache first-frame benchmark. The smoke-result.json timing includes an intentional 300 ms screenshot delay and is not pure startup latency.

The old Qt build was not rebuilt under equivalent conditions, so no size-reduction percentage or speedup factor is claimed. Runtime and old background assets have been removed from the native package.

## Not verified

- Windows MSVC builds or other Windows versions.
- Actual macOS Intel/ARM builds, signing, installation, and runtime; Linux amd64/arm64 builds, deb installation, X11/Wayland, and file pickers.
- Exercise uploads and email/SMS second-factor verification. Account login, My SJTU app verification, and read-only portal authorization were checked separately against live services; mocks do not validate other live workflows.
- Coverage was not measured; 80% coverage is not claimed.

The new desktop-check.yml configures three-OS build/test/static checks and Windows/Linux screenshot smoke tests. That CI has not been triggered in this session.

## Migration details

- Automatic CAPTCHA recognition through the legacy service is preserved. Manual entry appears only as a fallback after recognition failure.
- Coordinate calibration, fixed 200 m distance compensation, start-time variation, and payload fields are preserved. Sampling is reimplemented and retains corners to avoid shortening distances.
- Long routes are automatically truncated; the old Qt option to keep the complete route is removed. Preview shows actual generated length.
- Import legacy TXT routes into the new user data directory. Old Qt source and the real assets/resources_rc.py dependency remain.

## CAPTCHA regression fix

An anonymous live request reproduced HTTP 403 without Referer; restoring the legacy header returned HTTP 200 and a 110×40 JPEG. Rust’s live image retrieval and automatic-recognition test passes without submitting account credentials. Regression tests check Referer, JPEG validation, multipart format, absence of login credentials in recognition requests, and invalid recognition results.

Opt in with `cargo test --locked -p sjturunningman live_captcha_can_be_loaded_and_recognized -- --ignored`. The packaged executable’s `--login-smoke-test <output-directory>` checks that normal UI callbacks receive the image and recognized result, without submitting a login or saving CAPTCHA images or codes.

## Account-login regression fix

An authorized live account check reproduced the original request returning to the login page. Adding the page's `lt=p` password-login marker and AJAX header, then following the `errno=0` response URL, restored the next authentication step. Immediate CAPTCHA submission was rejected; restoring the legacy one-second delay after recognition reached two-factor verification. Errors use controlled categories without exposing server messages or credentials.

The same check sent one My SJTU app code, verified the user-provided code, checked the authentication cookie, and completed read-only portal authorization and rule retrieval. It called the production `Session` implementation; the temporary test entry point was removed. No exercise record was uploaded, and no credentials, codes, cookies, or tokens were saved. Interactive acceptance of the final window remains for the user; this live account check exercised the shared network implementation.

Regression tests cover the password-login form marker, AJAX/Referer headers, returned redirects, two-factor handling, controlled CAPTCHA/password errors, missing/untrusted redirects, and response redaction. Second-factor requests and the final authorization redirect restore Referer; completion checks the authentication cookie rather than accepting HTTP 200 alone.

## Branding and About fix

The sidebar reuses the original icon. This UI revision replaces the earlier handwritten SVG wordmark with a clean system sans-serif `RunningMan` label. About provides the legacy Shuiyuan discussion and GitHub links with existing icons and appropriate dark-theme colors. Both links retain their legacy destinations and open in the system browser. Update notices still direct users to GitHub Releases.

Release screenshots check the wordmark, original icon, link icons, vertical button alignment, and About in both themes at the minimum window size. Link bindings and destinations were inspected; no Shuiyuan login was performed. macOS/Linux visuals still require device acceptance.

## Browser-map HTTP 403 fix

The original maps opened from `file://`, without an HTTP Referer; the planner also specified `no-referrer`, conflicting with [OSM tile requirements](https://operations.osmfoundation.org/policies/tiles/). An on-demand in-memory HTTP service now binds only to `127.0.0.1`, uses a fresh per-run page path, and sends `strict-origin-when-cross-origin`. Both pages use the official `https://tile.openstreetmap.org/{z}/{x}/{y}.png`. Tiles are not proxied, referrers are not forged, and there is no bulk prefetch; normal browser caching follows server headers.

The actual Release executable's `--map-smoke-test` invokes production UI callbacks. Headed Chrome displayed maps in both preview and planner, with sampled tile HTTP 200, an actual loopback HTTP Referer, and visible attribution. Selecting two planner points exported valid BD09 TXT. An isolated browser intercepted tiles with simulated HTTP 403: both pages reported failures and the preview retained its track. Simulated cases sent no live OSM tile requests. Local screenshots/export are under ignored `output/playwright/`. No account login or record upload was performed; macOS/Linux were not tested this turn.

## UI cleanup and layout revision

Promotional headers, uppercase section labels, and decorative numbering are removed. The Header row is reduced to 32 px with a 22 px Chinese title. Sidebar items are 40 px with a soft active fill. About uses compact application, update/community, and usage cards, preserving the forest-green themes, original icon, and community links.

Inputs and dropdowns share 38 px height, a soft 1 px border, and 8 px corners. Toolbar buttons are 34 px, secondary buttons 36 px, and Clear logs 32 px with content-sized width. The log description sits below its title, text is aligned top-left, and empty logs have a placeholder. The plan card follows content height with a fixed gap before its hint.

The full task bar appears only on the workspace, with a 6 px track filled from the left. Other pages show a compact status and Stop button only during a task. Long notices grow with wrapped text. All 28 Release screenshots were rendered by the actual application; the page/theme/size matrix, long text, and modals were visually reviewed using offline states only. Screenshots and assertions are under ignored `target/ui-modernization/release/`. macOS/Linux visuals still need device verification.

## macOS CJK rendering fix

The newly added macOS runtime check (`desktop-check.yml` now runs `--smoke-test` on the packaged app, see below) produced the first real macOS screenshots and showed that **no Chinese glyph renders at all**: Windows/Linux screenshots of the same build are correct, while macOS keeps only digits and Latin letters -- "跑步次数 / 次" renders as " / ", "单次距离 / km" as " / km", and the consent dialog shows an empty title and body with just the `1.`-`4.` list markers, while `RunningMan`, `v4.5.0`, and `BD09` render normally.

Pixel inspection shows a zero advance width for the Han characters (the slash sits flush against the label origin): the selected font has no such glyphs and no system fallback supplied them. The UI font was previously requested by **name** only (macOS: `PingFang SC`); that name does not resolve in a session-less environment, so Slint fell back to a Latin-only font.

Fix: at startup, walk a candidate list of "family name + system font file path", read the file, register it with `slint::fontique_07::shared_collection()` into Slint's own font collection, use the first family that registers as the UI font, and append it as a fallback for the Han script (`Hani`). Candidates: macOS `PingFang.ttc` / `Hiragino Sans GB.ttc` / `STHeiti Medium.ttc` / `Arial Unicode.ttf`, Windows `msyh.ttc` / `simhei.ttf`, Linux Noto Sans CJK / WenQuanYi Zen Hei; if none is usable the previous system family name is kept. Registration and fallback results are written to `smoke-result.json` as `ui_font` and `font_candidates`. This also removes the dependency on system font enumeration behaviour; no visual regression was found on Windows/Linux.

`slint` therefore enables `unstable-fontique-07` (Slint's official runtime font registration interface, see the `fontique_07` module docs). macOS still needs one manual confirmation of Chinese text on real hardware.
