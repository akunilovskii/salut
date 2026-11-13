"""Export reviewed tracked source, flattening dependencies and excluding private state."""
import argparse
import pathlib
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parent.parent
PRIVATE_NAMES = {"AGENTS.md", "PERSONAL.md", ".gitmodules", "local.properties", "keystore.properties"}
PRIVATE_SUFFIXES = {".keystore", ".jks", ".p12", ".pem", ".key", ".http"}
OAUTH_ACCESS = re.compile(rb"ya29\.[A-Za-z0-9_.-]{20,}")
OAUTH_REFRESH = re.compile(rb"(?<![A-Za-z0-9_./-])1/{1,2}[A-Za-z0-9_-]{30,}")


def sanitized_source(data):
    # Upstream authentication tests contain historical live credentials. Keep only test labels.
    data = OAUTH_ACCESS.sub(b"REDACTED_TEST_ACCESS_TOKEN", data)
    return OAUTH_REFRESH.sub(b"REDACTED_TEST_REFRESH_TOKEN", data)


def publishable(path):
    return (path.name not in PRIVATE_NAMES
            and path.suffix.lower() not in PRIVATE_SUFFIXES
            and not path.name.startswith(".env")
            and "keys" not in path.parts
            and path.name != "gradle-wrapper.jar")


def tracked_files(repository, prefix=pathlib.PurePosixPath()):
    records = subprocess.check_output(
        ["git", "-C", str(repository), "ls-files", "--stage", "-z"])
    for record in records.split(b"\0"):
        if not record:
            continue
        metadata, raw_path = record.split(b"\t", 1)
        mode, commit, _ = metadata.decode().split()
        path = pathlib.PurePosixPath(raw_path.decode("utf-8"))
        full_path = prefix / path
        if mode == "160000":
            module = repository / path
            head = subprocess.check_output(["git", "-C", str(module), "rev-parse", "HEAD"], text=True).strip()
            if head != commit:
                raise ValueError(f"Dependency does not match the reviewed revision: {full_path}")
            yield from tracked_files(module, full_path)
        elif mode in {"100644", "100755"} and publishable(full_path):
            yield repository / path, full_path, mode
        elif mode == "120000":
            raise ValueError(f"Review symbolic link before publication: {full_path}")


def export(destination):
    destination = destination.resolve()
    if not destination.is_relative_to((ROOT / "build").resolve()):
        raise ValueError("Public staging must stay inside this project's ignored build directory")
    if destination.exists():
        raise ValueError("Use a new staging directory; existing exports are never overwritten")
    files = list(tracked_files(ROOT))
    destination.mkdir(parents=True)
    for source, relative, mode in files:
        data = sanitized_source(source.read_bytes())
        if relative.name == "gradlew" or relative.suffix == ".sh":
            data = data.replace(b"\r\n", b"\n")
        if len(data) > 50 * 1024 * 1024:
            raise ValueError(f"Review large source input before publication: {relative}")
        target = destination / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        if mode == "100755":
            target.chmod(0o755)
    print(f"Exported {len(files)} reviewed source files to {destination}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("destination", type=pathlib.Path)
    export(parser.parse_args().destination)
