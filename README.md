# SnapKey Map

Make the Snap Key on an Oppo Find X9 do anything: open any app, run an app shortcut, open a link, toggle the flashlight, take a screenshot, control music, and more. You can set a different action for a press, a double press, and a press & hold.

Everything is free. The APK is built by GitHub Actions, so your computer doesn't have to build it.

## How it works

ColorOS doesn't hand the Snap Key to other apps the way it does with volume keys. But each press shows up in the phone's system log. SnapKey Map watches that log on the phone and runs your action when the Snap Key line shows up. Nothing leaves the phone. The OnePlus 15 [Plus Key Remapper](https://github.com/SelenStudio/plus-key-remapper) uses the same idea.

This needs two one-time permissions:

1. **Accessibility service.** Lets the app keep running, open apps from the background, and do things like screenshot, home, back, and lock.
2. **Log access (`READ_LOGS`).** Android only lets you grant this from a computer with ADB, once. It stays after restarts and app updates.

Android asks "Allow access to all device logs?" each time the app starts listening, and it can only ask while the app is open. After a phone restart you get a notification. Tap it and choose **Allow**.

## Install

1. On the phone, open this repository's **Releases** page and download the latest `SnapKeyMap-*.apk`. You can also download it from the **Actions** tab: open a run and look under *Artifacts*.
2. Open the APK and allow your browser or file manager to install apps.
3. Open **SnapKey Map** and follow the setup steps on the main screen:
   - **Turn on the SnapKey Map service.** If the switch is greyed out ("Restricted setting"), open App info, tap ⋮, choose **Allow restricted settings**, and try again.
   - **Give one-time log access.** On the computer, install Google's free [SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools). Turn on USB debugging on the phone, plug it in, and run:

     ```
     adb shell pm grant com.heykay.snapkeymap android.permission.READ_LOGS
     ```

     If ColorOS refuses, turn on **Disable permission monitoring** in Developer options and run the command again.
   - **Teach the app your Snap Key.** First set Oppo's own Snap Key action to **None** (Settings › Snap Key). Then press the key 3 times and hold it once when the app asks. The app works out which log line the key writes.
4. Pick what **Press**, **Double press** and **Press & hold** should do.

Also recommended: allow notifications, and let the app run in the background (App info › Battery). Otherwise ColorOS may close it.

## Limits

- Press & hold only works if your phone keeps writing the log line while the key is held. The teach step checks this and tells you.
- With a double press action set, a single press waits about a third of a second to see if a second press follows.
- With the screen off, a press is handled when the phone wakes up enough to read the log. That isn't guaranteed on every phone.
- Shortcuts from apps that only support the new pinned-shortcut API can't be picked. Use **Open a link** with a deep link instead.

## Building

Every push to `main` builds a signed APK in GitHub Actions (see `.github/workflows/build.yml`). Pushing a tag like `v1.0.1` also publishes it as a release:

```
git tag v1.0.1
git push origin v1.0.1
```

The signing key is stored in the repository secrets `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`. Every build uses the same key, so updates install over the old version. Without these secrets, the build falls back to a throwaway debug key.

To build locally instead, run `gradle assembleRelease` with JDK 17, Gradle 8.14 and the Android SDK installed.
