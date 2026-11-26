"""Prepare a biweekly release PR and resume publication of its merged revision."""
import argparse
from datetime import date, datetime, timedelta, timezone
import json
import os
from pathlib import Path
import re
import subprocess

from tools.release_notes import extract_release_notes
from tools.update_manifest import version_code

IDENTITY = Path("personal/release.json")
CHANGELOG = Path("CHANGELOG.md")
GRADLE = Path("smarttubetv/build.gradle")
DIST = Path("smarttubetv/build/outputs/renamed_apks/stmobileRelease")
NOTES = Path("build/maintenance/release-notes.md")
FIRST_RELEASE_PERIOD = date(2026, 11, 26)
INTERVAL = timedelta(days=14)


def output(*command):
    return subprocess.check_output(command, text=True, encoding="utf-8").strip()


def run(*command):
    subprocess.run(command, check=True)


def git_remote(*args):
    return output("git", "-c", "credential.helper=", "-c",
                  "credential.helper=!gh auth git-credential", *args)


def period(today):
    elapsed = (today - FIRST_RELEASE_PERIOD) // INTERVAL
    return FIRST_RELEASE_PERIOD + elapsed * INTERVAL if elapsed >= 0 else None


def roll_changelog(changelog, version, release_date):
    heading = re.compile(r"^## Unreleased[ \t]*$", re.M)
    matches = list(heading.finditer(changelog))
    if len(matches) != 1:
        raise ValueError("Expected exactly one Unreleased heading")
    end = matches[0].end()
    rolled = changelog[:end] + f"\n\n## {version} - {release_date.isoformat()}" + changelog[end:]
    try:
        body = extract_release_notes(rolled, f"v{version}")
    except ValueError as error:
        if str(error).endswith(" is empty"):
            return None
        raise
    # The updater recognizes Markdown list items as user-facing notes.
    visible = re.sub(r"<!--.*?-->|^ {0,3}(`{3,}|~{3,}).*?^ {0,3}\1[ \t]*$",
                     "", body, flags=re.S | re.M)
    return rolled if re.search(r"^- [^\s]", visible, re.M) else None


def next_version(identity):
    previous = identity["versionName"]
    major, minor, _ = previous.split(".")
    version = f"{major}.{int(minor) + 1}.0"
    if version_code(previous) != identity["versionCode"] or version_code(version) <= identity["versionCode"]:
        raise ValueError("Release identity has inconsistent version numbers")
    return version


def bump_gradle(gradle, identity, version):
    previous = re.escape(identity["versionName"])
    pair = rf'(\bversionCode\s+){identity["versionCode"]}(\s+versionName\s+"){previous}(")'
    updated, count = re.subn(pair, lambda m: (
        f"{m[1]}{version_code(version)}{m[2]}{version}{m[3]}"), gradle)
    if count != 1:
        raise ValueError("Expected exactly one matching Gradle release identity")
    return updated


def release_state(tag):
    rows = output("gh", "api", "--paginate", "repos/{owner}/{repo}/releases",
                  "--jq", ".[] | {tag_name, draft}")
    for line in rows.splitlines():
        release = json.loads(line)
        if release["tag_name"] == tag:
            return "draft" if release["draft"] else "published"
    return "missing"


def release_files(identity, version, cutoff, changelog, gradle):
    rolled = roll_changelog(changelog, version, cutoff)
    if rolled is None:
        return {}
    updated = dict(identity, versionName=version, versionCode=version_code(version), releasePeriod=str(cutoff))
    return {
        CHANGELOG: rolled,
        GRADLE: bump_gradle(gradle, identity, version),
        IDENTITY: json.dumps(updated, indent=2) + "\n",
    }


def prepare_release_files(identity, version, cutoff):
    boundary = output("git", "rev-list", "--first-parent",
                      f"--before={cutoff}T00:00:00Z", "-1", "HEAD")
    if not boundary or not roll_changelog(output("git", "show", f"{boundary}:{CHANGELOG}"), version, cutoff):
        return False
    files = release_files(identity, version, cutoff, CHANGELOG.read_text(encoding="utf-8"),
                          GRADLE.read_text(encoding="utf-8"))
    for path, content in files.items():
        path.write_text(content, encoding="utf-8", newline="\n")
    return bool(files)


