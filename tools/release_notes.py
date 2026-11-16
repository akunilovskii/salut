#!/usr/bin/env python3
"""Export one version's Markdown body for GitHub Releases.

Usage: python -m tools.release_notes --tag v1.18.0 --out build/maintenance/release-notes.md
"""
import argparse
import pathlib
import re

from tools.update_manifest import version_from_heading

ROOT = pathlib.Path(__file__).resolve().parent.parent


def extract_release_notes(changelog, tag):
    if not re.fullmatch(r"v\d+\.\d+\.\d+", tag):
        raise ValueError(f"Invalid release tag: {tag}")
    version = tag[1:]
    sections = []
    selected = None
    fence = None
    for line in changelog.splitlines():
        marker = re.match(r"^ {0,3}(`{3,}|~{3,})(.*)$", line)
        if fence is None and line.startswith("## "):
            selected = [] if version_from_heading(line) == version else None
            if selected is not None:
                sections.append(selected)
            continue
        if selected is not None:
            selected.append(line)
        # Heading-like text inside examples belongs to the selected Markdown body.
        if marker:
            run, suffix = marker.groups()
            if fence is None:
                fence = run
            elif run[0] == fence[0] and len(run) >= len(fence) and not suffix.strip():
                fence = None
    if len(sections) != 1:
        raise ValueError(f"Expected exactly one changelog section for {tag}; found {len(sections)}")
    lines = sections[0]
    while lines and not lines[0].strip():
        lines.pop(0)
    while lines and not lines[-1].strip():
        lines.pop()
    body = "\n".join(lines)
    content = re.sub(r"<!--.*?-->", "", body, flags=re.S)
    content = re.sub(r"^#{1,6}[ \t]+.*$", "", content, flags=re.M)
    if not content.strip():
        raise ValueError(f"Changelog section for {tag} is empty")
    return body + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--changelog", type=pathlib.Path, default=ROOT / "CHANGELOG.md")
    parser.add_argument("--out", type=pathlib.Path, required=True)
    args = parser.parse_args()
    try:
        if args.out.resolve() == args.changelog.resolve():
            raise ValueError("Release notes output must not overwrite the changelog")
        body = extract_release_notes(args.changelog.read_text(encoding="utf-8"), args.tag)
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(body, encoding="utf-8")
    except (OSError, ValueError) as error:
        parser.error(str(error))


if __name__ == "__main__":
    main()
