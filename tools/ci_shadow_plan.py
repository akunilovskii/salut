"""Report which Android suites a PR could run. Never controls test execution."""

import json
import os
from pathlib import Path
import re
import subprocess
from collections import Counter

from tools.ci_metrics import inventory


REPORT = Path("build/maintenance/test-metrics.json")
OUTPUT = REPORT.with_name("shadow-test-plan.json")

# Initial rules use module boundaries. The phone module consumes common, and
# both consume youtubeapi, so changes in youtubeapi cannot narrow this corpus.
RULES = (
    (re.compile(r"smarttubetv/src/(?:stmobile|main|test)/(?:java|kotlin)/.+\.(?:java|kt)\Z"),
     {"phone"}, "Phone code or unit tests"),
    (re.compile(r"common/src/(?:main|test)/(?:java|kotlin)/.+\.(?:java|kt)\Z"),
     {"common", "phone"}, "Common code or unit tests and downstream phone tests"),
    (re.compile(r"(?:README\.md|CHANGELOG\.md)\Z"), set(), "Documentation with separate tooling checks"),
)


def git(*args):
    command = ["git"]
    if workspace := os.environ.get("GITHUB_WORKSPACE"):
        checkout = Path(workspace).resolve()
        if checkout != Path.cwd().resolve():
            raise ValueError("Expected to run from the GitHub checkout")
        # Container jobs can read a checkout owned by the host runner.
        command.extend(["-c", f"safe.directory={checkout.as_posix()}"])
    return subprocess.check_output([*command, *args])


def pr_diff(event, revision):
    pr = event["pull_request"]
    expected = [revision, pr["base"]["sha"], pr["head"]["sha"]]
    if any(not re.fullmatch(r"[0-9a-f]{40}", item) for item in expected):
        raise ValueError("Invalid pull request revision")
    parents = git("rev-list", "--parents", "-n", "1", "HEAD").decode("ascii").split()
    if parents != expected:
        raise ValueError("Checkout does not match the PR merge commit and its parents")
    # Explicit base avoids needing a merge base in the shallow checkout.
    # Disabling rename detection routes both the old and new paths.
    diff = git("diff", "--name-only", "-z", "--no-renames", expected[1], revision, "--")
    return [name.decode("utf-8") for name in diff.split(b"\0") if name]


def propose(paths, report, revision):
    counts = inventory(report)
    if (report.get("sourceRevision") != revision or report.get("sourceDirty")
            or report.get("suiteScope") != "offline"):
        raise ValueError("Expected a fresh offline report for the checked out PR revision")
    required = sum(len(suite["cases"]) for group in report["groups"] for suite in group["suites"]
                   if suite.get("required") is True)
    if (not required or required != report.get("requiredTestCases")
            or any(type(suite.get("required")) is not bool
                   for group in report["groups"] for suite in group["suites"])):
        raise ValueError("Required suite inventory is incomplete")

    groups = set()
    reasons = set()
    unmapped = []
    fallback = not paths
    if fallback:
        reasons.add("No changed paths established")
    for path in paths:
        for pattern, affected, reason in RULES:
            if pattern.fullmatch(path):
                groups.update(affected)
                reasons.add(reason)
                break
        else:
            fallback = True
            unmapped.append(path)
            reasons.add("A changed path has no verified narrow rule")

    selected = []
    totals = Counter()
    for group in report["groups"]:
        for suite in group["suites"]:
            if fallback or group["name"] in groups or suite["required"]:
                selected.append({"group": group["name"], "suite": suite["name"],
                                 "cases": len(suite["cases"]), "required": suite["required"]})
                totals[group["name"]] += len(suite["cases"])

    selected_cases = sum(totals.values())
    total = sum(counts.values())
    return dict(mode="full-offline" if selected_cases == total else "selected",
                executionChanged=False, revision=revision, changedPaths=sorted(set(paths)),
                affectedGroups=sorted(groups), unmappedPaths=sorted(set(unmapped)),
                reasons=sorted(reasons), requiredCases=required,
                proposedCases=selected_cases, fullCases=total, proposedCasesByGroup=dict(totals),
                suites=selected)


def main():
    revision = os.environ.get("GITHUB_SHA", "")
    try:
        event = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
        paths = pr_diff(event, revision)
        report = json.loads(REPORT.read_text(encoding="utf-8-sig"))
        plan = propose(paths, report, revision)
    except (OSError, KeyError, ValueError, TypeError, subprocess.SubprocessError):
        plan = dict(mode="full-offline", executionChanged=False,
                    reasons=["PR diff or complete offline inventory could not be verified"])
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(plan, indent=2) + "\n", encoding="utf-8")
    lines = ["### Proposed Android test selection (shadow mode)", "",
             "The full offline check runs independently of this proposal.", ""]
    if "proposedCases" in plan:
        lines.append(f"Proposed **{plan['proposedCases']} / {plan['fullCases']}** cases, "
                     f"including all {plan['requiredCases']} required checks.")
        lines.append("")
    lines.extend(f"- {reason}" for reason in plan["reasons"])
    lines.extend(["", "See the shadow-test-plan artifact for the proposed suite list."])
    if summary := os.environ.get("GITHUB_STEP_SUMMARY"):
        with Path(summary).open("a", encoding="utf-8") as output:
            output.write("\n".join(lines) + "\n")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
