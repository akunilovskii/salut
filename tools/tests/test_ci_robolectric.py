import hashlib
import io
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
from urllib.error import HTTPError

from tools.ci_robolectric import REPOSITORIES, prepare_runtime


class RobolectricCacheTest(unittest.TestCase):
    def setUp(self):
        directory = TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.cache = Path(directory.name)
        self.version = "14-robolectric-10818077-i7"
        self.jar = b"verified runtime"
        self.checksum = hashlib.sha512(self.jar).hexdigest()
        self.relative = f"org/robolectric/android-all-instrumented/{self.version}/android-all-instrumented-{self.version}.jar"
        self.target = self.cache / self.relative

    @patch("tools.ci_robolectric.urlopen")
    def test_rate_limited_download_uses_mirror_then_works_offline(self, request):
        request.side_effect = [HTTPError("primary", 429, "rate limited", {}, None), io.BytesIO(self.jar)]
        prepare_runtime(self.cache, self.version, self.checksum)
        self.assertEqual(self.target.read_bytes(), self.jar)
        self.assertEqual([call.args[0] for call in request.call_args_list],
                         [f"{repository}/{self.relative}" for repository in REPOSITORIES])
        request.reset_mock()
        prepare_runtime(self.cache, self.version, self.checksum)
        request.assert_not_called()

    @patch("tools.ci_robolectric.urlopen")
    def test_corrupted_cache_is_repaired_only_with_verified_bytes(self, request):
        self.target.parent.mkdir(parents=True)
        self.target.write_bytes(b"broken cache")
        request.side_effect = [io.BytesIO(b"incomplete") for _ in REPOSITORIES]
        with self.assertRaises(RuntimeError):
            prepare_runtime(self.cache, self.version, self.checksum)
        self.assertEqual(self.target.read_bytes(), b"broken cache")
        request.side_effect = [io.BytesIO(self.jar)]
        prepare_runtime(self.cache, self.version, self.checksum)
        self.assertEqual(self.target.read_bytes(), self.jar)
