# Home status

The status card at the top of Home tells the user whether the Snap Key is being detected. It shows `Ready`, `Turned off`, `Paused` or `Needs attention` with a one-line reason, offers a fix or a reconnect, and holds the master switch that turns SnapKey Map off and hands the key back to Oppo.

## Sub-features

- `status-ready`: `Ready`, with `Press the Snap Key to try it.` or `Last: <gesture> · just now` / `· Ns ago`.
- `status-attention`: `Needs attention`, with `Accessibility is off. Turn it on to run your actions.`, `Log access is missing. Grant it again to detect the key.`, or `Teach the Snap Key again so it can be detected.`, plus a `Fix` button.
- `status-paused`: `Paused` with a `Connect` button. The text depends on log access, for example `Tap "Allow" on the pop-up.`
- `status-off`: the master switch off shows `Turned off` / `The Snap Key does what Oppo set it to.`
- `status-details`: `Details` shows `Listening since <time> · <source>` (`Precise input via Shizuku`, `Logs via Shizuku` or `System logs`) and `Last key press <time>` or `No key press yet`.
- `status-shizuku-hint`: `Use Shizuku`, when Shizuku runs but hasn't granted SnapKey Map.

## How to get to it (user POV)

- Open the app. Home is the first screen once setup is done.
- `Fix` opens accessibility settings, the log-access screen, or the teach wizard, depending on the warning.

## Driving it with sk

Preconditions:

- Baseline from the README.
- Write down the switch state from the card title before you touch it.

- **Read the status.** Run `sk launch`, then `sk ui status`. The first two labels are the card title and body. Compare them with `sk doctor`: `accessibility service: off` must show `Needs attention` / `Accessibility is off…`.
- **Details.** Run `sk tap Details --expect "Hide details"`, then `sk ui details`. The line names the log source and the last key press.
- **Master switch off and on.** The switch has no label. In `sk ui`, it's the element without a label marked `clickable` to the right of the card title (on 2026-10-28 it was at `[948,532][1130,700]`, with the accessibility warning showing). Tap the centre of its bounds with `/mnt/c/platform-tools/adb.exe shell input tap <x> <y>`. Then run `sk wait "Turned off"`, tap it again, and wait for the original title. Save `sk ui` after each tap.
- **Fix.** Only with the user's agreement: `sk tap Fix` leaves the app (accessibility settings or another screen). Check where it went with `sk ui --all`, then `sk back`.
- **Live detection.** Ask the user for one Snap Key press, then run `sk ui pressed`. A working setup shows `Last: press · just now`, and the Details line updates `Last key press`.

## Gotchas

- `sk ui`, `tap` and `wait` run uiautomator, which suspends other accessibility services for a moment. The card can briefly read `Accessibility is off` even when the service is on. Trust `sk doctor`.
- The card updates every second, so the "Ns ago" text changes between dumps. Match it with `--contains "Last: press"`.
- The user leaves the app off between tests. `Turned off` at the start isn't a bug.
- `Paused` after a reboot is expected without Shizuku: Android asks for log access only while the app is in front.
