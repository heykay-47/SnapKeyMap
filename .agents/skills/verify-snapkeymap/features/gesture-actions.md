# Gesture actions

For each gesture (`Press`, `Double press`, `Press & hold`, `Tap, then hold`) the user picks an action from a searchable list. When the Snap Key is pressed that way, the action runs.

## Sub-features

- `pick-device`: built-in actions grouped under `Phone`, `Screen & navigation`, `Music & video` and `Open`. Examples: `Flashlight on / off`, `Do Not Disturb on / off`, `Take a screenshot`, `Circle to Search`, `Play / pause`, `Camera`.
- `pick-ringer`: `Ringer mode` opens a preset sheet, with `New preset` and `Edit <modes>`. The editor offers Preview, Save, Cancel and Delete. Selecting a preset assigns its cycle, such as `Ring → Vibrate → Silent`.
- `pick-app`: launcher apps, found with `Search actions and apps`.
- `pick-more`: `Call a contact…`, `Open a link…` (a dialog with a `Link` field and `Save`), `App shortcut…`.
- `pick-nothing`: `Do nothing`, at the top outside the sections.
- `run-action`: a real key press runs the chosen action.
- `try-action`: [Try a gesture](./try-gesture.md) runs an assigned action without proving key detection.

## How to get to it (user POV)

- Home → a gesture row (`Press`, `Double press`, `Press & hold`, `Tap, then hold`). The picker's title is the gesture's name.
- During setup, the last page offers the same picker.

## Driving it with sk

Preconditions:

- Baseline from the README.
- Write down the current action under each gesture row from `sk ui home`. Scroll the status card offscreen before tapping a gesture row.
- For `run-action`: the status card reads `Ready`, and the user is there to press the key.

- **Open the picker.** Run `sk tap Press --expect "Search actions and apps"`, then `sk ui picker`. `Current` shows the existing action, and the selected row has the `Selected` check.
- **Choose an action.** Run `sk tap "Flashlight on / off" --expect "What the Snap Key does"`. Choosing an action returns to Home, and the `Press` row now reads `Flashlight on / off`. Save `sk ui after-pick`.
- **Confirm it persists.** Run `sk tap About --expect Back`, then `sk back`, then `sk ui persisted`. The row still reads `Flashlight on / off`.
- **Run it.** Run `sk state before-press`, then ask the user for one single press. Run `sk state after-press` and `sk shot after-press`. Proof requires torch lines with the app's PID and a recent `Last: press` on the card. Ask for a second press and verify the torch returns to its starting state.
- **Restore.** Open `Press` again and choose the action you recorded (search it with `/mnt/c/platform-tools/adb.exe shell input text`, or scroll to it). Show the row with its original value in `sk ui restored`.

## Gotchas

- Actions marked as needing the service (`Take a screenshot`, `Lock the screen`, `Go back`, …) do nothing while `accessibility service: off`.
- With `Double press` or `Tap, then hold` set, a single press waits for the double-press window before it runs. Wait about 1 s before `sk state after-press`.
- `Press & hold` and `Tap, then hold` show `Your phone doesn't report holding the key…` and can't be tapped when the taught key can't detect holds.
- Choosing `Do Not Disturb on / off`, or `Ringer mode` with Silent and no Shizuku, opens a DND-access dialog instead of returning Home.
- Injected key events (`input keyevent`) skip precise `getevent` detection and arrive about 2 ms apart, inside the 3 ms dedupe window. They are not proof of a real press. Use the real key.
- Oppo's own Snap Key action must be `None` (Settings › Snap Key), or Oppo's action runs as well and muddies the side-effect proof.
