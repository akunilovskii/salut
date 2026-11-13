import json
import pathlib
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

from tools.export_public_source import publishable, sanitized_source
from tools.update_manifest import parse_changelog, version_code, REPO
from tools.verify_release import verify, ROOT


class PublicReleaseTest(unittest.TestCase):
    def test_inherited_oauth_values_are_replaced_without_damaging_source_urls(self):
        access = b"ya29." + b"A" * 60
        refresh = b"1//" + b"B" * 60
        source = b'token="' + access + b'"; refresh="' + refresh + b'"; https://example.com/1/' + b'C' * 60
        result = sanitized_source(source)
        self.assertNotIn(access, result)
        self.assertNotIn(refresh, result)
        self.assertIn(b'REDACTED_TEST_ACCESS_TOKEN', result)
        self.assertIn(b'REDACTED_TEST_REFRESH_TOKEN', result)
        self.assertIn(b'https://example.com/1/' + b'C' * 60, result)

    def test_signing_material_and_private_state_cannot_be_exported(self):
        for name in ("personal/keys/debug.keystore", "keystore.properties", ".env.production",
                     "AGENTS.md", "PERSONAL.md", "MediaServiceCore/.gitmodules", "signing/release.jks"):
            self.assertFalse(publishable(pathlib.PurePosixPath(name)), name)

    def test_source_assets_and_license_notices_remain_exportable(self):
        for name in ("LICENSE", "personal/assets/salut-wordmark.svg",
                     "SharedModules/j2v8/src/main/jniLibs/arm64-v8a/libj2v8.so"):
            self.assertTrue(publishable(pathlib.PurePosixPath(name)), name)

    def test_feed_targets_our_public_repository(self):
        urls = ET.parse(ROOT / "common/src/main/res/values/update_urls.xml")
        items = urls.findall("./string-array[@name='update_urls']/item")
        self.assertEqual([item.text for item in items],
                         [f"https://github.com/{REPO}/releases/latest/download/salut.json"])
        self.assertEqual(REPO, "akunilovskii/salut")

    def test_release_version_and_notes_match_the_update_protocol(self):
        identity = json.loads((ROOT / "personal/release.json").read_text(encoding="utf-8"))
        self.assertEqual(version_code(identity["versionName"]), identity["versionCode"])
        versions = parse_changelog(ROOT / "CHANGELOG.md")
        self.assertEqual(next(iter(versions)), identity["versionName"])
        self.assertTrue(versions[identity["versionName"]])

    def test_wrong_tag_is_rejected_before_signing_or_sdk_access(self):
        with patch("tools.verify_release.sdk_command") as sdk:
            with self.assertRaisesRegex(ValueError, "Tag does not match"):
                verify(ROOT / "build/nonexistent-release", "v0.0.0")
            sdk.assert_not_called()

    def test_incomplete_release_is_rejected_before_sdk_access(self):
        identity = json.loads((ROOT / "personal/release.json").read_text(encoding="utf-8"))
        with patch("tools.verify_release.sdk_command") as sdk:
            with self.assertRaisesRegex(ValueError, "four expected APKs"):
                verify(ROOT / "build/nonexistent-release", "v" + identity["versionName"])
            sdk.assert_not_called()


if __name__ == "__main__":
    unittest.main()
