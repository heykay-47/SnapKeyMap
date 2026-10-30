---
name: verify-snapkeymap
description: Drive the real SnapKey Map Android app on the user's Oppo Find X9 over adb (screens, taps, screenshots, ringer/torch/vibration side effects) and keep the evidence. Use to prove a SnapKey Map change works on the device, to reproduce a bug report on the phone, or to check what the installed build actually does.
---

# Verify SnapKey Map

SnapKey Map is an Android app. The only surface a user touches is its UI on the phone, plus the actions a Snap Key gesture triggers (flashlight, ringer, apps, Circle to Search, and so on). The project also has a website in `docs/` (GitHub Pages). This skill does not cover it.

There is **one** instance: the app on the user's own Find X9 (CPH2797, ColorOS 17, Android 17), with their real configuration. No emulator can produce the Snap Key, and no second copy can be installed next to it. Treat every run as driving someone's daily phone.

All commands run from the repo root. The helper is `.claude/skills/verify-snapkeymap/scripts/sk`; set `SK_ADB` to override its default adb path. Below it is written as `sk`; set `alias sk=.claude/skills/verify-snapkeymap/scripts/sk` or use the full path.

## Rules for this phone

- **No local builds.** The user builds only on GitHub Actions. Never run `./gradlew` locally.
- **Check before installing.** The user installs and tests builds by hand. Run `sk doctor` first: the build you need may already be installed.
- **Never `pm clear`, uninstall, or force-stop the app.** That would wipe the user's taught key, actions and setup. Re-granting READ_LOGS needs Shizuku or a reboot dance under ColorOS permission monitoring, and the shell cannot revoke it. A journey that needs a fresh install (the intro screen, first-run setup with no data) is reported as needing the user's consent, not run.
- **Restore what you change.** Before changing a setting (an action, timing, a toggle, the theme), record its value from `sk ui`, and set it back before `sk finish`. Save the `sk ui` output that shows the setting back at its old value.
- **The user switches the app off between tests.** Read the switch on the status card ("Turned off" or "Ready") before you conclude anything about detection.
- **A person has to press the Snap Key.** `sendevent` is denied, and injected key events skip precise (`getevent`) detection. When a journey needs real presses, ask the user to press, and say exactly which gesture and how many times.
- **Public replies need approval.** Never post device results to an issue or PR without showing the user the exact wording first.

## Launch

The app is already installed. Launch means: make sure the right build is on the phone, then bring it to the front.

1. `sk doctor` (add `--expect-version 1.1.0` to require a version). It exits non-zero and prints `BLOCKED:` when the phone shouldn't be driven.
2. If a different build is needed, get a **release-signed** APK from CI. PR builds use a throwaway debug key and will not install over the user's release build.
   ```bash
   gh workflow run build.yml --ref <branch>
   gh run list --workflow build.yml --branch <branch> --limit 1      # note the run id
   gh run watch <run-id> --exit-status
   gh run download <run-id> -n SnapKeyMap -D /mnt/c/Users/heykay47/Downloads/snapkeymap-<branch>
   /mnt/c/platform-tools/adb.exe install -r 'C:\Users\heykay47\Downloads\snapkeymap-<branch>\SnapKeyMap-<version>.apk'
   ```
   `adb.exe` is a Windows binary, so give it the Windows path. Over USB, ColorOS holds the install on a "Continue installation" prompt. Run `sk ui --all` to find the button, then `sk tap "<label>"` to accept it. Ask the user before you install over their build.
3. `sk run <label>` creates the evidence folder and records a doctor snapshot. Every later `sk ui`, `sk shot` and `sk state` saves into it.
4. `sk launch` runs `am start` and waits until a known app screen title shows. It prints `ready: <title>`. "Ready" means the UI can be driven. It does not mean detection is live: read the status card for that.

The app opens on Home (`SnapKey Map` title) once setup is done, on the setup flow if it isn't, and on the intro only after a data wipe.

## Doctor

`sk doctor` is read-only. Run it first, and again whenever something looks off. It reports:

- the device model and Android version. It blocks unless exactly one device is attached (or `SK_SERIAL` is set) and it is the CPH2797.
- the installed `versionName (versionCode)`. It blocks if the app isn't installed or `--expect-version` doesn't match.
- whether READ_LOGS is granted for the main user, and whether the SnapKey Map accessibility service is enabled.
- whether the app process is running.
- whether the screen is awake and unlocked. It blocks when the phone is asleep or locked; ask the user to unlock it.
- the foreground window. If it's an app other than the launcher or SnapKey Map, the user may be using the phone. Ask before taking over the screen.
- `zen_mode` and `mode_ringer`.

## Drive

The UI is Jetpack Compose with no test tags, so the handles are visible text and content descriptions. `sk ui` lists them with their bounds, and `sk tap` finds an element by its exact label.

