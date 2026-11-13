#!/usr/bin/env python3
"""Static verification of the built APKs. Proves packaging, not on-device behaviour."""
from pathlib import Path
import json
import os
import subprocess
import sys
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_apk  # noqa: E402  (reuses the same SDK/JDK discovery)

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / "dist"
AAPT2 = str(build_apk.BUILD_TOOLS / "aapt2.exe")
APKSIGNER = str(build_apk.BUILD_TOOLS / "apksigner.bat")

EXPECTED = {
    "GeoPinPrivate": {
        "package": "dev.geopin.privatepin",
        "launcher": "dev.geopin.app.ui.MainActivity",
        "is_module": True,
        "permissions": [
            "android.permission.INTERNET",
        ],
        "dex_markers": [
            "Ldev/geopin/app/HookEntry;",
            "Ldev/geopin/app/hook/LocationHook;",
            "Ldev/geopin/app/hook/PinRuntime;",
            "Ldev/geopin/core/StationaryEngine;",
            "Ldev/geopin/app/ui/MainActivity;",
            "Ldev/geopin/app/ui/PinMapView;",
        ],
    },
    "GeoPinProbe": {
        "package": "dev.geopin.probe",
        "launcher": "dev.geopin.probe.ProbeActivity",
        "is_module": False,
        "permissions": [
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
        ],
        "dex_markers": ["Ldev/geopin/probe/ProbeActivity;"],
    },
}


def run(command):
    environment = dict(os.environ)
    environment["JAVA_HOME"] = str(build_apk.JDK)
    environment["PATH"] = str(build_apk.JDK / "bin") + os.pathsep + environment.get("PATH", "")
    return subprocess.run(command, env=environment, capture_output=True,
                          text=True, encoding="utf-8", errors="replace")


def aapt2_dump(apk, *arguments):
    """Return (text, ok). A failed dump is reported, never treated as empty evidence."""
    result = run([AAPT2, "dump", *arguments, str(apk)])
    text = result.stdout
    return text, result.returncode == 0


def check(name, passed, detail=""):
    return {"check": name, "passed": bool(passed), "detail": detail}


def declared_permissions(badging_text):
    values = []
    for line in badging_text.splitlines():
        prefix = "uses-permission: name='"
        if line.startswith(prefix):
            values.append(line[len(prefix):].split("'")[0])
    return sorted(values)


def verify(apk, expected):
    checks = []
    badging_text, badging_ok = aapt2_dump(apk, "badging")
    tree, tree_ok = aapt2_dump(apk, "xmltree", "--file", "AndroidManifest.xml")
    with zipfile.ZipFile(apk) as bundle:
        names = set(bundle.namelist())
        dex = bundle.read("classes.dex") if "classes.dex" in names else b""
        xposed_init = ""
        if "assets/xposed_init" in names:
            xposed_init = bundle.read("assets/xposed_init").decode("utf-8").strip()

    checks.append(check("aapt2 dump badging", badging_ok))
    checks.append(check("aapt2 dump xmltree", tree_ok))
    checks.append(check("package name", f"name='{expected['package']}'" in badging_text, expected["package"]))
    checks.append(check("min sdk 29", "sdkVersion:'29'" in badging_text))
    checks.append(check("target sdk 30", "targetSdkVersion:'30'" in badging_text))
    checks.append(check("launcher activity", expected["launcher"] in badging_text, expected["launcher"]))

    actual_permissions = declared_permissions(badging_text)
    checks.append(check("declared permissions", actual_permissions == sorted(expected["permissions"]),
                        ", ".join(actual_permissions) or "none"))
    checks.append(check("no ACCESS_MOCK_LOCATION", "ACCESS_MOCK_LOCATION" not in badging_text))

    checks.append(check("classes.dex present", len(dex) > 0, f"{len(dex)} bytes"))
    checks.append(check("resources.arsc present", "resources.arsc" in names))
    missing = [marker for marker in expected["dex_markers"] if marker.encode("utf-8") not in dex]
    checks.append(check("dex class markers", not missing, ", ".join(missing) or "all present"))

    if expected["is_module"]:
        checks.append(check("xposed_init asset", xposed_init.endswith("HookEntry"), xposed_init))
        checks.append(check("meta-data xposedmodule", '"xposedmodule"' in tree))
        checks.append(check("meta-data xposedminversion", '"xposedminversion"' in tree))
        checks.append(check("meta-data xposedsharedprefs", '"xposedsharedprefs"' in tree))
    else:
        checks.append(check("no xposed metadata", '"xposedmodule"' not in tree))

    signature = run([APKSIGNER, "verify", str(apk)])
    checks.append(check("apksigner verify", signature.returncode == 0,
                        (signature.stdout + signature.stderr).strip()[:200]))
    return checks


def select_artifact(artifact):
    candidates = sorted(DIST.glob(artifact + "-*.apk"), key=lambda path: path.stat().st_mtime)
    if not candidates:
        return None, "tidak ada APK"
    newest = candidates[-1]
    return newest, ("satu-satunya APK" if len(candidates) == 1
                    else f"dipilih yang terbaru dari {len(candidates)} berkas")


def main():
    report = {"scope": "Verifikasi statis paket APK; BUKAN bukti perilaku di perangkat", "apks": {}}
    failures = 0
    for artifact, expected in EXPECTED.items():
        apk, note = select_artifact(artifact)
        if apk is None:
            print(f"MISSING {artifact} APK in {DIST}")
            failures += 1
            continue
        checks = verify(apk, expected)
        failed = [item for item in checks if not item["passed"]]
        failures += len(failed)
        report["apks"][artifact] = {
            "path": str(apk),
            "bytes": apk.stat().st_size,
            "selection": note,
            "checks": checks,
        }
        print(f"\n{artifact} ({apk.stat().st_size} bytes, {note})")
        for item in checks:
            status = "ok  " if item["passed"] else "FAIL"
            print(f"  {status} {item['check']}" + (f"  [{item['detail']}]" if item["detail"] else ""))

    DIST.mkdir(parents=True, exist_ok=True)
    (DIST / "verification.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"\n{'ALL CHECKS PASSED' if failures == 0 else str(failures) + ' CHECK(S) FAILED'}")
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
