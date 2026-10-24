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

## Later: Google Play

- [ ] **Same signing key on Google Play.** Upload the existing key to Play App Signing before the first upload, so Play updates install over GitHub and F-Droid installs.
- [ ] **Accessibility disclosure.** Explain what the accessibility service does and ask for consent before the setup step, as Google Play requires for apps that are not accessibility tools.
- [ ] **Battery step through system settings.** Open Android's battery optimisation list instead of requesting the exemption directly, which Google Play restricts.
- [ ] **App bundle builds.** CI builds the Android App Bundle that Google Play requires.
- [ ] **Privacy policy and Data safety.** Publish a privacy policy on the website and declare that the app collects no data.
- [ ] **Closed test.** New personal developer accounts need 12 testers opted in for 14 days before a production release.

## Delivered in v1.0.18

From feedback on the [launch post](https://www.reddit.com/r/Oppo/comments/1xaze1m/i_built_an_app_to_remap_the_oppo_find_x9s_snap_key/). This release was tested on a Find X9. Behaviour on other firmware may differ.

- [x] **Circle to Search.** Open Google's Circle to Search over the current screen. It needs Google as your digital assistant app, on firmware where Circle to Search is available.
- [x] **Ring and Vibrate feel different on ColorOS.** Vibrate now gives two distinct buzzes instead of short pulses, which ColorOS turned into light clicks that felt like the Ring buzz. ([#29](https://github.com/heykay-47/SnapKeyMap/issues/29))
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
- [x] **Feel the ringer mode.** Ring uses one long buzz, Vibrate uses two short buzzes (since v1.0.18), and Silent uses a tick.
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
