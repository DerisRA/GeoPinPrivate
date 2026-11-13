"""Run the production hook's cache logic with host-only runtime/Xposed doubles.

This does not test Android, Xposed hook installation, IPC, or Xposed's field storage.
"""
import os
import subprocess
import tempfile

from test_core import ROOT, find_binary


def main():
    libs = ROOT / ".toolchain" / "libs"
    jars = [libs / name for name in ("junit-4.13.2.jar", "hamcrest-core-1.3.jar", "api-82.jar")]
    sources = sorted((ROOT / "core/src/main/java").rglob("*.java"))
    sources += sorted((ROOT / "app/src/hostTest/java").rglob("*.java"))
    sources.append(ROOT / "app/src/main/java/dev/geopin/app/hook/LocationHook.java")
    classpath = os.pathsep.join(map(str, jars))
    with tempfile.TemporaryDirectory(prefix="geopin-hook-tests-") as output:
        subprocess.run([
            find_binary("javac"), "--release", "8", "-encoding", "UTF-8",
            "-Xlint:all", "-Werror", "-cp", classpath, "-d", output,
            *map(str, sources),
        ], check=True)
        return subprocess.run([
            find_binary("java"), "-cp", os.pathsep.join([output, classpath]),
            "org.junit.runner.JUnitCore", "dev.geopin.app.hook.LocationHookTest",
        ]).returncode


if __name__ == "__main__":
    raise SystemExit(main())
