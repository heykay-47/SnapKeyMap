# Roadmap

Have an idea? [Request a feature](https://github.com/heykay-47/SnapKeyMap/issues/new?template=feature_request.yml).

## Next: F-Droid

The F-Droid submission will request the same developer-signed APK as GitHub Releases. If accepted, you can switch between the two without uninstalling and keep the log permission.

- [x] **Free software license.** SnapKey Map is licensed under GPL-3.0-or-later.
- [x] **Store listing in the repository.** Title, descriptions, icon, and screenshots live in `fastlane/metadata`, where F-Droid reads them.
- [x] **Version codes set in the source.** F-Droid builds the same version code as GitHub, instead of one that depends on the CI run number.
- [x] **Reproducible builds.** CI checks that an independent build from source matches the signed release byte for byte. F-Droid needs this before it can publish the developer-signed APK.
- [x] **Release v1.0.15 with release notes.** The first release built for F-Droid. Each release since then passes the reproducibility check.
- [ ] **Submit to F-Droid.** Open a merge request in [fdroiddata](https://gitlab.com/fdroid/fdroiddata) with the build recipe. Review can take days or weeks.

## Delivered in v1.2.0

Quality-of-life additions for everyday use. Behaviour on other firmware may differ.

- [x] **Try a gesture.** Tap ▶ next to an assigned gesture on Home to run its action at once. Try always shows its confirmation, even with Show confirmations off.
- [x] **Quick Settings tile.** Add the **Snap Key** tile to turn the mapping on or off without opening the app. Long-press the tile to open the app.
- [x] **Back up and restore.** Save gesture actions, ringer presets, paused apps, timing, theme and the learned key to a file you choose, and restore them later. A gesture mapped to an app that is no longer installed shows "App not installed".
- [x] **Bug reports.** **Details** on the status card can copy a report with your versions, setup and last 20 detections, or open the bug form with its fields filled in. Reports name only the kind of each action, never contact numbers, links or app names.
- [x] **Safer teaching.** After teaching, press a different key, such as volume. The app keeps the learned key only if that press doesn't match it, and saves it only when you tap Save. Cancelling or a failed check keeps your previous settings.
- [x] **Clearer accessibility disclosure.** The service description, store listing and privacy page say that Android passes every hardware key press to the service and tells it which app is in front. The app acts only on the key you taught it.

## Delivered in v1.1.0

This release replaces v1.0.18 and includes its changes. It combines a review against Android's Compose, lifecycle and accessibility guidance with feedback from the [launch post](https://www.reddit.com/r/Oppo/comments/1xaze1m/i_built_an_app_to_remap_the_oppo_find_x9s_snap_key/). It was tested manually on a Find X9 running ColorOS 17. Behaviour on other firmware may differ.

- [x] **First-open introduction.** A new install shows an animated logo before setup. Existing users skip it. With animations turned off, the finished logo appears.
- [x] **Less background work.** Status checks stop when you leave the app. Touch events no longer reach the app with Shizuku, and log lines are not parsed while precise input handles the key.
- [x] **Reader recovery.** A log stream that keeps dropping waits longer between retries. Log detection stays available if precise input fails.
- [x] **Flashlight state.** The first press turns the flashlight off if it was already on, including from Quick Settings.
- [x] **Ringer feedback.** Vibrate uses two distinct buzzes and Ring uses one longer buzz, so they feel different on ColorOS. ([#29](https://github.com/heykay-47/SnapKeyMap/issues/29))
- [x] **Picker and keyboard.** Search results stay above the keyboard, the current action is listed first, app icons are sharper, and TalkBack reads which row is selected.
- [x] **Preset editing.** An unfinished ringer preset survives rotation. The link dialog scrolls in landscape.
- [x] **Setup, Home and Help.** Setup explains why Next is disabled and animates between steps. The Home header shows the logo, Teach again sits under the status card, and a Details button holds the diagnostics. Help cards show a one-line summary.
- [x] **Open-source licences.** About links to a list of libraries with their full licence texts, readable offline.
- [x] **Circle to Search.** Open Google's Circle to Search over the current screen. It needs Google as your digital assistant app, on firmware where Circle to Search is available.
- [x] **Split screen removed.** Android 13 and later ignore this action, so it did nothing. A gesture that used it now does nothing until you choose another action.
- [x] **Build checks.** CI uses the checksum-pinned Gradle wrapper and validates it, uploads advisory Android Lint reports, and cancels superseded branch builds.

## Delivered in v1.0.17

This release was tested on a Find X9. Behaviour on other firmware may differ.

- [x] **Tap, then hold.** Press once, then press again and hold to run a fourth action. A short second press still counts as a double press. ([#24](https://github.com/heykay-47/SnapKeyMap/issues/24))
- [x] **Only when unlocked.** Tap the lock next to a gesture so its action doesn't run while the phone is locked. ([#25](https://github.com/heykay-47/SnapKeyMap/issues/25))
- [x] **Keyboard input no longer triggers actions.** Keys typed on a keyboard, including from a PC while casting, are ignored. Teach the Snap Key again once after updating. ([#27](https://github.com/heykay-47/SnapKeyMap/issues/27))
- [x] **Ringer cycle with Do Not Disturb on.** With Shizuku, Ring, Vibrate and Silent cycle normally while Do Not Disturb is on. ([#23](https://github.com/heykay-47/SnapKeyMap/issues/23))

## Delivered in v1.0.16

This release was tested on a Find X9. Behaviour on other firmware may differ.

- [x] **Hide confirmation pop-ups.** Turn off Show confirmations to hide ringer and Do Not Disturb messages. Errors and permission reminders still show.
- [x] **Consistent background messages.** Confirmation messages also appear while SnapKey Map is in the background.
- [x] **Feel the ringer mode.** Ring uses one long buzz, Vibrate uses three short pulses, and Silent uses a tick. v1.1.0 changes the Vibrate pattern to two longer buzzes.
- [x] **Pause in chosen apps.** Pause mapped actions while a chosen app, such as the camera, is in front.
- [x] **App icons and Clear all.** The paused-app picker shows app icons and has a **Clear all** button to remove all selections.
- [x] **Settings and link tips.** Open phone Settings as a fresh task on ColorOS. The link dialog shows how to fill in a message with an `sms:` link.

## Delivered in v1.0.14

Delivered in v1.0.14 from feedback on the [launch post](https://www.reddit.com/r/Oppo/comments/1xaze1m/i_built_an_app_to_remap_the_oppo_find_x9s_snap_key/). The release was tested manually on a Find X9. Behaviour on other phones and firmware may differ.

- [x] **Oppo's native Silent mode with Shizuku.** Ringer presets use native Silent on Android 15 or later while Shizuku is running. Without Shizuku, Silent needs Do Not Disturb access and may turn on Do Not Disturb.
- [x] **Choose your ringer modes.** Create and edit presets with two or three modes in your chosen order. Editing a preset updates the gestures that use it.
- [x] **Grant Do Not Disturb access with Shizuku.** Request access from the app when your phone's Settings switch is unavailable.
- [x] **More precise key detection with Shizuku.** Teach the key again to use explicit press and release events instead of inferred log edges. Log detection remains the fallback.
- [x] **Adjustable timing.** Set the double-press window from 200 to 600 ms and the hold threshold from 300 to 1200 ms.
- [x] **Mute media toggle.** Mute and unmute media with one press.
