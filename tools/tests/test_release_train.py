from datetime import date
import json
import os
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import call, patch

from tools import release_train as train
from tools import update_manifest, verify_release

ROOT = Path(__file__).resolve().parents[2]


class ReleaseTrainTest(unittest.TestCase):
    def test_full_intervals_cross_month_and_year_boundaries(self):
        for day, expected in {
            "2026-11-25": None,
            "2026-11-26": "2026-11-26",
            "2026-12-09": "2026-11-26",
            "2026-12-10": "2026-12-10",
            "2026-12-24": "2026-12-24",
            "2027-01-06": "2026-12-24",
            "2027-01-07": "2027-01-07",
        }.items():
            with self.subTest(day=day):
                result = train.period(date.fromisoformat(day))
                self.assertEqual(str(result) if result else None, expected)

    def test_placeholder_notes_are_skipped(self):
        for content in ("", "### New\n<!--\n- Example\n-->\n", "```md\n- Example\n```\n"):
            with self.subTest(content=content):
                changelog = f"## Unreleased\n\n{content}\n## 1.18.0\n\n- Older\n"
                self.assertIsNone(train.roll_changelog(changelog, "1.19.0", date(2026, 11, 26)))

    def test_preparation_updates_note_version_and_code_together(self):
        identity = {"versionName": "1.18.0", "versionCode": 11800}
        changelog = "## Unreleased\n\n- **New** details.\n  Continued.\n\n## 1.18.0\n\n- Older\n"
        gradle = 'versionCode 11800\nversionName "1.18.0"\n'
        with TemporaryDirectory() as tmp:
            base = Path(tmp)
            files = {key: base / key for key in ("IDENTITY", "CHANGELOG", "GRADLE")}
            for key, content in (("CHANGELOG", changelog), ("GRADLE", gradle)):
                files[key].write_text(content, encoding="utf-8")
            with (patch.multiple(train, **files), patch.object(train, "git_remote", return_value="") as git,
                  patch.object(train, "output", side_effect=["[]", "boundary", changelog]),
                  patch.object(train, "run") as run,
                  patch.dict(os.environ, {"SALUT_RELEASE_PR_TOKEN": "test-token"})):
                train.prepare(identity, date(2026, 11, 26))
            self.assertEqual(json.loads(files["IDENTITY"].read_text(encoding="utf-8")), {
                "versionName": "1.19.0", "versionCode": 11900, "releasePeriod": "2026-11-26",
            })
            self.assertEqual(files["GRADLE"].read_text(encoding="utf-8"),
                             'versionCode 11900\nversionName "1.19.0"\n')
            self.assertEqual(update_manifest.parse_changelog(files["CHANGELOG"])["1.19.0"], ["New"])
            changelog = files["CHANGELOG"].read_text(encoding="utf-8")
            self.assertIn("## 1.19.0 - 2026-11-26\n", changelog)
            notes = train.extract_release_notes(changelog, "v1.19.0")
            self.assertEqual(notes, "- **New** details.\n  Continued.\n")
            git.assert_called_with("push", "origin", "HEAD:refs/heads/salut/release/v1.19.0")
            self.assertEqual(run.call_args.args[:3], ("gh", "pr", "create"))

    def test_empty_period_and_open_pull_request_do_not_write(self):
        for responses in (['[{"number":25,"isDraft":true}]'], ["[]", "boundary", "## Unreleased\n\n<!-- empty -->"]):
            with (patch.object(train, "output", side_effect=responses),
                  patch.object(train, "git_remote", return_value=""), patch.object(train, "run") as run):
                train.prepare({"versionName": "1.18.0", "versionCode": 11800}, date(2026, 12, 9))
            run.assert_not_called()

    def test_minor_release_resets_patch_and_checks_previous_android_code(self):
        self.assertEqual(train.next_version({"versionName": "1.19.4", "versionCode": 11904}), "1.20.0")
        with self.assertRaises(ValueError):
            train.next_version({"versionName": "1.19.4", "versionCode": 11900})

    def test_daily_check_skips_an_already_published_period(self):
        identity = {"versionName": "1.19.0", "versionCode": 11900, "releasePeriod": "2026-11-26"}
        with patch.object(train, "release_state", return_value="published"), patch.object(train, "output") as output:
            train.prepare(identity, date(2026, 12, 9))
            output.assert_not_called()

    def test_automatic_merge_requires_exact_release_head_and_successful_test(self):
        identity = {"versionName": "1.19.0", "versionCode": 11900}
        cutoff = date(2026, 12, 10)
        head, base = "c" * 40, "b" * 40
        branch = "salut/release/v1.20.0"
        for fetched, state, check, expected in (
            (head, "CLEAN", "SUCCESS", True), (head, "BLOCKED", "SUCCESS", False),
            (head, "CLEAN", "FAILURE", False), ("d" * 40, "CLEAN", "SUCCESS", False),
        ):
            pr = dict(number=41, author={"login":"release[bot]"}, isDraft=False, headRefOid=head, mergeStateStatus=state,
                      statusCheckRollup=[dict(name="test", conclusion=check)])
            with (patch.dict(os.environ, SALUT_RELEASE_APP_SLUG="release"),
                  patch.object(train, "read_revision", return_value='{"releasePeriod":"2026-12-10"}'),
                  patch.object(train, "output", side_effect=[fetched, base, base, "true"]) as command,
                  patch.object(train, "git_remote"), patch.object(train, "verify_release_branch") as verify,
                  patch.object(train, "authorize_release") as authorize, patch.object(train, "run") as run):
                train.continue_release(pr, identity, "1.20.0", date(2026, 12, 24), branch)
                if expected:
                    verify.assert_called_once_with(base, head, "1.20.0", cutoff)
                    command.assert_called_with("gh", "api", "--method", "PUT", "repos/{owner}/{repo}/pulls/41/merge",
                                               "-f", f"sha={head}", "-f", "merge_method=merge", "--jq", ".merged")
                else:
                    authorize.assert_not_called()
                    run.assert_not_called()

    def test_release_branch_rejects_changes_outside_generated_files_and_modes(self):
        cutoff = date(2026, 12, 10)
        source = {train.IDENTITY: '{"versionName":"1.19.0","versionCode":11900}',
                  train.GRADLE: 'versionCode 11900\nversionName "1.19.0"\n',
                  train.CHANGELOG: "## Unreleased\n\n- New feature.\n"}
        generated = train.release_files(json.loads(source[train.IDENTITY]), "1.20.0", cutoff,
                                        source[train.CHANGELOG], source[train.GRADLE])
        paths = "\n".join(path.as_posix() for path in generated)
        for listing, summary, script, valid in (
            (paths, "", "", True), (paths + "\nother.py", "", "", False),
            (paths, " mode change 100644 => 100755", "", False), (paths, "", "// edited", False),
        ):
            candidate = dict(generated)
            candidate[train.GRADLE] += script
            def read(ref, path):
                return (source if ref == "base" else candidate)[path]
            with patch.object(train, "read_revision", side_effect=read), patch.object(train, "output", side_effect=[listing, summary]):
                if valid:
                    train.verify_release_branch("base", "head", "1.20.0", cutoff)
                else:
                    with self.assertRaises(ValueError):
                        train.verify_release_branch("base", "head", "1.20.0", cutoff)

    def test_behind_release_is_refreshed_and_must_pass_a_new_check(self):
        pr = dict(number=41, isDraft=False, author={"login":"app/release"}, headRefOid="old", mergeStateStatus="BEHIND", statusCheckRollup=[])
        with (patch.dict(os.environ, SALUT_RELEASE_APP_SLUG="release"),
              patch.object(train, "read_revision", return_value='{"releasePeriod":"2026-12-10"}'),
              patch.object(train, "output", side_effect=["old", "main", "base", "tree", "new"]) as git,
              patch.object(train, "git_remote") as remote, patch.object(train, "verify_release_branch") as verify,
              patch.object(train, "prepare_release_files", return_value=True), patch.object(train, "authorize_release"),
              patch.object(train, "run") as run):
            train.continue_release(pr, {}, "1.20.0", date(2026, 12, 10), "salut/release/v1.20.0")
            verify.assert_called_once_with("base", "old", "1.20.0", date(2026, 12, 10))
            self.assertIn(("tree", "-p", "old", "-p", "main"), [c.args[6:11] for c in git.call_args_list])
            remote.assert_called_with("push", "origin", "new:refs/heads/salut/release/v1.20.0")
            run.assert_called_once_with("git", "add", "--", str(train.CHANGELOG), str(train.GRADLE), str(train.IDENTITY))

    def test_release_service_leaves_a_human_authored_pr_alone(self):
        with patch.dict(os.environ, SALUT_RELEASE_APP_SLUG="release"), patch.object(train, "git_remote") as git:
            with self.assertRaisesRegex(ValueError, "own pull requests"):
                train.continue_release({"isDraft":False,"author":{"login":"owner"}}, {}, "1.20.0",
                                       date(2026, 12, 10), "salut/release/v1.20.0")
            git.assert_not_called()

    def test_merged_unpublished_release_dispatches_retry(self):
        with patch.object(train, "release_state", return_value="draft"), patch.object(train, "run") as run:
            train.prepare({"versionName": "1.18.1", "releasePeriod": "2026-12-09"}, date(2027, 1, 6))
        run.assert_called_once_with("gh", "workflow", "run", "release.yml", "--ref", "main",
                                    "-f", "validation=release-retry")

    def test_retry_resumes_draft_and_failed_upload_never_publishes(self):
        tag = "v1.18.1"
        remote = "tested\trefs/tags/v1.18.1"
        with (patch.object(train, "git_remote", return_value=remote),
              patch.object(train, "release_state", return_value="draft"),
              patch.object(train, "run", side_effect=[None, subprocess.CalledProcessError(1, "gh")]) as run):
            with self.assertRaises(subprocess.CalledProcessError):
                train.publish(tag, "tested")
        self.assertEqual(len(run.call_args_list), 2)
        self.assertEqual(run.call_args.args[:4], ("gh", "release", "upload", tag))

        with (patch.object(train, "git_remote", return_value=remote),
              patch.object(train, "release_state", return_value="draft"),
              patch.object(train, "run") as run):
            train.publish(tag, "tested")
        self.assertEqual(run.call_args_list[-1],
                         call("gh", "release", "edit", tag, "--draft=false", "--latest"))

    def test_tag_conflict_is_rejected_before_release_mutations(self):
        with (patch.object(train, "git_remote", return_value="other\trefs/tags/v1.18.1"),
              patch.object(train, "run") as run):
            with self.assertRaisesRegex(ValueError, "tested release revision"):
                train.publish("v1.18.1", "tested")
        run.assert_not_called()

    def test_signed_apk_identity_and_manifest_use_the_same_tag(self):
        identity = json.loads((ROOT / "personal/release.json").read_text(encoding="utf-8"))
        version = identity["versionName"]
        tag = f"v{version}"
        with TemporaryDirectory() as tmp:
            dist = Path(tmp)
            for abi in ("arm64-v8a", "armeabi-v7a", "x86", "universal"):
                (dist / f"Salut_{version}_{abi}.apk").write_bytes(b"mock apk")
            badging = (f"package: name='{identity['packageName']}' versionCode='{identity['versionCode']}' "
                       f"versionName='{version}'")
            signature = f"certificate SHA-256 digest: {identity['certificateSha256']}"
            with patch.object(verify_release, "sdk_command", side_effect=[badging, signature] * 4):
                verify_release.verify(dist, tag)
            manifest = json.loads((dist / "salut.json").read_text(encoding="utf-8"))
            self.assertEqual(manifest[version]["versionCode"], identity["versionCode"])
            self.assertTrue(manifest[version]["changelog"])
            url = manifest["package"]["downloadUrlList"][0]
            self.assertTrue(url.endswith(f"/{tag}/Salut_{version}_universal.apk"))
            self.assertIn("  salut.json\n", (dist / "SHA256SUMS").read_text(encoding="utf-8"))
