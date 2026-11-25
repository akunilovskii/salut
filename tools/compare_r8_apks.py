"""Build and compare signed arm64 APKs locally.

Run python -m tools.compare_r8_apks from a clean Linux checkout with the Salut
Android toolchain and SALUT_SIGNING_KEY pointing to the retained signing key.
The two APKs stay under smarttubetv/build/outputs/renamed_apks/salut-r8.
"""
import json
import os
import re
import subprocess
from pathlib import Path

from tools.verify_release import ROOT, verify_apk


def git(*arguments):
    return subprocess.check_output(["git", *arguments], cwd=ROOT, text=True).strip()


def compare():
    if git("status", "--porcelain"):
        raise SystemExit("The comparison requires a clean checkout.")
    commit = git("rev-parse", "HEAD")
    identity = json.loads((ROOT / "personal/release.json").read_text(encoding="utf-8"))
    key = Path(os.environ.get("SALUT_SIGNING_KEY") or ROOT / "personal/keys/debug.keystore")
    if not key.is_file():
        raise SystemExit("The retained Salut signing key is required in SALUT_SIGNING_KEY.")

    env = {
        **os.environ,
        "SALUT_SIGNING_KEY": str(key.resolve()),
        "SOURCE_DATE_EPOCH": git("show", "-s", "--format=%ct", commit),
    }
    dist = ROOT / "smarttubetv/build/outputs/renamed_apks/salut-r8"
    sizes = {}
    for mode in ("baseline", "optimized"):
        subprocess.run(
            ["bash", "gradlew", "--console=plain", "--max-workers=4",
             ":smarttubetv:assembleStmobileRelease", "-PsalutRelease", f"-PsalutR8Experiment={mode}"],
            cwd=ROOT, env=env, check=True,
        )
        apk = dist / mode / f'Salut_{identity["versionName"]}_r8_{mode}_arm64-v8a.apk'
        badging = verify_apk(apk, identity)
        native = re.search(r"^native-code: (.+)$", badging, re.MULTILINE)
        if not native or native[1].strip() != "'arm64-v8a'":
            raise ValueError(f"Expected arm64-v8a exclusively: {apk.name}")
        sizes[mode] = apk.stat().st_size

    if git("rev-parse", "HEAD") != commit or git("status", "--porcelain"):
        raise SystemExit("The checkout changed during the comparison.")
    saved = sizes["baseline"] - sizes["optimized"]
    print(f"Commit: {commit}")
    for mode, size in sizes.items():
        print(f"{mode}: {size:,} bytes ({size / 1_000_000:.3f} MB)")
    print(f"Saved: {saved:,} bytes ({saved / 1_000_000:.3f} MB, "
          f"{saved / sizes['baseline']:.2%})")


if __name__ == "__main__":
    compare()
