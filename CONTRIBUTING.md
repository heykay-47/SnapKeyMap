# Contributing to SnapKey Map

Thanks for helping. This guide covers reporting problems, the code layout, Find X9 device notes, and how to build, sign, and release the app.

## Report a problem

Use the [bug report form](https://github.com/heykay-47/SnapKeyMap/issues/new?template=bug_report.yml). Include your app version, phone model, ColorOS version, log-access method, what the teach step showed, your assigned actions, and the text under **Last:**. Do not attach an unfiltered system log. Device logs can contain sensitive information.

For ideas, use the [feature request form](https://github.com/heykay-47/SnapKeyMap/issues/new?template=feature_request.yml).

## Pull requests

- Keep each pull request to one change.
- Run the unit tests. Passing tests do not replace testing on a phone, so say whether and where you tested on a device.
- If the change needs a new Android permission, update [`app/expected-permissions.txt`](app/expected-permissions.txt) in the same pull request and explain why.

## Develop the app

The app uses Kotlin, Jetpack Compose, and Material 3. The package name is `com.heykay.snapkeymap`. The minimum Android version is 10, API 29, and the compile and target SDK are 36.

### Find the relevant code

Paths below are relative to [`app/src/main/java/com/heykay/snapkeymap`](app/src/main/java/com/heykay/snapkeymap).

- `ui/` contains the screens and shared UI components.
- `SnapEngine.kt` connects key detection to the configured actions.
- `LogReader.kt` reads logcat and reports log-access status.
- `InputReader.kt` reads shell input through Shizuku; `InputKey.kt` parses explicit `getevent -lt` edges.
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
- The shell can also read the Snap Key on `/dev/input/event0` as `BTN_TRIGGER_HAPPY32` with `adb shell getevent -lt`. Normal apps cannot read these devices. The app learns the input code alongside the log signature through Shizuku, without pinning the device path. Confirm this on other firmware before relying on it.
- The home status says **Precise input via Shizuku** only after the input reader has received a valid key edge and a key was learned. **Logs via Shizuku** or **System logs** means log fallback is in use. Stopping Shizuku resets gestures and reconnects the local log reader; Android may require device-log consent again.
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

The repository has no `gradlew` script. It keeps only `gradle/wrapper/gradle-wrapper.properties`, which tells F-Droid to build with Gradle 8.14.3. Run the installed `gradle` command from the repository root:

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

1. In [`app/build.gradle.kts`](app/build.gradle.kts), set `versionName` to the new version and `versionCode` to major × 10000 + minor × 100 + patch. For example, `1.0.15` is `10015`. The build fails if the two disagree.
2. Write the release notes, at most 500 characters, in `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`. F-Droid shows this file.
3. Commit the release changes and push them to `main`.
4. Check that the tests and build pass.
5. Create and push a matching version tag. For example, for `1.0.15`, run:

```shell
git tag v1.0.15
git push origin v1.0.15
```

The tag starts another build and publishes the APK on GitHub Releases. The workflow rejects a tag that does not match `versionName` or has no changelog file. The release tag, APK filename, and app version share that version.

F-Droid reads `versionName` and `versionCode` as literals from `app/build.gradle.kts`, and the store listing from [`fastlane/metadata/android/en-US`](fastlane/metadata/android/en-US). Keep both values as plain literals. Releases up to v1.0.14 used the CI run number as `versionCode`, which never went above 67, so the new scheme installs over them.
