import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest


SCRIPT = pathlib.Path(__file__).with_name("release-check")


class ReleaseCheckTest(unittest.TestCase):
    def check_release(self, code=10100, notes="Release notes\n",
                      roadmap="## Delivered in v1.1.0\n", expected=(), version="1.1.0"):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory) / "repo"
            (root / "scripts").mkdir(parents=True)
            script = root / "scripts/release-check"
            shutil.copy2(SCRIPT, script)
            (root / "app").mkdir()
            (root / "app/build.gradle.kts").write_text(
                f'android {{\n    defaultConfig {{\n        versionCode = {code}\n'
                f'        versionName = "{version}"\n    }}\n}}\n', encoding="utf-8",
            )
            if notes is not None:
                changelog = root / f"fastlane/metadata/android/en-US/changelogs/{code}.txt"
                changelog.parent.mkdir(parents=True)
                changelog.write_text(notes, encoding="utf-8")
            if roadmap is not None:
                (root / "ROADMAP.md").write_text(roadmap, encoding="utf-8")
            result = subprocess.run(
                [sys.executable, str(script)], cwd=directory,
                capture_output=True, text=True,
            )
            self.assertEqual(1 if expected else 0, result.returncode, result.stdout + result.stderr)
            self.assertEqual("", result.stderr)
            self.assertEqual(len(expected) if expected else 1, len(result.stdout.splitlines()))
            for failure in expected:
                self.assertIn(failure, result.stdout)
            if not expected:
                self.assertIn(f"Release v{version} ({code}) checks passed.", result.stdout)

    def test_success(self):
        self.check_release()

    def test_version_formula(self):
        self.check_release(code=20304, version="2.3.4", roadmap="## Delivered in v2.3.4\n")

    def test_wrong_code(self):
        self.check_release(code=10099, expected=("versionCode for 1.1.0 must be 10100, not 10099",))

    def test_missing_changelog(self):
        self.check_release(notes=None, expected=("Missing changelog",))

    def test_empty_changelog(self):
        self.check_release(notes="", expected=("is empty",))

    def test_whitespace_changelog(self):
        self.check_release(notes=" \n\t", expected=("is empty",))

    def test_too_long_changelog(self):
        self.check_release(notes="x" * 501, expected=("has 501 characters, at most 500 allowed",))

    def test_500_unicode_characters_are_allowed(self):
        self.check_release(notes="é" * 500)

    def test_missing_roadmap_section(self):
        self.check_release(roadmap="## Delivered in v1.0.17\n", expected=("ROADMAP.md is missing heading",))

    def test_missing_roadmap(self):
        self.check_release(roadmap=None, expected=("ROADMAP.md is missing heading",))

    def test_heading_must_match_exactly(self):
        for heading in ("## Delivered in v1.1.01", "### Delivered in v1.1.0", "Text ## Delivered in v1.1.0"):
            with self.subTest(heading=heading):
                self.check_release(roadmap=heading, expected=("ROADMAP.md is missing heading",))

    def test_reports_each_failure(self):
        self.check_release(code=10099, notes=None, roadmap="", expected=(
            "versionCode for 1.1.0 must be 10100, not 10099",
            "Missing changelog", "ROADMAP.md is missing heading",
        ))

    def test_invalid_version(self):
        self.check_release(version="1.1", expected=("literal major.minor.patch versionName",))

    def test_invalid_code(self):
        self.check_release(code="invalid", notes=None, expected=("literal integer versionCode",))


if __name__ == "__main__":
    unittest.main()