| Command | What it does |
|---|---|
| `sk ui [name]` | Lists the labelled elements of the app on screen. Saves the XML and a text copy. `--all` includes system dialogs and other apps. |
| `sk tap "<label>"` | Taps the centre of the element whose text or content description is exactly `<label>`. Use `--contains` for a substring and `--index N` when several match (it lists them). Use `--expect "<label>"` to confirm that the tap landed: it retries once if that label doesn't appear. |
| `sk wait "<label>" [--timeout S]` | Waits until the label is on screen. |
| `sk scroll down` / `sk scroll up` | Swipes the list. Compose lists only show what's on screen, so scroll before you look for lower items. |
| `sk back` | Presses the system Back key. |
| `sk shot <name>` | Saves a PNG screenshot. |
| `sk state <name>` | Saves the side effects outside the app: ringer and DND, camera-service events (each torch on/off with its PID), recent vibrations, accessibility and permission state. |
| `sk logcat start` / `sk logcat stop` | Captures `logcat -v threadtime,uid` into the run folder. Only the process this run started gets stopped. |

Stable handles in the app (see `features/` for each journey):

- Home toolbar: `Theme`, `Help` and `About` (content descriptions).
- Status card: title `Ready`, `Turned off`, `Paused` or `Needs attention`, followed by the body text. The switch next to the title is the master toggle. It has no label, so it's the clickable element without a label at the right of the title row. Also `Details`/`Hide details`, `Fix` and `Connect`.
- Gesture rows: `Press`, `Double press`, `Press & hold`, `Tap, then hold`, each followed by its current action's label. Each row has a lock toggle labelled `Only when unlocked, off` or `Only when unlocked, on`.
- Settings: `Timing`, `Show confirmations`, `Pause in apps`.
- Back arrow on every inner screen: `Back`.

Raw adb, for anything the helper doesn't cover: `/mnt/c/platform-tools/adb.exe shell ...`.

## Evidence

Each run writes to `~/snapkeymap-verify/<UTC timestamp>-<label>/`, which is outside the repo and survives cleanup and worktree removal. It holds `00-doctor.txt`, numbered `NN-<name>.xml`/`.txt`/`.png`/state files in the order they were taken, `actions.log` (every launch, tap, back and wait, with times), `logcat.txt` if captured, and `99-doctor-after.txt`.

Proof standards:

- Drive the real user path: taps on the screens a user sees. Never edit preferences or call internals. The release build isn't debuggable, so `run-as` can't read preferences anyway.
- Capture the action and the result: an `sk ui` or `sk shot` before the tap, and another after it.
- Check side effects, not just the screen. Flashlight: `sk state` before and after, then compare the camera events for new torch lines with the app's PID (`pidof com.heykay.snapkeymap`). Neither `sk state` run on 2026-10-28 toggled the torch, so the exact format of that line is unconfirmed. Diff the two files instead of searching for a fixed string, and record the real format here the first time you see it. Ringer: `mode_ringer` (0 silent, 1 vibrate, 2 normal) and `zen_mode`. Vibration: `dumpsys vibrator_manager` shows what actually played. ColorOS replaces short steps with light prebaked clicks.
- A setting change is proven by leaving the screen and coming back (or relaunching the app), then showing the value again with `sk ui`.
- Gesture detection is proven by the status card (`Last: double press · just now`) together with the action's side effect, after the user presses the key.

## Cleanup

`sk finish` stops the logcat capture this run started (by its PID, never by name), removes the helper's temporary file on the phone (`/sdcard/sk-ui.xml`), writes `99-doctor-after.txt`, and lists the evidence it kept. It doesn't close the app or change any setting. Restore settings yourself before running it, and compare `99-doctor-after.txt` with `00-doctor.txt`. Run `sk finish` after a failed run too.

## Gotchas

- `uiautomator` (behind `sk ui`, `tap` and `wait`) briefly suspends other accessibility services, so the status card can flash "Accessibility is off" while you're driving. Confirm with `sk doctor`, which reads the setting.
- Taps right after a screen change can be dropped. Pass `--expect` to `sk tap`, or follow each tap with `sk wait`.
- A slow phone can make `sk launch` time out before the first frame. Run it again rather than force-stopping the app.
- Android shows the log-access consent dialog only while the app is in the foreground, and remembers approval for just 60 s. A detection journey that relies on logs (not Shizuku) may need the user to tap Allow.
- Two machines, two paths: `adb.exe` runs on Windows. `exec-out` piping works from WSL, but `adb pull`/`install` need Windows paths.

## Feature map

`features/README.md` lists the journeys. Each one says how a user reaches the feature, how to drive it with `sk`, and what proves it works. A proof that drives only one entry point is incomplete when the map lists others.
