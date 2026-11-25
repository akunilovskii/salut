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
            "2026-12-08": None,
            "2026-12-09": "2026-12-09",
            "2026-12-22": "2026-12-09",
            "2026-12-23": "2026-12-23",
            "2027-01-05": "2026-12-23",
            "2027-01-06": "2027-01-06",
        }.items():
            with self.subTest(day=day):
                result = train.period(date.fromisoformat(day))
                self.assertEqual(str(result) if result else None, expected)

    def test_placeholder_notes_are_skipped(self):
        for content in ("", "### New\n<!--\n- Example\n-->\n", "```md\n- Example\n```\n"):
            with self.subTest(content=content):
                changelog = f"## Unreleased\n\n{content}\n## 1.18.0\n\n- Older\n"
                self.assertIsNone(train.roll_changelog(changelog, "1.18.1"))

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
                  patch.object(train, "output", side_effect=["", "boundary", changelog]),
                  patch.object(train, "run") as run,
                  patch.dict(os.environ, {"SALUT_RELEASE_PR_TOKEN": "test-token"})):
                train.prepare(identity, date(2026, 12, 9))
            self.assertEqual(json.loads(files["IDENTITY"].read_text(encoding="utf-8")), {
                "versionName": "1.18.1", "versionCode": 11801, "releasePeriod": "2026-12-09",
            })
            self.assertEqual(files["GRADLE"].read_text(encoding="utf-8"),
                             'versionCode 11801\nversionName "1.18.1"\n')
            self.assertEqual(update_manifest.parse_changelog(files["CHANGELOG"])["1.18.1"], ["New"])
            notes = train.extract_release_notes(files["CHANGELOG"].read_text(encoding="utf-8"), "v1.18.1")
            self.assertEqual(notes, "- **New** details.\n  Continued.\n")
            git.assert_called_with("push", "origin", "HEAD:refs/heads/salut/release/v1.18.1")
            self.assertEqual(run.call_args.args[:3], ("gh", "pr", "create"))

    def test_empty_period_and_open_pull_request_do_not_write(self):
        for responses in (["25"], ["", "boundary", "## Unreleased\n\n<!-- empty -->"]):
            with (patch.object(train, "output", side_effect=responses),
                  patch.object(train, "git_remote", return_value=""), patch.object(train, "run") as run):
                train.prepare({"versionName": "1.18.0", "versionCode": 11800}, date(2026, 12, 9))
            run.assert_not_called()

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
