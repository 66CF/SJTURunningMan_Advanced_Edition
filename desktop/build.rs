fn main() {
    println!("cargo:rerun-if-changed=ui");
    println!("cargo:rerun-if-changed=Cargo.toml");
    println!("cargo:rerun-if-env-changed=APP_VERSION");
    // 版本号只有一个来源：desktop/Cargo.toml 的 package.version。
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
    slint_build::compile_with_config(
        "ui/app.slint",
        slint_build::CompilerConfiguration::new().with_style("fluent-light".into()),
    )
    .expect("Compile Slint UI");
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("windows") {
        // 只取前三段数字作为 FILEVERSION，预发布后缀（如 4.5.0-beta.2）不参与。
        let numbers: Vec<u64> = version
            .split(['-', '+'])
            .next()
            .unwrap_or_default()
            .split('.')
            .map(|part| {
                part.parse::<u64>()
                    .unwrap_or_else(|_| panic!("Numeric desktop version, got {part:?}"))
                    .min(65535)
            })
            .chain(std::iter::repeat(0))
            .take(3)
            .collect();
        let numeric = (numbers[0] << 48) | (numbers[1] << 32) | (numbers[2] << 16);
        winresource::WindowsResource::new()
            .set_icon("../assets/SJTURM.ico")
            .set("ProductName", "SJTU RunningMan")
            .set("FileDescription", "SJTU RunningMan · Rust + Slint")
            .set("FileVersion", &version)
            .set("ProductVersion", &version)
            .set_version_info(winresource::VersionInfo::FILEVERSION, numeric)
            .set_version_info(winresource::VersionInfo::PRODUCTVERSION, numeric)
            .compile()
            .expect("Embed Windows icon");
    }
}
