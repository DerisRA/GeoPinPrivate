"""Exercise the real tile loader with Android doubles and a controlled HTTP server substitute."""
import os
import subprocess
import tempfile

from test_core import ROOT, find_binary


def main():
    jars = [ROOT / ".toolchain/libs" / name for name in ("junit-4.13.2.jar", "hamcrest-core-1.3.jar")]
    sources = sorted((ROOT / "app/src/mapHostTest/java").rglob("*.java"))
    sources.append(ROOT / "app/src/main/java/dev/geopin/app/ui/TileStore.java")
    classpath = os.pathsep.join(map(str, jars))
    with tempfile.TemporaryDirectory(prefix="geopin-map-tests-") as output:
        subprocess.run([find_binary("javac"), "--release", "8", "-encoding", "UTF-8",
                        "-Xlint:all", "-Werror", "-cp", classpath, "-d", output,
                        *map(str, sources)], check=True)
        return subprocess.run([find_binary("java"), "-cp", os.pathsep.join([output, classpath]),
                               "org.junit.runner.JUnitCore", "dev.geopin.app.ui.TileStoreTest"]).returncode


if __name__ == "__main__":
    raise SystemExit(main())
