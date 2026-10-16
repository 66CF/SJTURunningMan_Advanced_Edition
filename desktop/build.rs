fn main() {
    println!("cargo:rerun-if-changed=ui");
    println!("cargo:rerun-if-changed=../src/config.py");
    println!("cargo:rerun-if-env-changed=APP_VERSION");
    let version = std::env::var("APP_VERSION").unwrap_or_else(|_| {
        let config = std::fs::read_to_string("../src/config.py").expect("Read desktop version");
        config
            .split('"')
            .nth(1)
            .expect("global_version")
            .trim_start_matches('v')
            .to_owned()
    });
    println!("cargo:rustc-env=APP_VERSION={version}");
    slint_build::compile_with_config(
        "ui/app.slint",
        slint_build::CompilerConfiguration::new().with_style("fluent-light".into()),
    )
    .expect("Compile Slint UI");
    if std::env::var("CARGO_CFG_TARGET_OS").as_deref() == Ok("windows") {
        let numbers: Vec<u64> = version
            .split(['.', '-', '+'])
            .take(3)
            .map(|part| {
                part.parse::<u64>()
                    .expect("Numeric desktop version")
                    .min(65535)
            })
            .collect();
        assert_eq!(numbers.len(), 3, "Desktop version needs major.minor.patch");
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
