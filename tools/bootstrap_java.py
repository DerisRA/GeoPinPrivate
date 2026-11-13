#!/usr/bin/env python3
"""Download checksum-verified portable Java 17 and test jars. No Android SDK."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path, PurePosixPath
import hashlib
import json
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / ".toolchain"
LIBS = TOOLS / "libs"


def read_url(url):
    return subprocess.run(["curl", "-fLsS", "--connect-timeout", "20", "--max-time", "120", url], check=True, capture_output=True).stdout


def download(url, destination, checksum, algorithm="sha256"):
    destination.parent.mkdir(parents=True, exist_ok=True)
    if destination.exists():
        digest = hashlib.new(algorithm, destination.read_bytes()).hexdigest()
        if digest == checksum:
            print("Verified existing", destination.name, flush=True)
            return
        raise RuntimeError("Existing file has an unexpected checksum: " + str(destination))
    temporary = destination.with_suffix(destination.suffix + ".part")
    digest = hashlib.new(algorithm)
    try:
        subprocess.run(["curl", "-fLsS", "--connect-timeout", "20", "--max-time", "600", url, "-o", str(temporary)], check=True)
        with temporary.open("rb") as content:
            for block in iter(lambda: content.read(1024 * 1024), b""):
                digest.update(block)
        if digest.hexdigest() != checksum:
            raise RuntimeError("Checksum mismatch for " + destination.name)
        temporary.replace(destination)
        print("Downloaded and verified", destination.name, flush=True)
    finally:
        if temporary.exists():
            temporary.unlink()


def java():
    if (TOOLS / "jdk" / "bin" / "javac.exe").is_file():
        print("Portable JDK already present", flush=True)
        return
    data = json.loads(read_url("https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=windows&vendor=eclipse"))
    package = data[0]["binary"]["package"]
    archive = TOOLS / "downloads" / package["name"]
    download(package["link"], archive, package["checksum"])
    staging = TOOLS / "jdk-unpack"
    staging.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as bundle:
        for info in bundle.infolist():
            path = PurePosixPath(info.filename)
            if path.is_absolute() or ".." in path.parts or "\\" in info.filename:
                raise RuntimeError("Unsafe archive member")
            if (info.external_attr >> 16) & 0o170000 == 0o120000:
                raise RuntimeError("Symlink in JDK archive")
        bundle.extractall(staging)
    roots = [p for p in staging.iterdir() if (p / "bin" / "javac.exe").exists()]
    if len(roots) != 1:
        raise RuntimeError("Expected one JDK directory")
    shutil.move(str(roots[0]), str(TOOLS / "jdk"))
    staging.rmdir()
    archive.unlink()
    print("Portable JDK ready:", data[0]["version"]["semver"], flush=True)


def jar(path):
    url = "https://repo.maven.apache.org/maven2/" + path
    checksum = read_url(url + ".sha1").decode().strip().split()[0]
    download(url, LIBS / path.rsplit("/", 1)[-1], checksum, "sha1")


if __name__ == "__main__":
    TOOLS.mkdir(parents=True, exist_ok=True)
    with ThreadPoolExecutor(max_workers=3) as pool:
        jobs = [pool.submit(java)]
        for path in ("junit/junit/4.13.2/junit-4.13.2.jar", "org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3.jar"):
            jobs.append(pool.submit(jar, path))
        for job in jobs:
            job.result()
    print("Done. Android SDK was not downloaded; no SDK licenses were accepted.")