def read_revision(revision, path):
    return subprocess.check_output(["git", "show", f"{revision}:{path.as_posix()}"]).decode("utf-8")


def verify_release_branch(base, head, version, cutoff):
    identity = json.loads(read_revision(base, IDENTITY))
    if next_version(identity) != version:
        raise ValueError("Release branch does not follow its base version")
    files = release_files(identity, version, cutoff, read_revision(base, CHANGELOG), read_revision(base, GRADLE))
    paths = output("git", "diff", "--no-renames", "--name-only", base, head).splitlines()
    if (not files or set(paths) != {p.as_posix() for p in files} or output("git", "diff", "--summary", base, head)
            or any(read_revision(head, path) != content for path, content in files.items())):
        raise ValueError("Release branch differs from the generated release files")


def authorize_release():
    token = os.environ.pop("SALUT_RELEASE_PR_TOKEN", "")
    if not token:
        raise ValueError("Missing authorization for release pull requests")
    os.environ["GH_TOKEN"] = token


def continue_release(pr, identity, version, cutoff, branch):
    if pr["isDraft"]:
        return
    app = os.environ["SALUT_RELEASE_APP_SLUG"]
    if pr["author"]["login"] not in (f"{app}[bot]", f"app/{app}"):
        raise ValueError("Only the release application's own pull requests can merge automatically")
    ref = f"refs/heads/{branch}"
    git_remote("fetch", "--no-tags", "origin", ref)
    head = output("git", "rev-parse", "FETCH_HEAD")
    if head != pr["headRefOid"]:
        return
    main = output("git", "rev-parse", "HEAD")
    base = output("git", "merge-base", main, head)
    # A delayed PR still belongs to its original period even when the next window opens.
    candidate = date.fromisoformat(json.loads(read_revision(head, IDENTITY))["releasePeriod"])
    if (period(candidate) != candidate or candidate > cutoff or
            (identity.get("releasePeriod") and candidate <= date.fromisoformat(identity["releasePeriod"]))):
        raise ValueError("Unexpected period for the pending release")
    verify_release_branch(base, head, version, candidate)
    if base != main:
        if not prepare_release_files(identity, version, candidate):
            return
        # Preserve the previous release head when refreshing it against the latest main.
        run("git", "add", "--", str(CHANGELOG), str(GRADLE), str(IDENTITY))
        tree = output("git", "write-tree")
        refreshed = output("git", "-c", "user.name=Salut Release", "-c",
                           "user.email=41898282+github-actions[bot]@users.noreply.github.com",
                           "commit-tree", tree, "-p", head, "-p", main, "-m", f"Refreshed Salut v{version}")
        authorize_release()
        git_remote("push", "origin", f"{refreshed}:{ref}")
        return
    tests = [check.get("conclusion") for check in pr["statusCheckRollup"] if check.get("name") == "test"]
    if pr["mergeStateStatus"] == "CLEAN" and tests == ["SUCCESS"]:
        authorize_release()
        merged = output("gh", "api", "--method", "PUT", "repos/{owner}/{repo}/pulls/" + str(pr["number"]) + "/merge",
                        "-f", f"sha={head}", "-f", "merge_method=merge", "--jq", ".merged")
        if merged != "true":
            raise ValueError("GitHub did not confirm the release merge")


