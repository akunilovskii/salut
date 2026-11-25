"""Validate signed release APKs before generating the public update feed."""
import argparse
import hashlib
import json
import os
import pathlib
import re
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent


def sdk_command(name, *arguments):
    sdk = pathlib.Path(os.environ["ANDROID_HOME"])
    suffix = ".bat" if name == "apksigner" and os.name == "nt" else ".exe" if os.name == "nt" else ""
    tool = sdk / "build-tools" / "37.0.0" / (name + suffix)
    command = [str(tool), *map(str, arguments)]
    if suffix == ".bat":
        command = ["cmd.exe", "/d", "/c", *command]
    return subprocess.check_output(command, text=True, encoding="utf-8", errors="replace")


def verify_apk(apk, identity):
    version = identity["versionName"]
    badging = sdk_command("aapt", "dump", "badging", apk)
    package = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not package or package.groups() != (identity["packageName"], str(identity["versionCode"]), version):
        raise ValueError(f"Unexpected package or version: {apk.name}")
    if "application-debuggable" in badging:
        raise ValueError(f"Public release is debuggable: {apk.name}")
    signatures = sdk_command("apksigner", "verify", "--print-certs", apk)
    digests = re.findall(r"certificate SHA-256 digest: ([0-9a-fA-F]+)", signatures)
    if not digests or set(value.lower() for value in digests) != {identity["certificateSha256"]}:
        raise ValueError(f"Unexpected signing certificate: {apk.name}")
    print(f"Verified package, version and certificate: {apk.name}")
    return badging


def verify(dist, tag):
    identity = json.loads((ROOT / "personal/release.json").read_text(encoding="utf-8"))
    version = identity["versionName"]
    if tag != "v" + version:
        raise ValueError("Tag does not match personal/release.json")
    apks = [dist / f"Salut_{version}_{abi}.apk"
            for abi in ("arm64-v8a", "armeabi-v7a", "x86", "universal")]
    if set(dist.glob("*.apk")) != set(apks) or not all(apk.is_file() for apk in apks):
        raise ValueError("Release must contain exactly the four expected APKs")
    for apk in apks:
        verify_apk(apk, identity)
    subprocess.run([sys.executable, str(ROOT / "tools/update_manifest.py"),
                    "--tag", tag, "--dist", str(dist), "--out", str(dist / "salut.json")], check=True)
    checksums = [f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}"
                 for path in [*apks, dist / "salut.json"]]
    (dist / "SHA256SUMS").write_text("\n".join(checksums) + "\n", encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dist", required=True, type=pathlib.Path)
    parser.add_argument("--tag", required=True)
    arguments = parser.parse_args()
    verify(arguments.dist, arguments.tag)
