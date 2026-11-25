#!/usr/bin/env bash
set -euo pipefail

# The pinned build container has Python 3.12 and unzip, but no pip.
deps="$(mktemp -d)"
trap 'rm -rf "$deps"' EXIT
curl --fail --location --retry 3 --max-time 60 \
  'https://files.pythonhosted.org/packages/e6/99/fdf65cc539e266ab93546c261bdf98411fe165c6e4e13f9dbf597097192b/pillow-13.0.0-cp312-cp312-manylinux_2_27_x86_64.manylinux_2_28_x86_64.whl' \
  --output "$deps/pillow.whl"
echo "42d63be3af4f9b951b199b0a9d23365d37ab14687e6d77b74940432c8515c4ca  $deps/pillow.whl" | sha256sum --check
unzip -q "$deps/pillow.whl" -d "$deps/site"
PYTHONPATH="$deps/site" python -S -X utf8 -m unittest discover -s tools/tests
