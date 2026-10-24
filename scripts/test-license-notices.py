import pathlib
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[1]


class NoticeChecks(unittest.TestCase):
    def test_changed_attribution_is_rejected(self):
        with tempfile.TemporaryDirectory(prefix="snapkeymap-notices-") as directory:
            root = pathlib.Path(directory)
            files = [
                "app/build.gradle.kts",
                "app/src/main/java/com/heykay/snapkeymap/ui/LicensesScreen.kt",
                "scripts/check-license-notices.py",
            ]
            for name in files:
                target = root / name
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(ROOT / name, target)
            assets = pathlib.Path("app/src/main/assets/licenses")
            shutil.copytree(ROOT / assets, root / assets)
            command = [sys.executable, str(root / "scripts/check-license-notices.py")]
            original = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(original.returncode, 0, original.stderr)

            attribution = root / assets / "attributions.txt"
            attribution.write_bytes(attribution.read_bytes() + b"\nAltered attribution.\n")
            changed = subprocess.run(command, capture_output=True, text=True)
            self.assertNotEqual(changed.returncode, 0)
            self.assertIn("Attribution checksum mismatch", changed.stderr)


if __name__ == "__main__":
    unittest.main()
