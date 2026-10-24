import hashlib
import json
import pathlib
import re
import sys


ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app/src/main/assets/licenses"
notices = json.loads((ASSETS / "notices.json").read_text())
licenses = {item["name"]: item for item in notices["licenses"]}


def license_for(group):
    matches = [
        dependency["license"]
        for dependency in notices["dependencies"]
        for pattern in dependency["groups"]
        if group == pattern or pattern.endswith(".*") and group.startswith(pattern[:-1])
    ]
    assert len(matches) == 1, f"Missing or ambiguous licence mapping for {group}: {matches}"
    assert matches[0] in licenses, f"Missing licence text for {group}"
    return matches[0]


build = (ROOT / "app/build.gradle.kts").read_text()
coordinates = re.findall(r'\bimplementation\((?:platform\()?"([^"\n]+)"', build)
for coordinate in coordinates:
    group, *artifact_version = coordinate.split(":")
    source_path = group.replace(".", "/") + "/" + "/".join(artifact_version)
    license_for(group)
    assert any(
        source_path in source
        for dependency in notices["dependencies"]
        for source in dependency["sources"]
    ) or group.startswith("androidx."), f"Missing pinned source for {coordinate}"

assert license_for("dev.rikka.shizuku") == "MIT (Shizuku)"
for group in ["androidx.compose.ui", "org.lsposed.hiddenapibypass", "org.jetbrains.kotlin",
              "org.jetbrains.kotlinx", "org.jetbrains", "org.jspecify", "com.google.guava"]:
    assert license_for(group) == "Apache-2.0", group

for license in licenses.values():
    data = (ASSETS / license["file"]).read_bytes()
    assert hashlib.sha256(data).hexdigest() == license["sha256"], license["file"]

mit = (ASSETS / licenses["MIT (Shizuku)"]["file"]).read_text()
for phrase in ["Copyright (c) 2021 RikkaW", "Permission is hereby granted, free of charge",
               "The above copyright notice and this permission notice shall be included",
               'THE SOFTWARE IS PROVIDED "AS IS"', "OTHER DEALINGS IN THE\nSOFTWARE."]:
    assert phrase in mit, f"Incomplete MIT notice: {phrase}"

apache = (ASSETS / licenses["Apache-2.0"]["file"]).read_text()
for section in range(1, 10):
    assert re.search(rf"^   {section}\. ", apache, re.MULTILINE), f"Missing Apache section {section}"
assert "END OF TERMS AND CONDITIONS" in apache
assert "APPENDIX: How to apply the Apache License to your work." in apache
assert apache.rstrip().endswith("limitations under the License.")
assert len(apache) > 11000, "Truncated Apache licence"

attributions = (ASSETS / notices["attributions"]).read_text()
for author in ["2021-2025 LSPosed", "The Android Open Source Project", "JetBrains s.r.o.",
               "The JSpecify Authors", "The Guava Authors"]:
    assert author in attributions, f"Missing attribution for {author}"

screen = (ROOT / "app/src/main/java/com/heykay/snapkeymap/ui/LicensesScreen.kt").read_text()
assert 'assets.open("licenses/notices.json")' in screen
assert 'getString("attributions")' in screen
assert 'getJSONArray("licenses")' in screen

if len(sys.argv) == 2:
    inventory = json.loads(pathlib.Path(sys.argv[1]).read_text())
    for artifact in inventory["artifacts"]:
        license_for(artifact["coordinate"].split(":")[0])
    print(f"Mapped all {len(inventory['artifacts'])} inspected POM entries, including superseded requests.")

print("Production notice mappings, attribution notices, complete MIT and Apache-2.0 texts passed.")
