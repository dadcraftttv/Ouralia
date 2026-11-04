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


if __name__ == "__main__":
    unittest.main()
