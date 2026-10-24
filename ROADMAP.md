# Roadmap

Have an idea? [Request a feature](https://github.com/heykay-47/SnapKeyMap/issues/new?template=feature_request.yml).

## In testing: v1.1.0

This candidate combines the Android skill review with feedback from the [launch post](https://www.reddit.com/r/Oppo/comments/1xaze1m/i_built_an_app_to_remap_the_oppo_find_x9s_snap_key/). It is not a published release. Each change needs a manual check on the Find X9 running ColorOS 17 before release.

- [ ] **First-open introduction.** Show the animated logo before setup on a new install. Existing users skip it. With animations disabled, show the finished logo.
- [ ] **Less background work.** Stop screen-specific polling when the activity stops, filter touch traffic out of the input pipe, and skip log parsing while precise input handles the key. Measure CPU and screen-off battery use before claiming savings.
- [ ] **Reader recovery.** Keep reconnect backoff across short-lived connections and reset it after a healthy connection. Keep log detection available if precise input fails.
- [ ] **Flashlight state.** Observe the flashlight before the first toggle, including changes made through Quick Settings.
- [ ] **Ringer feedback.** Use two distinct buzzes for Vibrate and one longer buzz for Ring. Keep the shared Shizuku command connection on coroutine cancellation. ([#29](https://github.com/heykay-47/SnapKeyMap/issues/29))
- [ ] **Picker and keyboard.** Keep search results above the keyboard, show the current action first, size icons for the display density, and expose selection to TalkBack.
- [ ] **Preset editing.** Preserve an unfinished ringer preset across rotation. Keep the link dialog usable in landscape.
- [ ] **Setup and help.** Explain disabled Next buttons, animate step changes, add a Details toggle to the status card, and give Help cards useful summaries.
- [ ] **Open-source licences.** Add a licence page under About.
- [ ] **Build checks.** Use the checksum-pinned Gradle wrapper in both CI builds and validate its JAR. Upload advisory Android Lint reports with an explicit outcome. Cancel superseded branch builds without cancelling main or release-tag builds.
- [ ] **Release transition.** Publish v1.1.0 with versionCode 10100 after manual approval. Then remove the superseded v1.0.18 release and tag. Keep the same signing certificate so existing installations can update without uninstalling.

## Next: F-Droid

The F-Droid submission will request the same developer-signed APK as GitHub Releases. If accepted, you can switch between the two without uninstalling and keep the log permission.

- [x] **Free software license.** SnapKey Map is licensed under GPL-3.0-or-later.
- [x] **Store listing in the repository.** Title, descriptions, icon, and screenshots live in `fastlane/metadata`, where F-Droid reads them.
- [x] **Version codes set in the source.** F-Droid builds the same version code as GitHub, instead of one that depends on the CI run number.
- [x] **Reproducible builds.** CI checks that an independent build from source matches the signed release byte for byte. F-Droid needs this before it can publish the developer-signed APK.
- [x] **Release v1.0.15 with release notes.** The first release built for F-Droid. Each release since then passes the reproducibility check.
- [ ] **Submit to F-Droid.** Open a merge request in [fdroiddata](https://gitlab.com/fdroid/fdroiddata) with the build recipe. Review can take days or weeks.

## Later: Google Play

- [ ] **Same signing key on Google Play.** Upload the existing key to Play App Signing before the first upload, so Play updates install over GitHub and F-Droid installs.
- [ ] **Accessibility disclosure.** Explain what the accessibility service does and ask for consent before the setup step, as Google Play requires for apps that are not accessibility tools.
- [ ] **Battery step through system settings.** Open Android's battery optimisation list instead of requesting the exemption directly, which Google Play restricts.
- [ ] **App bundle builds.** CI builds the Android App Bundle that Google Play requires.
- [ ] **Privacy policy and Data safety.** Publish a privacy policy on the website and declare that the app collects no data.
- [ ] **Closed test.** New personal developer accounts need 12 testers opted in for 14 days before a production release.

## Previously published in v1.0.18

These changes remain in v1.1.0. The v1.0.18 release stays available until v1.1.0 is ready.

- [x] **Circle to Search.** Open Google's Circle to Search over the current screen. It needs Google as your digital assistant app, on firmware where Circle to Search is available.
- [x] **Split screen removed.** Android 13 and later ignore this action, so it did nothing. A gesture that used it now does nothing until you choose another action.

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
- [x] **Feel the ringer mode.** Ring uses one long buzz, Vibrate uses three short pulses, and Silent uses a tick. The v1.1.0 candidate changes the Vibrate pattern to two longer buzzes.
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
