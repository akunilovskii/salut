import unittest

from tools.release_notes import extract_release_notes
from tools.update_manifest import version_from_heading


class ReleaseNotesTest(unittest.TestCase):
    def test_only_selected_version_is_exported_with_full_markdown(self):
        changelog = """# Salut releases

## Unreleased
- Future change.

## 1.18.1 - 2026-11-15

### Fixed
- **Playlist playback:** Full details with [a link](https://example.com).
  Continued explanation.

### Changed
- Second change.

## 1.18.0
- Older change.
"""
        self.assertEqual(extract_release_notes(changelog, "v1.18.1"),
                         "### Fixed\n- **Playlist playback:** Full details with [a link](https://example.com).\n"
                         "  Continued explanation.\n\n### Changed\n- Second change.\n")
        self.assertEqual(extract_release_notes(changelog, "v1.18.0"), "- Older change.\n")

    def test_heading_like_lines_in_code_blocks_do_not_split_versions(self):
        changelog = "## 1.18.1\n```markdown\n## 1.18.0\n```\n- Current change.\n## 1.18.0\n- Old.\n"
        self.assertEqual(extract_release_notes(changelog, "v1.18.1"),
                         "```markdown\n## 1.18.0\n```\n- Current change.\n")

    def test_missing_empty_and_duplicate_sections_are_rejected(self):
        for changelog in ("## Unreleased\n- Draft.\n", "## 1.18.1\n\n## 1.18.0\n- Old.\n",
                          "## 1.18.1\n<!-- pending -->\n", "## 1.18.1\n### Fixed\n",
                          "## 1.18.1\n- A.\n## 1.18.1\n- B.\n"):
            with self.subTest(changelog=changelog), self.assertRaises(ValueError):
                extract_release_notes(changelog, "v1.18.1")

    def test_markdown_indentation_and_more_than_six_changes_are_preserved(self):
        body = "    indented example  \n\n" + "\n".join(f"- Change {index}." for index in range(8))
        self.assertEqual(extract_release_notes("## 1.18.1\n\n" + body + "\n", "v1.18.1"), body + "\n")

    def test_version_prefixes_and_prereleases_do_not_match_stable_tags(self):
        changelog = "## 1.18.10\n- Other.\n## 1.18.1-rc1\n- Preview.\n"
        with self.assertRaises(ValueError):
            extract_release_notes(changelog, "v1.18.1")
        for tag in ("Unreleased", "1.18.1", "v1.18.1-rc1", "v1.18.1\n"):
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                extract_release_notes(changelog, tag)

    def test_feed_and_release_notes_recognize_the_same_version_headings(self):
        self.assertEqual(version_from_heading("## 1.18.1 - 2026-11-15"), "1.18.1")
        self.assertIsNone(version_from_heading("## Unreleased"))
        self.assertIsNone(version_from_heading("## 1.18.1-rc1"))


if __name__ == "__main__":
    unittest.main()
