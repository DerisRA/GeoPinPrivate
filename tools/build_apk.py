"""Build GeoPin Private APKs without Gradle, using aapt2 + javac + d8 + apksigner."""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def find_sdk():
    """Locate an Android SDK from project configuration or standard environment variables."""
    candidates = [
        os.environ.get("GEOPIN_SDK"),
        str(ROOT / ".toolchain" / "android-sdk"),
        os.environ.get("ANDROID_HOME"),
        os.environ.get("ANDROID_SDK_ROOT"),
    ]
    for candidate in candidates:
        if not candidate:
            continue
        root = Path(candidate)
        build_tools_root = root / "build-tools"
        platform = root / "platform-30" / "android-11" / "android.jar"
        if build_tools_root.is_dir() and platform.is_file():
            build_tools = sorted(path for path in build_tools_root.iterdir() if path.is_dir())
            if build_tools:
                return build_tools[-1], platform
    raise SystemExit(
        "Android SDK tidak ditemukan. Setel GEOPIN_SDK (atau ANDROID_HOME) ke folder yang berisi "
        "build-tools/ dan platform-30/android-11/android.jar.")


def find_jdk():
    candidates = [os.environ.get("GEOPIN_JDK"), str(ROOT / ".toolchain" / "jdk"), os.environ.get("JAVA_HOME")]
    for candidate in candidates:
        if not candidate:
            continue
        javac = Path(candidate) / "bin" / "javac.exe"
        if javac.is_file():
            return Path(candidate)
    raise SystemExit("JDK 17 tidak ditemukan. Setel GEOPIN_JDK atau jalankan tools/bootstrap_java.py.")


def find_xposed_api():
    candidates = [
        os.environ.get("GEOPIN_XPOSED_API"),
        str(ROOT / ".toolchain" / "libs" / "api-82.jar"),
    ]
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            return Path(candidate)
    raise SystemExit("api-82.jar tidak ditemukan. Setel GEOPIN_XPOSED_API ke jar Xposed API 82.")


BUILD_TOOLS, PLATFORM_JAR = find_sdk()
JDK = find_jdk()
XPOSED_API = find_xposed_api()

VERSION_CODE = "1"
VERSION_NAME = "0.1.0"
APP_ID = os.environ.get("GEOPIN_APPLICATION_ID", "dev.geopin.privatepin")

MODULES = {
    "app": {
        "manifest": ROOT / "app" / "src" / "main" / "AndroidManifest.xml",
        "res": ROOT / "app" / "src" / "main" / "res",
        "assets": ROOT / "app" / "src" / "main" / "assets",
        "sources": [ROOT / "app" / "src" / "main" / "java", ROOT / "core" / "src" / "main" / "java"],
        "namespace": "dev.geopin.app",
        "package": APP_ID,
        "artifact": "GeoPinPrivate",
        "build_config": True,
    },
    "probe": {
        "manifest": ROOT / "probe" / "src" / "main" / "AndroidManifest.xml",
        "res": ROOT / "probe" / "src" / "main" / "res",
        "assets": None,
        "sources": [ROOT / "probe" / "src" / "main" / "java"],
        "namespace": "dev.geopin.probe",
        "package": "dev.geopin.probe",
        "artifact": "GeoPinProbe",
        "build_config": False,
    },
}


def run(command, cwd=None):
    printable = " ".join(str(part) for part in command)
    print(f"$ {printable}", flush=True)
    environment = dict(os.environ)
    environment["JAVA_HOME"] = str(JDK)
    environment["PATH"] = str(JDK / "bin") + os.pathsep + environment.get("PATH", "")
    completed = subprocess.run(command, cwd=cwd, env=environment, capture_output=True,
                               text=True, encoding="utf-8", errors="replace")
    if completed.stdout.strip():
        print(completed.stdout.rstrip(), flush=True)
    if completed.returncode != 0:
        sys.stderr.write(completed.stderr)
        raise SystemExit(f"command failed ({completed.returncode}): {printable}")
    if completed.stderr.strip():
        print(completed.stderr.rstrip(), flush=True)
    return completed


def write_build_config(target: Path, namespace: str, package: str):
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(
        f"""package {namespace};

/** Dihasilkan oleh tools/build_apk.py; jangan disunting manual. */
public final class BuildConfig {{
    public static final String APPLICATION_ID = "{package}";
    public static final String BUILD_TYPE = "debug";
    public static final boolean DEBUG = true;
    public static final int VERSION_CODE = {VERSION_CODE};
    public static final String VERSION_NAME = "{VERSION_NAME}";

    private BuildConfig() {{
    }}
}}
""",
        encoding="utf-8",
    )


def collect_sources(directories):
    files = []
    for directory in directories:
        files.extend(sorted(path for path in directory.rglob("*.java")))
    return files


def prepare_manifest(module, build_dir):
    """AGP rejects a manifest `package` attribute while aapt2 needs one, so inject it per build."""
    text = module["manifest"].read_text(encoding="utf-8")
    if "package=" not in text[:600]:
        text = text.replace(
            "<manifest", '<manifest package="%s"' % module["namespace"], 1)
    target = build_dir / "AndroidManifest.xml"
    target.write_text(text, encoding="utf-8")
    return target


