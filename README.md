# SnapKey Map

Assign actions to the Oppo Find X9's Snap Key. Set a different action for a single press, double press, or press and hold.

Open an app, run a supported app shortcut, open a link, toggle the flashlight, take a screenshot, or control music. Android and ColorOS still restrict some actions. This app cannot bypass the lock screen or control every function in every app.

The app follows Material 3 with dynamic colour and a System, Light, or Dark theme choice.

No root or paid tools are required. Download a prebuilt APK or use GitHub Actions to build one without installing the Android build tools on your computer. Private repository builds use your account's Actions allowance.

- [Install and set up](#install-and-set-up)
- [Troubleshoot](#troubleshoot)
- [Permissions and limits](#permissions-and-limits)
- [Develop the app](#develop-the-app)
- [Author and credits](#author-and-credits)

## Install and set up

You need an Oppo Find X9 and either a running Shizuku or a USB cable and computer for the one-time permission setup. Other devices and ColorOS versions may behave differently.

### 1. Install the APK

1. Open the [latest release](https://github.com/heykay-47/SnapKeyMap/releases/latest) on your phone.
2. Download `SnapKeyMap-*.apk` and open it.
3. Allow your browser or file manager to install apps when Android asks.

The repository and release downloads are public. You do not need a GitHub account to download the APK.

To update, install the new APK over the existing app. Do not uninstall first. An update signed with the same key preserves your mappings and granted log permission.

### 2. Enable the accessibility service

Open SnapKey Map and tap **Open settings** in the first setup step. Find SnapKey Map in the accessibility settings and turn it on.

If Android blocks the switch, open SnapKey Map's **App info**. Tap the three-dot menu, choose **Allow restricted settings**, and try again.

### 3. Grant log access once

**Without a computer:** install and start [Shizuku](https://shizuku.rikka.app/guide/setup/) using wireless debugging (Android 11+). Open SnapKey Map's log-access screen, tap **Grant with Shizuku**, and allow its permission request. The button appears only while Shizuku is running. You do not need to keep Shizuku running after the grant. If ColorOS blocks it, turn on **Disable permission monitoring** in Developer options, restart the phone (the setting only takes effect after a restart), start Shizuku again and retry. Or use the computer method below.

**With a computer:**

1. Download Google's free [SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools) on your computer and unzip them.
2. On the phone, open Settings, then About device, then Version. Tap the build or version number seven times to enable Developer options.
3. In Developer options, turn on USB debugging.
4. Connect the phone with a USB cable. Accept the USB debugging prompt on the phone.
5. Open a terminal in the unzipped `platform-tools` folder and run:

```shell
adb shell pm grant com.heykay.snapkeymap android.permission.READ_LOGS
```

In Windows PowerShell, use `.\adb` in place of `adb` if the command is not on your PATH.

Success normally returns to the command prompt without a message. If you get `GRANT_RUNTIME_PERMISSIONS`, follow the [ColorOS permission fix](#adb-refuses-to-grant-log-access).

Reopen SnapKey Map and choose **Allow** when Android asks for access to device logs. The ADB grant survives restarts and app updates. Android may ask for consent again when the log reader reconnects.

### 4. Choose your actions

1. Open phone Settings, then Snap Key, and set Oppo's own action to **None**. Otherwise both actions may run.
2. Return to SnapKey Map and start **Teach the app your Snap Key**.
3. Follow the prompts to press the key three times and hold it once.
4. Tap **Press**, **Double press**, or **Press & hold** on the home screen and choose an action.
5. Test each gesture. Check the **Last:** text in the Ready card to see what the app detected.

Allow notifications so the app can tell you when detection pauses. In App info, open Battery and allow background activity to reduce the chance of ColorOS closing the app.

After updating from v1.0.3 or earlier, run **Teach the Snap Key again** once before testing your mappings. Older calibration can select a message that matches only key-down events and merges double presses. The new learning step prefers the known Oppo press-and-release source when it is present. Your action assignments stay unchanged.

## Troubleshoot

### ADB refuses to grant log access

An error containing `Neither user 2000` and `GRANT_RUNTIME_PERMISSIONS` means ColorOS is blocking ADB from granting permissions.

1. Open Developer options on the phone.
2. Turn on **Disable permission monitoring**. Newer firmware may call it **Disable system optimization**.
3. If neither setting appears, temporarily switch the phone's system language to English, United States, and reopen Developer options.
4. Read the warning before accepting. This setting relaxes manufacturer restrictions and may increase background power use.
5. Restart the phone. On ColorOS 16 the setting only takes effect after a restart; reconnecting the cable is not enough. Then run the grant command again.

The name and location depend on your firmware. If the setting is still missing, include your ColorOS version when reporting the problem.

### The key stops working after a restart

Open SnapKey Map and allow device log access again. If the app shows **Paused**, tap **Connect**. You do not need to repeat the ADB grant unless you uninstalled the app or revoked the permission.

If you declined the log-access prompt, wait a minute before trying again. Also check that the accessibility service remains enabled and that ColorOS allows background activity.

### A double press runs the single-press action

First set Oppo's own Snap Key action to None. Keep SnapKey Map open, double-press the key, and check the **Last:** text.

- If it says `press`, the app did not recognise the double press. Use v1.0.3 or later, which fixes duplicate key events in the recorded Find X9 log format.
- If it says `double press`, check the assigned action and confirm that the selected app is still installed.

When reporting a detection problem, include your app version, ColorOS version, assigned actions, and the text shown under Last. Do not share an unfiltered system log. Device logs can contain sensitive information.

## Permissions and limits

The accessibility service receives supported key events and runs actions such as screenshot, home, back, and lock. Its configuration does not allow it to read window content.

On the Find X9, the app reads system logs to detect the Snap Key. The log permission grants access to more than key events. SnapKey Map processes those logs on the phone and does not upload them. The app declares no internet permission. Apps and websites you open through an action have their own privacy policies.

Optional Shizuku access is used only to grant the log permission, not to read logs or run mapped actions.

Keep these limits in mind:

- A double-press assignment adds a 350 ms wait after release before the app runs the single-press action.
- The known Find X9 log reports duplicated press and release events. Other learned log formats need repeated events while held to support press and hold.
- Screen-off detection and background reliability depend on ColorOS. They are not guaranteed.
- The shortcut picker supports apps that provide `ACTION_CREATE_SHORTCUT`. It cannot list every pinned shortcut. A deep link may work instead.
- Direct-dial contact shortcuts are not supported, because the app has no calling permission. The app shows a message instead.
- The app accepts a key log line only from the app that wrote it while you taught the key. On the Find X9, that is the system. Other apps can't fake a Snap Key press by writing the same line.
- Mappings and the learned key are not included in Android backups.
- Silent mode and Do Not Disturb need notification policy access. Without it, the ringer action cycles between ring and vibrate.
- System actions depend on Android permissions, the lock-screen state, and device support.

## Develop the app

The app uses Kotlin, Jetpack Compose, and Material 3. The package name is `com.heykay.snapkeymap`. The minimum Android version is 10, API 29, and the compile and target SDK are 36.

### Find the relevant code

Paths below are relative to [`app/src/main/java/com/heykay/snapkeymap`](app/src/main/java/com/heykay/snapkeymap).

- `ui/` contains the screens and shared UI components.
- `SnapEngine.kt` connects key detection to the configured actions.
- `LogReader.kt` reads logcat and reports log-access status.
- `KeyLearning.kt` identifies the key during setup.
- `OplusKeyEdges.kt` removes duplicate log events and separates presses from releases.
- `GestureDetector.kt` recognises single press, double press, and hold.
- `SnapAction.kt` defines the actions. `ActionRunner.kt` executes them.
- `ConfigStore.kt` saves mappings and key configuration in SharedPreferences.
- `SnapAccessibilityService.kt` receives key events and provides accessibility actions.

The [unit tests](app/src/test/java/com/heykay/snapkeymap) cover key learning and gesture detection. They include a replay of recorded Find X9 event timings. Passing tests do not replace testing on a phone.

### Find X9 device notes

These facts were checked on a Find X9 with ColorOS 16 (`CPH2797_16.0.10.600`). They may change with firmware updates.

- The Snap Key writes `KEYLOG_OplusKeyEventUtil: should not notify undefined keys in restrict listen mode` on every press and every release. Each edge appears two or three times within the same millisecond or the next. Real edges are at least 60 ms apart.
- The line comes from UID 1000 (system). Run `adb logcat -v threadtime,uid -s KEYLOG_OplusKeyEventUtil` to see it.
- With **Permission monitoring** on, ColorOS blocks `pm grant` and `pm revoke` from the shell, for both ADB and Shizuku. Turning on **Disable permission monitoring** takes effect only after a restart.
- The shell cannot revoke `READ_LOGS`, so you can't reset the grant to test a first-time grant. Reinstalling the app resets it.
- To count actions exactly, check `adb shell dumpsys media.camera`. Its event log records each flashlight on and off.

### Build with GitHub Actions

The [Build APK workflow](.github/workflows/build.yml) runs tests and builds an APK on pushes to `main`, pull requests, version tags, and manual runs.

To start a manual build, open the repository's Actions tab, select Build APK, and choose Run workflow. After a successful run, download the `SnapKeyMap` artifact and extract the APK. The `test-results` artifact contains the unit test reports.

Pull request builds get no signing secrets and use a temporary debug key. Pushes to `main`, tags, and manual runs use the release key.

After the build, the workflow compares the APK's permissions with [`app/expected-permissions.txt`](app/expected-permissions.txt) and fails on any difference. If a change needs a new permission, update that file in the same change and explain why.

The workflow uses JDK 17 and Gradle 8.14.3. It runs:

```shell
gradle --no-daemon testReleaseUnitTest assembleRelease
```

### Build locally

Install JDK 17, Gradle 8.14.3, and Android SDK Platform 36. Set `ANDROID_HOME` to your SDK directory, or set `sdk.dir` in an untracked `local.properties` file.

This repository does not include a Gradle wrapper. Run the installed `gradle` command from the repository root:

```shell
gradle testReleaseUnitTest
gradle assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. Local builds use the debug signing key unless you provide the signing environment variables defined in `app/build.gradle.kts`.

### Sign builds and publish releases

Configure these repository secrets for a consistent release signing key:

- `SIGNING_KEYSTORE_BASE64` holds the base64-encoded PKCS12 keystore.
- `SIGNING_STORE_PASSWORD` holds the keystore password.
- `SIGNING_KEY_ALIAS` identifies the signing key.
- `SIGNING_KEY_PASSWORD` holds the key password.

Back up the keystore and passwords outside Git. Without the signing secrets, CI uses a temporary debug key. That APK may not install over an earlier build. Never commit signing keys or passwords.

To publish a release:

1. Set `appVersion` in [`gradle.properties`](gradle.properties) to the version you want to release.
2. Commit the release changes and push them to `main`.
3. Check that the tests and build pass.
4. Create and push a matching version tag. For example, if `appVersion` is `1.0.8`, run:

```shell
git tag v1.0.8
git push origin v1.0.8
```

The tag starts another build and publishes the APK on GitHub Releases. The workflow rejects a tag that does not match `appVersion`. The release tag, APK filename, and app version share that version. Android's internal `versionCode` uses the workflow run number so updates can install over earlier CI builds.

## Author and credits

Krithik, [heykay-47 on GitHub](https://github.com/heykay-47), is the author of SnapKey Map.

Version 1.0.8 adds an About / Credits page with the author's GitHub photo, app version, and repository and profile links. Open it with the home screen's info button. The photo is bundled and works offline.

The system-log detection approach was informed by the OnePlus 15 [Plus Key Remapper](https://github.com/SelenStudio/plus-key-remapper). SnapKey Map uses free Android development tools and GitHub Actions for builds.
