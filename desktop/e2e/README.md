# Desktop UI checks

These scripts run the production frontend bundle in Chromium with a mocked Tauri
IPC layer. They are for layout and interaction checks only; the real WebView is
covered by the Rust `--ui-smoke-test` command.

```bash
# terminal 1
pnpm build
pnpm exec vite preview --port 4173 --strictPort

# terminal 2
pnpm exec playwright install chromium   # once per machine
node e2e/check.mjs        # functional assertions
node e2e/preview-fixes.mjs # consent alignment, continuous slider, tile error recovery
node e2e/slider-drag.mjs   # first-frame pointer tracking, including sub-threshold moves
node e2e/live-route.mjs   # live geometry, smooth endpoint, stable viewport, reduced motion
node e2e/screenshot.mjs   # screenshots to /tmp/ui-shots
```

The mock (`mock-init.js`) answers the same command names as `src-tauri/src/commands.rs`.
It never talks to SJTU services. Keep it in sync when a command or payload changes.
