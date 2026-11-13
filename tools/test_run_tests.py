#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Runner prerequisites must succeed before any suite can use generated/native artifacts."""
import contextlib
import io
import subprocess
import unittest
from unittest.mock import patch

import run_tests


class PrerequisiteTests(unittest.TestCase):
    def run_runner(self, args, failed_command=None):
        def command_result(cmd, **kwargs):
            if cmd == failed_command:
                return subprocess.CompletedProcess(cmd, 2, "build stdout\n", "compiler stderr\n")
            return subprocess.CompletedProcess(cmd, 0, "", "")

        output = io.StringIO()
        with patch.object(run_tests.sys, "argv", ["run_tests.py", *args]), \
                patch.object(run_tests.subprocess, "run", side_effect=command_result) as commands, \
                patch.object(run_tests, "run_patch_sections", return_value=(0, [], 1)) as sections, \
                contextlib.redirect_stdout(output):
            status = run_tests.main()
        return status, output.getvalue(), [c.args[0] for c in commands.call_args_list], sections

    def test_native_build_failure_stops_both_tiers(self):
        for args in ([], ["quick"]):
            with self.subTest(args=args):
                status, output, commands, sections = self.run_runner(args, ["make", "-s", "-C", "dcs"])
                self.assertNotEqual(status, 0)
                self.assertIn("FAIL: make -s -C dcs", output)
                self.assertIn("build stdout\n", output)
                self.assertIn("compiler stderr\n", output)
                self.assertNotIn("ALL PASSED", output)
                self.assertEqual(commands, [["python3", "tools/make_patch.py"], ["make", "-s", "-C", "dcs"]])
                sections.assert_not_called()

    def test_patch_generation_failure_stops_before_native_build(self):
        status, output, commands, sections = self.run_runner([], ["python3", "tools/make_patch.py"])
        self.assertNotEqual(status, 0)
        self.assertIn("FAIL: make_patch.py", output)
        self.assertIn("build stdout\n", output)
        self.assertIn("compiler stderr\n", output)
        self.assertNotIn("ALL PASSED", output)
        self.assertEqual(commands, [["python3", "tools/make_patch.py"]])
        sections.assert_not_called()

    def test_successful_prerequisites_allow_suites_in_both_tiers(self):
        for args in ([], ["quick"]):
            with self.subTest(args=args):
                status, output, commands, sections = self.run_runner(args)
                self.assertEqual(status, 0)
                self.assertIn("ALL PASSED", output)
                self.assertEqual(commands[:2], [["python3", "tools/make_patch.py"], ["make", "-s", "-C", "dcs"]])
                self.assertIn(["tools/javatests/run.sh"], commands)
                self.assertIn(["python3", "tools/test_run_tests.py"], commands)
                self.assertIn(["python3", "tools/test_release.py"], commands)
                self.assertEqual(["desktop/test.sh"] in commands, not args)
                sections.assert_called_once()


class TimeLimitTests(unittest.TestCase):
    """A stuck section fails with its name instead of holding the tier forever (Nov 13)."""
    def test_sh_gives_up_on_a_stuck_command(self):
        import time
        t0 = time.time()
        code, text = run_tests.sh(["sleep", "5"], timeout=0.3)
        self.assertEqual(code, 124)
        self.assertIn("TIMED OUT", text)
        self.assertLess(time.time() - t0, 3)

    def test_a_stuck_section_fails_with_its_title(self):
        import os, tempfile
        calls = []
        def stuck(cmd, **kw):
            calls.append(kw.get("timeout"))
            return 124, "realloc called\nTIMED OUT after 300 s"
        with tempfile.TemporaryDirectory() as d, patch.object(run_tests, "COSTS", os.path.join(d, "costs.json")), \
                patch.object(run_tests, "sh", side_effect=stuck):
            fails, out, n = run_tests.run_patch_sections(lambda t: "7z6. underwater" in t, 1)
        self.assertEqual((fails, n), (1, 1))
        self.assertTrue(calls and calls[0] >= run_tests.SECTION_MIN_LIMIT)
        self.assertIn("[7z6. underwater", out[0])
        self.assertIn("TIMED OUT", out[0])


class ReadsTests(unittest.TestCase):
    """A section runs only the earlier statements it reads from (Nov 13: 7z6 hung pulling in 137 it didn't need)."""
    def reads(self, src):
        import ast
        return run_tests.reads(ast.parse(src).body[0])

    def test_loop_variable_and_body_assignments_are_local(self):
        self.assertEqual(self.reads("for st in (1, 2):\n    a = f(st)\n    check(a)\n"), {"f", "check"})

    def test_read_before_the_loop_sets_it_still_counts(self):
        self.assertEqual(self.reads("for k in ks:\n    g(prev)\n    prev = k\n"), {"ks", "g", "prev"})

    def test_after_an_empty_loop_the_target_counts(self):
        self.assertIn("x", self.reads("for x in xs:\n    pass\nelse:\n    h(x)\n"))

    def test_with_target_is_local(self):
        self.assertEqual(self.reads("with open(p) as fh:\n    t = fh.read()\n    use(t)\n"), {"open", "p", "use"})


if __name__ == "__main__":
    unittest.main()
