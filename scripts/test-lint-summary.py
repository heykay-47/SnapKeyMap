import pathlib
import subprocess
import tempfile
import unittest


SCRIPT = pathlib.Path(__file__).with_name("lint-summary.sh")


class LintSummaryTest(unittest.TestCase):
    def check_summary(self, outcome, extensions, expected, exit_code):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            for extension in extensions:
                (root / f"lint-results-release.{extension}").write_text("report")
            summary = root / "summary.md"
            result = subprocess.run(
                ["bash", str(SCRIPT), outcome, str(root), str(summary)],
                capture_output=True, text=True,
            )
            self.assertEqual(exit_code, result.returncode, result.stderr)
            text = summary.read_text()
            self.assertIn(expected, text)
            if outcome != "success" or len(extensions) != 2:
                self.assertNotIn("Lint passed", text)
            if exit_code:
                self.assertIn("::warning::Expected lint report", result.stdout)

    def test_success_with_both_reports(self):
        self.check_summary("success", ["html", "xml"], "Lint passed", 0)

    def test_findings_remain_advisory(self):
        self.check_summary("failure", ["html", "xml"], "Lint failed", 0)

    def test_success_without_xml_is_unavailable(self):
        self.check_summary("success", ["html"], "Lint unavailable", 1)

    def test_failure_without_reports_is_explicit(self):
        self.check_summary("failure", [], "Lint failed", 1)

    def test_skipped_is_unavailable(self):
        self.check_summary("skipped", [], "Lint unavailable", 1)


if __name__ == "__main__":
    unittest.main()
