#!/usr/bin/env python3
import contextlib
import importlib.machinery
import importlib.util
import io
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET


loader = importlib.machinery.SourceFileLoader("sk", str(Path(__file__).with_name("sk")))
spec = importlib.util.spec_from_loader(loader.name, loader)
sk = importlib.util.module_from_spec(spec)
loader.exec_module(sk)


def node(label="Target", package=sk.PACKAGE, bounds="[10,20][110,120]", **attrs):
    return ET.Element("node", {"text": label, "package": package, "bounds": bounds,
                               "enabled": "true", **attrs})


def hierarchy(*children):
    root = ET.Element("hierarchy")
    root.extend(children)
    return ET.tostring(root, encoding="unicode")


class HelperTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="sk-test-")
        self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name)
        current = self.folder / ".current"
        current.write_text(str(self.folder))
        self.xml = hierarchy(node())
        self.responses = {"wm size": "Physical size: 1000x2000"}
        self.inputs = []
        self.allowed_inputs = []
        self.started = False
        self.allow_start = False
        self.devices = False
        for name, value in (("CURRENT", current), ("sh", self.shell), ("adb", self.adb)):
            self.enterContext(patch.object(sk, name, value))
        self.enterContext(patch.object(sk.time, "sleep"))
        self.enterContext(patch.object(sk.subprocess, "run", side_effect=AssertionError("real process forbidden")))
        self.enterContext(patch.object(sk.subprocess, "Popen", side_effect=AssertionError("real process forbidden")))
        self.enterContext(patch.dict(os.environ, {}, clear=True))

    def adb(self, *args, **kwargs):
        if args == ("exec-out", "sh -c 'uiautomator dump /sdcard/sk-ui.xml >/dev/null 2>&1; "
                    "cat /sdcard/sk-ui.xml; rm -f /sdcard/sk-ui.xml'"):
            return '<?xml version="1.0"?>' + self.xml
        if args == ("devices", "-l") and self.devices:
            return "List of devices attached\nfake device product:stub\n"
        self.fail(f"unexpected adb call: {args}")

    def shell(self, command, check=False):
        if command.startswith("input "):
            self.inputs.append(command)
            self.assertIn(command, self.allowed_inputs)
            self.assertTrue(check)
            return ""
        if command == f"am start -W -n {sk.ACTIVITY}" and self.allow_start:
            self.started = True
            return "Status: ok"
        if command in self.responses:
            return self.responses[command]
        self.fail(f"unexpected shell call: {command}")

    def run_cli(self, *args, rejected=False):
        out, err = io.StringIO(), io.StringIO()
        with patch.object(sk.sys, "argv", ["sk", *args]), contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            try:
                sk.main()
            except SystemExit as exc:
                if rejected and not exc.code:
                    self.fail("command should have been rejected")
                if not rejected and exc.code:
                    raise
                if isinstance(exc.code, str):
                    print(exc.code, file=err)
            else:
                if rejected:
                    self.fail("command should have been rejected")
        return out.getvalue(), err.getvalue()

    def test_default_ui_privacy_and_valid_artifacts(self):
        foreign = node("PRIVATE_TEXT", "private.pkg", secret="PRIVATE_ATTRIBUTE")
        foreign.text, foreign.tail = "PRIVATE_BODY", "PRIVATE_TAIL"
        app = node("App")
        app.append(foreign)
        nested_app = node("Nested app")
        foreign.append(nested_app)
        outer = node("PRIVATE_PARENT", "private.pkg")
        outer.append(app)
        cases = ((hierarchy(node("App")), ["App"]),
                 (hierarchy(node("PRIVATE_ONLY", "private.pkg")), []),
                 (hierarchy(node("App"), node("PRIVATE_SIBLING", "private.pkg")), ["App"]),
                 (hierarchy(outer), ["App", "Nested app"]))
        for xml, labels in cases:
            with self.subTest(labels=labels, xml=xml):
                self.xml = xml
                out, err = self.run_cli("ui")
                artifact = Path(out.split("saved ", 1)[1].strip())
                self.assertTrue(artifact.is_file())
                saved = artifact.read_text()
                root = ET.fromstring(saved)
                self.assertEqual(root.tag, "hierarchy")
                self.assertEqual([n.get("text") for n in root.iter("node")], labels)
                self.assertNotIn("PRIVATE", out + err + saved + artifact.with_suffix(".txt").read_text())
                self.assertTrue(all(n.get("package") == sk.PACKAGE for n in root.iter("node")))

    def test_ui_all_explicitly_retains_foreign_hierarchy(self):
        self.xml = hierarchy(node("PRIVATE", "private.pkg"))
        out, _ = self.run_cli("ui", "--all")
        artifact = Path(out.split("saved ", 1)[1].strip())
        self.assertIn("PRIVATE", out)
        self.assertEqual(ET.tostring(ET.fromstring(artifact.read_text())),
                         ET.tostring(ET.fromstring(self.xml)))

    def test_launch_rejects_foreign_and_unknown_without_start(self):
        for focus in ("mCurrentFocus=null", "mCurrentFocus=Window{abc u0 private.pkg/.Main}"):
            with self.subTest(focus=focus):
                self.responses["dumpsys window | grep -m1 mCurrentFocus"] = focus
                self.run_cli("launch", rejected=True)
                self.assertFalse(self.started)

    def test_launch_take_over_and_allowlist(self):
        self.xml = hierarchy(node("SnapKey Map"))
        self.allow_start = True
        cases = [("mCurrentFocus=null", ["--take-over"]),
                 ("mCurrentFocus=Window{abc u0 private.pkg/.Main}", ["--take-over"])]
        cases += [(f"mCurrentFocus=Window{{abc u0 {package}/.Main}}", [])
                  for package in sk.IDLE_FOREGROUND]
        for focus, flags in cases:
            with self.subTest(focus=focus):
                self.started = False
                self.responses["dumpsys window | grep -m1 mCurrentFocus"] = focus
                self.assertIn("ready: SnapKey Map", self.run_cli("launch", *flags)[0])
                self.assertTrue(self.started)

    def test_doctor_warns_on_unknown_focus(self):
        self.devices = True
        self.responses.update({
            "getprop ro.product.model; getprop ro.build.version.release": "CPH2797\n17",
            f"dumpsys package {sk.PACKAGE}": "versionName=1.2.0 versionCode=10200",
            "settings get secure enabled_accessibility_services": sk.SERVICE,
            f"pidof {sk.PACKAGE}": "123",
            "dumpsys power | grep -m1 mWakefulness=": "mWakefulness=Awake",
            "dumpsys window | grep -m1 isKeyguardShowing": "isKeyguardShowing=false",
            "dumpsys window | grep -m1 mCurrentFocus": "mCurrentFocus=null",
            "settings get global zen_mode": "0", "settings get global mode_ringer": "2",
        })
        out, _ = self.run_cli("doctor")
        self.assertIn("WARNING: foreground is unknown", out)

    def test_tap_rejects_disabled_and_unsafe_bounds(self):
        cases = [("[10,20][110,120]", {"enabled": "false"})]
        cases += [(bounds, {}) for bounds in ("[10,20][10,120]", "[10,20][110,20]",
                  "[-10,20][110,120]", "[10,-20][110,120]", "[110,20][10,120]",
                  "[10,120][110,20]", "[10,20][1001,120]", "[10,20][110,2001]", "bad")]
        for bounds, attrs in cases:
            with self.subTest(bounds=bounds, attrs=attrs):
                self.xml = hierarchy(node(bounds=bounds, **attrs))
                self.run_cli("tap", "Target", rejected=True)
                self.assertEqual(self.inputs, [])

    def test_tap_rejects_invalid_index_and_requires_disambiguation(self):
        self.xml = hierarchy(node(), node(bounds="[200,20][300,120]"))
        for flags in ([], ["--index", "-1"], ["--index", "2"], ["--index", "999999"]):
            with self.subTest(flags=flags):
                self.run_cli("tap", "Target", *flags, rejected=True)
                self.assertEqual(self.inputs, [])
        self.allowed_inputs = ["input tap 250 70"]
        self.run_cli("tap", "Target", "--index", "1")
        self.assertEqual(self.inputs, self.allowed_inputs)

    def test_tap_default_lookup_and_missing_diagnostics_are_app_only(self):
        self.xml = hierarchy(node(), node("PRIVATE", "private.pkg"), node("Target", "private.pkg"))
        self.allowed_inputs = ["input tap 60 70"]
        self.run_cli("tap", "Target")
        self.assertEqual(self.inputs, self.allowed_inputs)
        out, err = self.run_cli("tap", "Missing", rejected=True)
        self.assertNotIn("PRIVATE", out + err)

    def test_tap_foreign_requires_all(self):
        self.xml = hierarchy(node("Allow", "com.android.systemui"))
        self.run_cli("tap", "Allow", rejected=True)
        self.assertEqual(self.inputs, [])
        self.allowed_inputs = ["input tap 60 70"]
        self.run_cli("tap", "Allow", "--all")
        self.assertEqual(self.inputs, self.allowed_inputs)

    def test_status_card_guard_and_scrolled_safe_tap(self):
        self.responses["settings get secure enabled_accessibility_services"] = sk.SERVICE
        self.xml = hierarchy(node(sk.DUMP_ONLY_WARNING), node("Press", bounds="[0,200][500,300]"))
        _, err = self.run_cli("tap", "Press", rejected=True)
        self.assertIn("scroll down --px", err)
        self.assertEqual(self.inputs, [])
        self.allowed_inputs = ["input swipe 500 1700 500 1000 1500", "input tap 250 250"]
        self.run_cli("scroll", "down", "--px", "700")
        self.xml = hierarchy(node("Press", bounds="[0,200][500,300]"))
        self.run_cli("tap", "Press")
        self.assertEqual(self.inputs, self.allowed_inputs)

    def test_scroll_rejects_invalid_distances(self):
        for px in ("0", "-1", "1701", "999999999"):
            with self.subTest(px=px):
                self.run_cli("scroll", "down", "--px", px, rejected=True)
                self.assertEqual(self.inputs, [])

    def test_learn_warning_blocks_start_but_toolbar_back_is_safe(self):
        self.responses["wm size"] = "Physical size: 1264x2780"
        self.responses["settings get secure enabled_accessibility_services"] = sk.SERVICE
        self.xml = hierarchy(
            node("Back", bounds="[0,50][100,150]"),
            node("Turn on the SnapKey Map service first", bounds="[0,1372][1000,1472]"),
            node("Start", bounds="[0,2037][500,2137]"),
        )
        _, err = self.run_cli("tap", "Start", rejected=True)
        self.assertIn("transient accessibility warning", err)
        self.assertIn("explicitly approved", err)
        self.assertIn("tap Start manually", err)
        self.assertEqual(self.inputs, [])
        self.allowed_inputs = ["input tap 50 100"]
        self.run_cli("tap", "Back")
        self.assertEqual(self.inputs, self.allowed_inputs)

    def test_scroll_default_and_explicit_distance_boundaries(self):
        for direction, flags, command in (
                ("down", [], "input swipe 500 1500 500 600 300"),
                ("up", [], "input swipe 500 600 500 1500 300"),
                ("down", ["--px", "1"], "input swipe 500 1700 500 1699 1500"),
                ("up", ["--px", "1700"], "input swipe 500 0 500 1700 1500")):
            with self.subTest(direction=direction, flags=flags):
                self.allowed_inputs = [command]
                self.inputs = []
                self.run_cli("scroll", direction, *flags)
                self.assertEqual(self.inputs, [command])


if __name__ == "__main__":
    unittest.main()