def build(name, keep=False):
    module = MODULES[name]
    build_dir = ROOT / "build" / name
    if build_dir.exists():
        shutil.rmtree(build_dir)
    for folder in ("gen", "classes", "dex", "res"):
        (build_dir / folder).mkdir(parents=True, exist_ok=True)

    if module["build_config"]:
        write_build_config(
            build_dir / "gen" / module["namespace"].replace(".", "/") / "BuildConfig.java",
            module["namespace"], module["package"])

    run([str(BUILD_TOOLS / "aapt2.exe"), "compile", "--dir", str(module["res"]),
         "-o", str(build_dir / "res" / "compiled.zip")])

    manifest = prepare_manifest(module, build_dir)
    link = [
        str(BUILD_TOOLS / "aapt2.exe"), "link",
        "-o", str(build_dir / "base.apk"),
        "-I", str(PLATFORM_JAR),
        "--manifest", str(manifest),
        "-R", str(build_dir / "res" / "compiled.zip"),
        "--java", str(build_dir / "gen"),
        "--custom-package", module["namespace"],
        "--min-sdk-version", "29",
        "--target-sdk-version", "30",
        "--version-code", VERSION_CODE,
        "--version-name", VERSION_NAME,
        "--auto-add-overlay",
    ]
    if module["package"] != module["namespace"]:
        link.extend(["--rename-manifest-package", module["package"]])
    if module["assets"] is not None:
        link.extend(["-A", str(module["assets"])])
    run(link)

    sources = collect_sources(module["sources"]) + sorted((build_dir / "gen").rglob("*.java"))
    run([
        str(JDK / "bin" / "javac.exe"),
        "-source", "8", "-target", "8",
        "-bootclasspath", str(PLATFORM_JAR),
        "-classpath", os.pathsep.join([str(PLATFORM_JAR), str(XPOSED_API)]),
        "-encoding", "UTF-8",
        "-Xlint:all,-options",
        "-Werror",
        "-d", str(build_dir / "classes"),
    ] + [str(path) for path in sources])

    classes_jar = build_dir / "classes.jar"
    run([str(JDK / "bin" / "jar.exe"), "--create", "--file", str(classes_jar),
         "-C", str(build_dir / "classes"), "."])
    run([str(BUILD_TOOLS / "d8.bat"), "--release", "--lib", str(PLATFORM_JAR),
         "--min-api", "29", "--output", str(build_dir / "dex"), str(classes_jar)])

    unsigned = build_dir / "unsigned.apk"
    with zipfile.ZipFile(build_dir / "base.apk") as base, zipfile.ZipFile(unsigned, "w", zipfile.ZIP_DEFLATED) as out:
        for item in base.infolist():
            out.writestr(item, base.read(item.filename))
        out.write(build_dir / "dex" / "classes.dex", "classes.dex")

    aligned = build_dir / "aligned.apk"
    run([str(BUILD_TOOLS / "zipalign.exe"), "-p", "-f", "4", str(unsigned), str(aligned)])

    keystore = ROOT / ".toolchain" / "debug.keystore"
    keystore.parent.mkdir(parents=True, exist_ok=True)
    if not keystore.is_file():
        run([str(JDK / "bin" / "keytool.exe"), "-genkeypair", "-keystore", str(keystore),
             "-alias", "geopin", "-storepass", "android", "-keypass", "android",
             "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
             "-dname", "CN=GeoPin Private, O=Local Build, C=ID"])

    dist = ROOT / "dist"
    dist.mkdir(parents=True, exist_ok=True)
    apk = dist / f"{module['artifact']}-{VERSION_NAME}-debug.apk"
    # minSdk 29 berarti v1 (JAR signing) tidak diperlukan; apksigner memilih skema v3 untuk rentang ini.
    run([str(BUILD_TOOLS / "apksigner.bat"), "sign",
         "--ks", str(keystore), "--ks-pass", "pass:android", "--key-pass", "pass:android",
         "--v2-signing-enabled", "true", "--v3-signing-enabled", "true",
         "--out", str(apk), str(aligned)])
    run([str(BUILD_TOOLS / "apksigner.bat"), "verify", str(apk)])
    run([str(BUILD_TOOLS / "aapt2.exe"), "dump", "badging", str(apk)])

    print(f"\nAPK {name}: {apk} ({apk.stat().st_size} bytes)\n", flush=True)
    if not keep:
        shutil.rmtree(build_dir, ignore_errors=True)
    return apk


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--module", choices=sorted(MODULES) + ["all"], default="all")
    parser.add_argument("--keep", action="store_true", help="keep intermediate build directories")
    args = parser.parse_args()
    names = sorted(MODULES) if args.module == "all" else [args.module]
    for name in names:
        build(name, keep=args.keep)


if __name__ == "__main__":
    main()
