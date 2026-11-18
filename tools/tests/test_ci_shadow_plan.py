from pathlib import Path
import unittest
from unittest.mock import patch

from tools.ci_shadow_plan import git, pr_diff, propose
from test_ci_metrics import report


class ShadowPlanTest(unittest.TestCase):
    def setUp(self):
        self.report = report("reuse")
        self.report.update(suiteScope="offline", requiredTestCases=3)
        for group in self.report["groups"]:
            suite = group["suites"][0]
            suite["required"] = True
            name = group["name"] + ".ExtraTest"
            group["suites"].append(dict(name=name, required=False, cases=[
                dict(className=name, name="extra", outcome="passed")
            ]))

    def test_phone_code_retains_the_base_and_adds_phone_suites(self):
        plan = propose(["smarttubetv/src/stmobile/java/ui/Feedback.java"], self.report, "abc")
        self.assertEqual(plan["proposedCasesByGroup"], {
            "common": 1, "notification-provider": 1, "phone": 2
        })
        self.assertFalse(plan["executionChanged"])

    def test_common_code_includes_downstream_phone_coverage(self):
        plan = propose(["common/src/main/java/player/State.java"], self.report, "abc")
        self.assertEqual(plan["proposedCases"], 5)
        self.assertEqual(plan["affectedGroups"], ["common", "phone"])

    def test_unclassified_path_overrides_other_narrow_rules(self):
        for path in ("settings.gradle", "smarttubetv/src/stmobile/res/layout/feedback.xml",
                     "MediaServiceCore/youtubeapi/src/main/java/api/Player.java"):
            with self.subTest(path=path):
                plan = propose(["README.md", path], self.report, "abc")
                self.assertEqual(plan["mode"], "full-offline")
                self.assertEqual(plan["proposedCases"], 6)

    def test_missing_required_inventory_cannot_yield_a_narrow_plan(self):
        del self.report["groups"][0]["suites"][0]["required"]
        with self.assertRaises(ValueError):
            propose(["README.md"], self.report, "abc")

    @patch("tools.ci_shadow_plan.subprocess.check_output")
    def test_trusts_only_the_current_ci_checkout(self, check_output):
        checkout = Path.cwd().resolve()
        with patch.dict("os.environ", GITHUB_WORKSPACE=str(checkout)):
            git("rev-list", "HEAD")
        check_output.assert_called_once_with([
            "git", "-c", f"safe.directory={checkout.as_posix()}", "rev-list", "HEAD"
        ])
        with patch.dict("os.environ", GITHUB_WORKSPACE=str(checkout / "other")):
            with self.assertRaises(ValueError):
                git("rev-list", "HEAD")

    @patch("tools.ci_shadow_plan.git")
    def test_diff_uses_verified_merge_parents_and_preserves_both_paths(self, git):
        merge, base, head = (letter * 40 for letter in "abc")
        event = {"pull_request": {"base": {"sha": base}, "head": {"sha": head}}}
        git.side_effect = [(f"{merge} {base} {head}\n").encode(), b"old path\0new path\0"]
        self.assertEqual(pr_diff(event, merge), ["old path", "new path"])
        git.assert_called_with("diff", "--name-only", "-z", "--no-renames", base, merge, "--")
        git.side_effect = [(f"{merge} {head} {base}\n").encode()]
        with self.assertRaises(ValueError):
            pr_diff(event, merge)


if __name__ == "__main__":
    unittest.main()
