# Quick Settings tile

The Snap Key tile flips the same master switch as Home. It displays On, Off or Set up in app. Long-press opens SnapKey Map.

## Sub-features

- `tile-toggle`: On is active and Off is inactive. Home's master switch follows each change.
- `tile-setup`: Set up in app means the key is not taught or required log permission is missing. Tapping opens the app.
- `tile-open`: long-press opens the app. A locked-phone tap requires the user to unlock first.

## How to get to it (user POV)

- Add Snap Key to Quick Settings through the panel's edit screen, then tap it in the panel.

## Driving it with sk

Preconditions:

- Baseline from the README. Record the master switch, real card state and tile layout before changing anything.
- The tile must be present. Ask before adding it, and agree whether it stays after the test.
- On this phone, approved temporary addition works with `adb shell cmd statusbar add-tile com.heykay.snapkeymap/.SnapTileService`. Removal uses `remove-tile` with the same component. Record `settings get secure sysui_qs_tiles` first; compare the list after removal. Never rewrite the whole list.

1. Save `sk shot home-before`. Open the panel with `adb shell cmd statusbar expand-settings`.
2. Inspect only Snap Key nodes from the hierarchy where possible. A full `sk ui --all` or screenshot can include private notification content.
3. On the checked phone, the clickable tile node has description `Snap Key`, text On or Off, and a checked flag. Use fresh bounds and tap it once with `sk tap --all "Snap Key" --index <clickable-node-index>`.
4. Collapse the panel with `adb shell cmd statusbar collapse`. Return to the app and capture `sk shot home-after` to prove the switch changed.
5. Repeat to restore the original switch. With System logs, check the listening state too. If Paused asks for consent, let the user reconnect from the foreground; the switch alone is not proof of Ready.
6. Remove a tile added only for this test. Compare the stored tile list with the original and leave Home in its starting state.

## Gotchas

- Multiple nodes can say Snap Key; choose the clickable tile, not its caption. Read the current node list rather than assuming an index.
- Do not manufacture Set up in app by clearing the taught key or revoking access.
- A Paused card was observed after toggling from the panel on 1.2.0. Ready was present again on resume. The cause was not established; do not call it a confirmed tile defect.
- Long-press and locked-phone behavior need separate live checks. A tap test does not prove them.
