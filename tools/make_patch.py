#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Generates app/src/main/assets/patches/test_scene.pd.

Pd patch files address objects by index, which is easy to break by hand. This builds the
graph from named nodes instead, so adding an effect is a few lines. Run from anywhere:

    python3 tools/make_patch.py
"""
# The parts live in tools/patchgen/ (split on Oct 22, code health, ROADMAP 22): constants.py (every tuning number,
# DEFAULTS), core.py (the Patch builder, shared helpers), build.py (the chain), and one module per area.
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from patchgen.constants import LEGACY_FX  # noqa: E402
from patchgen.build import build  # noqa: E402

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets",
                   "patches", "test_scene.pd")


if __name__ == "__main__":
    path = os.path.normpath(sys.argv[sys.argv.index("--legacy-fx") + 1] if LEGACY_FX else OUT)
    with open(path, "w") as f:
        f.write(build().text())
    print("wrote", path)
