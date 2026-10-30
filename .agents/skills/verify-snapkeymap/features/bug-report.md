# Bug report

Details on the status card contains Copy report and Report a bug. The first writes a plain-text report to the clipboard. The second opens GitHub's bug form with setup fields prefilled.

## Sub-features

- `report-copy`: the report includes app, phone and OS versions, grant and detection state, learned-key data, timing, action kinds and recent detections.
- `report-privacy`: configured contacts, URLs and app targets are reduced to action kinds. The report still contains device and setup information; inspect it before sharing.
- `report-form`: opens the repository's `issues/new?template=bug_report.yml` URL with version, device, access, teach and action fields prefilled.

## How to get to it (user POV)

- Home, Details on the status card. Both buttons appear below the detection details.

## Driving it with sk

Preconditions:

- Baseline from the README. Ask before replacing the clipboard or opening the browser.
- Stop before submitting anything to GitHub or entering credentials.

1. Expand Details. If the helper blocks the tap because the card resizes, ask the user to expand it. Record `sk ui details` and a screenshot.
2. Tap Copy report once, then immediately run `sk shot copied`. On this Android 17 phone, the system clipboard preview shows `SnapKey Map 1.2.0 (10200)` and the phone heading. No app toast is expected on Android 13 or later.
3. The preview proves a copy occurred, not the complete report's privacy. Full-content proof requires an agreed temporary paste destination and cleanup. Otherwise report that limitation.
4. Tap Report a bug only when its bounds are stable. Use `sk ui --all bugform` in the browser to check Create new issue and Bug report. Capture only the needed form area.
5. Leave the title blank and never tap Submit. Back out of the newly opened form tab and verify SnapKey Map returns. Restore the Details panel to its initial state.

## Gotchas

- Do not infer clipboard contents from a toast or claim that adb can read the full clipboard without testing it.
- The system preview can include report contents. Keep that screenshot local unless the user approves sharing it.
- Opening the form was proven on the installed 1.2.0 build. If a sign-in gate prevents checking prefilled fields, report the gate instead of signing in.
- A copied report replaces the previous clipboard. That previous value is not restored by returning to Home.
