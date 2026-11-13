#!/usr/bin/env python3
"""Compile and run the real Java core tests without Gradle or the Android SDK."""
from pathlib import Path
import argparse
import json
import os
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def find_binary(name):
    suffix = ".exe" if os.name == "nt" else ""
    portable = ROOT / ".toolchain" / "jdk" / "bin" / (name + suffix)
    if portable.is_file():
        return str(portable)
    for base in (os.environ.get("GEOPIN_JDK"), os.environ.get("JAVA_HOME")):
        if base:
            candidate = Path(base) / "bin" / (name + suffix)
            if candidate.is_file():
                return str(candidate)
    found = shutil.which(name)
    if found:
        return found
    raise SystemExit(f"{name} tidak ditemukan. Setel GEOPIN_JDK/JAVA_HOME atau jalankan tools/bootstrap_java.py.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("tests", nargs="*", help="Optional fully-qualified JUnit test classes")
    args = parser.parse_args()

    javac = find_binary("javac")
    java = find_binary("java")
    libs = ROOT / ".toolchain" / "libs"
    jars = [libs / "junit-4.13.2.jar", libs / "hamcrest-core-1.3.jar"]
    missing = [str(jar) for jar in jars if not jar.is_file()]
    if missing:
        raise SystemExit("JUnit jar hilang: " + ", ".join(missing) + ". Jalankan tools/bootstrap_java.py.")

    tests = sorted((ROOT / "core" / "src" / "test" / "java").rglob("*Test.java"))
    if not tests:
        raise SystemExit("Tidak ada kelas tes yang ditemukan (bukan berarti lulus).")
    sources = sorted((ROOT / "core" / "src" / "main" / "java").rglob("*.java")) + tests

    out = ROOT / "core" / "build" / "host-classes"
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)

    classpath = os.pathsep.join(map(str, jars))
    compile_command = [javac, "--release", "8", "-encoding", "UTF-8", "-Xlint:all", "-Werror",
                       "-cp", classpath, "-d", str(out)] + [str(path) for path in sources]
    build = subprocess.run(compile_command, cwd=ROOT, capture_output=True, text=True, encoding="utf-8")
    sys.stdout.write(build.stdout)
    sys.stderr.write(build.stderr)
    if build.returncode:
        return build.returncode

    classes = args.tests or [
        ".".join(path.relative_to(ROOT / "core" / "src" / "test" / "java").with_suffix("").parts)
        for path in tests
    ]
    test_command = [java, "-cp", os.pathsep.join([str(out), classpath]), "org.junit.runner.JUnitCore"] + classes
    result = subprocess.run(test_command, cwd=ROOT, capture_output=True, text=True, encoding="utf-8")
    print(result.stdout, end="")
    print(result.stderr, end="", file=sys.stderr)

    report = {
        "command": "python tools/test_core.py" + (" " + " ".join(args.tests) if args.tests else ""),
        "test_classes": classes,
        "source_files": len(sources),
        "exit_code": result.returncode,
        "stdout": result.stdout,
        "stderr": result.stderr,
        "scope": "Eksekusi JVM nyata untuk kode core; BUKAN verifikasi Android/perangkat/APK",
    }
    reports = ROOT / "core" / "build" / "reports"
    reports.mkdir(parents=True, exist_ok=True)
    (reports / "host-tests.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    return result.returncode


if __name__ == "__main__":
    sys.exit(main())
