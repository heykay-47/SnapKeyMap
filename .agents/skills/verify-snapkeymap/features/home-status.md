# Home status

The status card at the top of Home tells the user whether the Snap Key is being detected. It shows `Ready`, `Turned off`, `Paused` or `Needs attention` with a one-line reason, offers a fix or a reconnect, and holds the master switch that turns SnapKey Map off and hands the key back to Oppo.

## Sub-features

- `status-ready`: `Ready`, with `Press the Snap Key to try it.` or `Last: <gesture> · just now` / `· Ns ago`.
- `status-attention`: `Needs attention`, with `Accessibility is off. Turn it on to run your actions.`, `Log access is missing. Grant it again to detect the key.`, or `Teach the Snap Key again so it can be detected.`, plus a `Fix` button.
- `status-paused`: `Paused`. WAITING says `Tap "Allow" on the pop-up.`; declined access says `Log access was declined. Wait a minute, then tap Connect and choose Allow.` Otherwise the card asks the user to tap Connect. Connect appears only when not waiting for consent.
- `status-off`: without a warning, the master switch off shows `Turned off` / `The Snap Key does what Oppo set it to.` Warnings take priority over this title.
- `status-details`: `Details` shows `Listening since <time> · <source>` (`Precise input via Shizuku`, `Logs via Shizuku` or `System logs`) and `Last key press <time>` or `No key press yet`.
- `status-report`: Details also holds `Copy report` and `Report a bug`; follow [Bug report](./bug-report.md) before using them.
- `status-shizuku-hint`: `Use Shizuku`, when Shizuku runs but hasn't granted SnapKey Map.

## How to get to it (user POV)

- Open the app. Home is the first screen once setup is done.
- `Fix` opens accessibility settings, the log-access screen, or the teach wizard, depending on the warning.

## Driving it with sk

Preconditions:

- Baseline from the README.
- Record the visible switch state from a screenshot before you touch it. A warning title does not tell you whether the switch is on.

- **Read the status.** Run `sk doctor`, `sk launch` and `sk shot status`. Read the real title and body from the screenshot. A dump can show a false accessibility warning even when doctor reports the service on.
- **Details.** If `sk tap Details` is blocked by the transient warning, ask the user to expand it. Then `sk ui details` names the log source and the last key press. Collapse it after the check.
- **Master switch.** The switch has no label. Ask the user to flip it twice, or use the [Quick Settings tile](./quick-settings-tile.md). Capture the actual card with `sk shot` after each flip. With System logs, consent may need renewal; do not finish until the starting switch and listening state are restored.
- **Fix.** Only with the user's agreement: `sk tap Fix` leaves the app (accessibility settings or another screen). Check where it went with `sk ui --all`, then `sk back`.
- **Live detection.** Ask the user for one Snap Key press, then run `sk shot pressed`. A working setup shows `Last: press` and a recent age. Check the assigned action's side effect with `sk state`, then undo that effect.

## Gotchas

- `sk ui`, `tap` and `wait` run uiautomator, which suspends other accessibility services for a moment. The card can briefly read `Accessibility is off` even when the service is on. Trust `sk doctor`.
- The card updates every second, so the "Ns ago" text changes between dumps. Match it with `--contains "Last: press"`.
- The user leaves the app off between tests. `Turned off` at the start isn't a bug.
- `Paused` after a reboot is expected without Shizuku: Android asks for log access only while the app is in front.
