"""打包 Rust 桌面产物；仅在构建机运行，不随应用分发。"""
import argparse
import os
from pathlib import Path
import plistlib
import re
import shutil
import subprocess


ROOT = Path(__file__).resolve().parents[1]


def package(platform, binary, output, version, arch):
    if not re.fullmatch(r"\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?", version):
        raise ValueError("Invalid APP_VERSION")
    binary = binary.resolve(strict=True)
    output.mkdir(parents=True, exist_ok=True)
    if platform == "macos":
        stage = output / "dmg-stage"
        contents = stage / "SJTURunningMan.app" / "Contents"
        if stage.exists():
            raise FileExistsError("Use a fresh output directory for packaging")
        (contents / "MacOS").mkdir(parents=True)
        (contents / "Resources").mkdir()
        shutil.copy2(binary, contents / "MacOS" / "SJTURunningMan")
        shutil.copy2(ROOT / "assets/SJTURM.icns", contents / "Resources/SJTURM.icns")
        with (contents / "Info.plist").open("wb") as file:
            plistlib.dump({
                "CFBundleExecutable": "SJTURunningMan",
                "CFBundleIdentifier": "com.sjtu.runningman",
                "CFBundleName": "SJTURunningMan",
                "CFBundleDisplayName": "SJTU RunningMan",
                "CFBundlePackageType": "APPL",
                "CFBundleShortVersionString": version.split("-")[0].split("+")[0],
                "CFBundleVersion": version.split("-")[0].split("+")[0],
                "CFBundleIconFile": "SJTURM.icns",
                "NSHighResolutionCapable": True,
                "LSMinimumSystemVersion": "11.0",
            }, file)
        subprocess.run(["codesign", "--force", "--deep", "--sign", "-", str(contents.parent)], check=True)
        (stage / "Applications").symlink_to("/Applications")
        subprocess.run(["hdiutil", "create", "-volname", f"SJTURunningMan {version}", "-srcfolder", str(stage),
                        "-ov", "-format", "UDZO", str(output / f"SJTURunningMan_{version}_macOS.dmg")], check=True)
    else:
        stage = output / f"deb-{arch}"
        if stage.exists():
            raise FileExistsError("Use a fresh output directory for packaging")
        for directory in ["DEBIAN", "usr/bin", "usr/share/applications", "usr/share/pixmaps"]:
            (stage / directory).mkdir(parents=True, exist_ok=True)
        shutil.copy2(binary, stage / "usr/bin/sjturunningman")
        (stage / "usr/bin/sjturunningman").chmod(0o755)
        shutil.copy2(ROOT / "assets/SJTURM.png", stage / "usr/share/pixmaps/sjturunningman.png")
        (stage / "usr/share/applications/sjturunningman.desktop").write_text(
            "[Desktop Entry]\nType=Application\nName=SJTU RunningMan\nExec=sjturunningman\n"
            "Icon=sjturunningman\nTerminal=false\nCategories=Education;Sports;\n", encoding="utf-8")
        (stage / "DEBIAN/control").write_text(
            f"Package: sjturunningman\nVersion: {version.replace('-', '~', 1)}\nArchitecture: {arch}\n"
            "Maintainer: Jacky Tang <tang.jie@live.com>\n"
            "Depends: libc6 (>= 2.39), libgcc-s1, libx11-6, libx11-xcb1, libxcb1, libxcb-render0, "
            "libxcb-shm0, libxkbcommon0, libxkbcommon-x11-0, libxrandr2, libxi6, libxcursor1, "
            "libwayland-client0, libfontconfig1\nRecommends: xdg-desktop-portal, fonts-noto-cjk\n"
            "Homepage: https://github.com/JackyTJie/SJTURunningMan_Advanced_Edition\n"
            "Description: SJTU RunningMan native Rust and Slint desktop client\n", encoding="utf-8")
        subprocess.run(["dpkg-deb", "--build", "--root-owner-group", str(stage),
                        str(output / f"SJTURunningMan_{version}_linux_{arch}.deb")], check=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("platform", choices=["macos", "linux"])
    parser.add_argument("--binary", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ROOT / "dist")
    parser.add_argument("--arch", choices=["amd64", "arm64"], default="amd64")
    parser.add_argument("--version", default=os.environ.get("APP_VERSION", "4.5.0"))
    args = parser.parse_args()
    package(args.platform, args.binary, args.output, args.version, args.arch)
