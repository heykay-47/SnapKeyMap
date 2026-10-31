# About and licences

About shows the app's name, version and author, links to the source code and to sponsorship, and opens a screen of open-source licences. Those licence texts are bundled in the app and can be read offline.

## Sub-features

- `about-version`: About shows `Version <versionName> · By Krithik (heykay-47)`.
- `about-links`: `Source code`, `Author’s GitHub`, `Buy Me a Tea` and `Sponsor on GitHub` open the browser.
- `licences-index`: `Open-source licences` lists the libraries, then `Complete attribution notices`, then `Licence texts`.
- `licences-detail`: tapping a library shows its name, versions, `Project website`, `Read complete attribution notices` and the full licence text.

## How to get to it (user POV)

- Home → the `About` (i) icon in the toolbar.
- About → `Open-source licences` at the bottom.
- Licences → any library row, such as `Shizuku`.

## Driving it with sk

Preconditions:

- Baseline from the README. Nothing is changed, so nothing needs restoring.

- **Open About.** Tap the toolbar icon. Run `sk tap About --expect "Open-source licences"`, then `sk ui about`. The text shows `Version <versionName> · By Krithik (heykay-47)`, matching `sk doctor`.
- **Open licences.** Run `sk tap "Open-source licences" --expect Libraries`, then `sk ui licences`. The title is `Open-source licences`, and the rows include `Shizuku` / `MIT (Shizuku)` and `Hidden API Bypass` / `Apache-2.0`.
- **Read one licence.** Run `sk tap Shizuku --expect "Project website"`, `sk ui shizuku`, then `sk shot shizuku`. The detail shows the library's full name and version (`Shizuku API, provider, aidl and shared 13.1.5` in 1.1.0), the `Project website` and `Read complete attribution notices` buttons, the heading `MIT (Shizuku)`, and licence text starting `MIT License` / `Copyright (c) 2021 RikkaW`.
- **Back out.** Run `sk back` three times, then `sk wait "What the Snap Key does"`. Back goes detail → index → About → Home.
- **Links (optional).** `sk tap "Source code"` opens the browser on github.com/heykay-47/SnapKeyMap. Check it with `sk ui --all` (the foreground package is the browser), then use `sk back` to return.

## Gotchas

- `Licence texts` sits below the fold on a 2760 px screen. Run `sk scroll down` before you wait for it.
- On 2026-10-28 the first `sk tap Shizuku` was dropped without error: the screen stayed on the index. Always pass `--expect`.
- Back from the licence detail returns to the index, not to About. Count your `sk back` presses.
