fn main() {
    println!("cargo:rerun-if-changed=../dist");
    println!("cargo:rerun-if-changed=Cargo.toml");
    println!("cargo:rerun-if-changed=tauri.conf.json");
    println!("cargo:rerun-if-changed=capabilities");
    println!("cargo:rerun-if-env-changed=APP_VERSION");
    // 版本号只有一个来源：desktop/src-tauri/Cargo.toml 的 package.version。
    // 发布流水线可用 APP_VERSION 覆盖它，并用 tag 做一致性断言（见 release.yml）。
    let version = std::env::var("APP_VERSION")
        .ok()
        .map(|value| value.trim_start_matches(['v', 'V']).to_owned())
        .unwrap_or_else(|| env!("CARGO_PKG_VERSION").to_owned());
    assert!(
        !version.is_empty()
            && version
                .chars()
                .all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '-' | '+')),
        "Desktop version must be a plain semantic version, got {version:?}"
    );
    println!("cargo:rustc-env=APP_VERSION={version}");
    // 生产构建把前端产物嵌进二进制；开发构建（pnpm tauri dev）使用 Vite。
    if std::env::var_os("CARGO_FEATURE_CUSTOM_PROTOCOL").is_some()
        && !std::path::Path::new("../dist/index.html").exists()
    {
        panic!("缺少 desktop/dist/index.html：请先运行 vite v8.3.5 building client environment for production...
transforming...
✓ 201 modules transformed.
rendering chunks...
computing gzip size...
dist/index.html                   0.45 kB │ gzip:   0.29 kB
dist/assets/index-CmF-FlXM.css   38.83 kB │ gzip:   6.41 kB
dist/assets/index-D8QRIYfX.js   571.18 kB │ gzip: 142.43 kB

✓ built in 94ms，再构建生产版");
    }
    tauri_build::build();
}
