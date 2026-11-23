"""Prepare the instrumented Android JARs used by Robolectric's Maven resolver."""

import hashlib
import json
import shutil
from http.client import HTTPException
from pathlib import Path
from tempfile import TemporaryDirectory
from urllib.request import urlopen


REPOSITORIES = (
    "https://repo.maven.apache.org/maven2",
    "https://maven-central.storage-download.googleapis.com/maven2",
)


def digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha512").hexdigest()


def prepare_runtime(cache, version, expected):
    artifact = "android-all-instrumented"
    relative = f"org/robolectric/{artifact}/{version}/{artifact}-{version}.jar"
    target = cache / relative
    if target.is_file() and digest(target) == expected:
        print(f"{version}: verified cached JAR", flush=True)
        return

    target.parent.mkdir(parents=True, exist_ok=True)
    # Stage on the same filesystem so the resolver only sees a complete replacement.
    with TemporaryDirectory(dir=target.parent) as staging:
        candidate = Path(staging) / target.name
        for repository in REPOSITORIES:
            print(f"{version}: downloading from {repository}", flush=True)
            try:
                with urlopen(f"{repository}/{relative}", timeout=45) as response, candidate.open("wb") as output:
                    shutil.copyfileobj(response, output, length=1024 * 1024)
            except (OSError, HTTPException) as error:
                print(f"{version}: download failed: {error}", flush=True)
                continue
            if digest(candidate) == expected:
                candidate.replace(target)
                return
            print(f"{version}: downloaded JAR failed SHA-512 verification", flush=True)
    raise RuntimeError(f"Could not obtain a verified Robolectric runtime: {version}")


if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1]
    runtimes = json.loads((root / "personal/robolectric-runtimes.json").read_text(encoding="utf-8"))
    for version, checksum in runtimes.items():
        prepare_runtime(root / "build/robolectric-maven", version, checksum)
