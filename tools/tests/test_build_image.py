import hashlib
import json
from pathlib import Path
import re
import unittest


class BuildImageContractTest(unittest.TestCase):
    def test_local_and_all_android_ci_jobs_use_the_same_immutable_image(self):
        root = Path(__file__).resolve().parents[2]
        lock = json.loads((root / 'personal/container/image.json').read_text(encoding='utf-8-sig'))
        self.assertRegex(lock['image'], r'^ghcr\.io/akunilovskii/salut-build@sha256:[a-f0-9]{64}$')
        recipe = (root / 'personal/container/Dockerfile').read_bytes()
        self.assertEqual(lock['recipeSha256'], hashlib.sha256(recipe).hexdigest(),
                         'Publish and qualify the changed recipe, then update its image reference.')
        workflow = (root / '.github/workflows/release.yml').read_text(encoding='utf-8')
        jobs = dict(re.findall(r'^  (test|benchmark|release):\n(.*?)(?=^  [a-z][a-z-]*:|\Z)',
                               workflow, re.M | re.S))
        self.assertEqual(set(jobs), {'test', 'benchmark', 'release'})
        for job, body in jobs.items():
            with self.subTest(job=job):
                self.assertIn('    container:\n      image: ' + lock['image'] + '\n', body)


if __name__ == '__main__':
    unittest.main()
