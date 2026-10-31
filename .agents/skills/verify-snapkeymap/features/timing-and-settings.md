# Timing and settings

Home lets the user tune how gestures are told apart, choose whether confirmation pop-ups show, pause the key inside chosen apps, limit a gesture to when the phone is unlocked, and pick a theme.

## Sub-features

- `timing`: the `Timing` row (`Double <n> ms · Hold <n> ms`) opens a dialog with two sliders, `Double-press window in milliseconds` (200–600 ms) and `Hold time in milliseconds` (300–1200 ms), plus `Reset to defaults` (350 / 650) and `Done`.
- `confirmations`: the `Show confirmations` row toggles pop-ups such as Vibrate or Do Not Disturb on. Errors always show.
- `pause-in-apps`: the `Pause in apps` row opens a sheet of launcher apps to tick, with `Clear all`.
- `unlocked-only`: each gesture row's lock, `Only when unlocked, off` / `Only when unlocked, on`. The first time it's turned on, an `Only when unlocked` dialog with `Got it` appears.
- `theme`: the `Theme` toolbar icon opens `System default`, `Light` and `Dark`.

## How to get to it (user POV)

- Home, below the gesture rows: `Timing`, `Show confirmations`, `Pause in apps`.
- Home gesture rows: the lock icon on the right.
- Home toolbar: `Theme`.

## Driving it with sk

Preconditions:

- Baseline from the README.
- Record `Double <n> ms · Hold <n> ms`, the confirmations switch and the paused-apps summary from `sk scroll down` then `sk ui settings-before`.
- Use a screenshot for the confirmations switch; its state is not exposed in this phone's XML. Scroll the status card offscreen before tapping settings or lock icons.
- Record the theme mode, not just whether the screen is dark. If the menu exposes no selection, ask the user or read only the `theme` field from an agreed current backup without printing the backup.

- **Timing.** Run `sk tap Timing --expect "Reset to defaults"`, then `sk ui timing`. The dialog shows `Double-press window: <n> ms` and `Press & hold: <n> ms`, matching the row. Move a slider with `/mnt/c/platform-tools/adb.exe shell input swipe` across the slider bounds from `sk ui`, then confirm the label changed in 25 ms steps. Run `sk tap Done`, and check that the row's summary shows the new values. Restore by dragging back to the recorded values; use `Reset to defaults` only if they were 350 / 650.
- **Show confirmations.** Run `sk tap "Show confirmations"`, then `sk shot confirmations`. The switch changes visually. Tap again and take a second screenshot to prove restoration.
- **Pause in apps.** Run `sk tap "Pause in apps" --expect "Clear all"`, then `sk ui paused`. The sheet lists launcher apps. Close it with `sk back` without ticking anything, unless the journey needs a paused app.
- **Only when unlocked.** Run `sk tap "Only when unlocked, off" --index 0`, then `sk ui lock`. If the first-use hint appears, ask before dismissing it; that hint state cannot be restored. Otherwise verify `Only when unlocked, on`, then toggle back to the recorded state.
- **Theme.** Run `sk tap Theme`, then `sk ui --all theme-menu` (the menu is a popup window). Pick a mode, take a `sk shot`, then pick the recorded mode again.

## Gotchas

- Slider positions come from the slider's bounds, which shift when the dialog scrolls. Read the bounds from a fresh `sk ui` before every swipe.
- Values outside the range are clamped (200–600 and 300–1200), so a long swipe lands on the end of the range.
- `Show confirmations` changes only pop-ups for success. Errors still show, so an error toast doesn't prove the switch is broken.
- The `Only when unlocked` dialog appears only once, ever. If it doesn't show, it was dismissed in an earlier run.
