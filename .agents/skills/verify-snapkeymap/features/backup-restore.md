# Back up and restore settings

The Backup section saves the current configuration to a JSON file or restores a chosen backup. A valid restore replaces mappings, timing, theme and the learned key after confirmation.

## Sub-features

- `backup-save`: Back up settings warns that the file can contain private data. Continue opens the system save picker, suggesting `snapkeymap-backup-<date>.json`. Success shows Settings backed up.
- `backup-restore`: Restore settings opens the file picker. A valid file shows Restore settings? with Restore and Cancel. Success shows Settings restored.
- `backup-validation`: invalid, oversized or newer-format backups show an error without applying settings.

## How to get to it (user POV)

- Scroll Home below Pause in apps to Back up settings and Restore settings.

## Driving it with sk

Preconditions:

- Baseline from the README. The user approves the file-picker and same-file restore check.
- Restore only the current backup created by this run. Keep its contents out of chat, logs, Git and screenshots.
- Choose two unique test filenames. Leave existing backups alone.

1. Record gesture rows, timing, confirmations, pause summary and theme with `sk ui` and `sk shot`.
2. Tap Back up settings, inspect the privacy warning, then tap Continue. Use `sk ui --all` in the system picker and `sk tap --all` for its controls. The checked ColorOS picker opens Download and uses uppercase `SAVE`.
3. Replace the suggested filename with a unique `snapkeymap-verify-<run>-before.json`. Save it. Verify that exact file exists and is nonempty under `/sdcard/Download` without printing its contents.
4. Tap Restore settings. In the picker, tap Search, type the unique basename, and select the exact file. System controls require `sk tap --all`.
5. Capture Restore settings? and its replacement warning. Tap Restore once. Capture the result and recheck the visible settings.
6. Save a second current backup as `snapkeymap-verify-<run>-after.json`. Read both through `adb exec-out cat` into a local Python process and compare the parsed JSON objects. Print only equality results. Require full equality, including `actions` and `key`, before deleting the baseline file.
7. Delete only the two exact test paths. Confirm both are gone and older backups remain. Keep a non-sensitive equality receipt with the evidence.

## Gotchas

- Restoring replaces the learned key. Do not test with a hand-edited or older file on the daily phone.
- The backup excludes the master switch, setup state and the one-time unlocked-only hint. Check those separately.
- A toast may disappear before a dump finishes. File existence and the round-trip equality prove more than a toast alone.
- A same-day backup may already exist. Use a unique name rather than accepting an overwrite dialog.