def prepare(identity, today):
    tag = "v" + identity["versionName"]
    if identity.get("releasePeriod") and release_state(tag) != "published":
        # workflow_dispatch is permitted with GITHUB_TOKEN.
        run("gh", "workflow", "run", "release.yml", "--ref", "main",
            "-f", "validation=release-retry")
        return

    cutoff = period(today)
    last_period = identity.get("releasePeriod")
    if cutoff is None or (last_period and cutoff <= date.fromisoformat(last_period)):
        return

    version = next_version(identity)
    branch = f"salut/release/v{version}"
    existing = json.loads(output("gh", "pr", "list", "--base", "main", "--head", branch, "--state", "open",
                                 "--json", "number,author,isCrossRepository,isDraft,headRefOid,mergeStateStatus,statusCheckRollup",
                                 "--jq", "[.[] | select(.isCrossRepository == false)]"))
    if existing:
        if len(existing) != 1:
            raise ValueError("Ambiguous release pull request")
        continue_release(existing[0], identity, version, cutoff, branch)
        return

    ref = f"refs/heads/{branch}"
    has_branch = bool(git_remote("ls-remote", "--heads", "origin", ref))
    if not has_branch:
        if not prepare_release_files(identity, version, cutoff):
            return
        run("git", "add", "--", str(CHANGELOG), str(GRADLE), str(IDENTITY))
        run("git", "-c", "user.name=Salut Release", "-c",
            "user.email=41898282+github-actions[bot]@users.noreply.github.com",
            "commit", "-m", f"Prepare Salut v{version}")
    authorize_release()
    if not has_branch:
        git_remote("push", "origin", f"HEAD:{ref}")

    run("gh", "pr", "create", "--base", "main", "--head", branch,
        "--title", f"Salut v{version}",
        "--body", "Prepared from the user-facing Unreleased notes. The release train merges the verified revision after the required checks pass.")


def select(identity, retry):
    if not identity.get("releasePeriod"):
        return
    version = identity["versionName"]
    tag = f"v{version}"
    # On main's first-parent history, identify the integration commit, including squash merges.
    revision = output("git", "log", "--first-parent", "-1", "--format=%H",
                      "-G", rf'"versionName": "{re.escape(version)}"', "--", str(IDENTITY))
    if not revision:
        raise ValueError("Cannot locate the merged release revision")
    if not retry and revision != os.environ["GITHUB_SHA"]:
        return
    if release_state(tag) == "published":
        return
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as stream:
        stream.write(f"tag={tag}\nrevision={revision}\n")


def publish(tag, revision):
    ref = f"refs/tags/{tag}"
    existing = git_remote("ls-remote", "origin", ref, ref + "^{}")
    if existing:
        targets = {name: sha for sha, name in (line.split() for line in existing.splitlines())}
        if targets.get(ref + "^{}", targets.get(ref)) != revision:
            raise ValueError(f"{tag} does not point to the tested release revision")
    else:
        run("gh", "api", "--method", "POST", "repos/{owner}/{repo}/git/refs",
            "-f", f"ref={ref}", "-f", f"sha={revision}", "--silent")

    state = release_state(tag)
    if state == "published":
        return
    if state == "missing":
        run("gh", "release", "create", tag, "--verify-tag", "--draft",
            "--title", f"Salut {tag}", "--notes-file", str(NOTES))
    else:
        run("gh", "release", "edit", tag, "--title", f"Salut {tag}", "--notes-file", str(NOTES))

    assets = sorted(DIST.glob(f"Salut_{tag[1:]}_*.apk")) + [DIST / "salut.json", DIST / "SHA256SUMS"]
    run("gh", "release", "upload", tag, "--clobber", *map(str, assets))
    run("gh", "release", "edit", tag, "--draft=false", "--latest")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("prepare")
    select_parser = commands.add_parser("select")
    select_parser.add_argument("--retry", action="store_true")
    publish_parser = commands.add_parser("publish")
    publish_parser.add_argument("--tag", required=True)
    publish_parser.add_argument("--revision", required=True)
    args = parser.parse_args()
    if args.command == "publish":
        publish(args.tag, args.revision)
    else:
        identity = json.loads(IDENTITY.read_text(encoding="utf-8"))
        if args.command == "prepare":
            prepare(identity, datetime.now(timezone.utc).date())
        else:
            select(identity, args.retry)


if __name__ == "__main__":
    main()
