# Setup guide

This guide covers installing and updating SnapKey Map, the setup wizard, log access, and the accessibility permission. For problems, see [Troubleshooting](troubleshooting.md).

## Installation

1. Open the [latest release](https://github.com/heykay-47/SnapKeyMap/releases/latest) on your phone.
2. Download `SnapKeyMap-*.apk` and open it.
3. Allow your browser or file manager to install apps when Android asks.

You do not need a GitHub account to download the APK.

To update, install the new APK over the existing app. Do not uninstall first. An update signed with the same key keeps your mappings and the granted log permission.

With Shizuku running and allowed, run **Teach the Snap Key again** after updating to enable precise press and release detection. Your mappings stay unchanged. Without Shizuku, the app keeps using the system log.

> [!IMPORTANT]
> After updating from v1.0.3 or earlier, run **Teach the Snap Key again** once before you test your mappings. Older calibration can pick a log line that matches only key-down events and merges double presses. Your action assignments stay unchanged.

## Setup wizard

Open SnapKey Map. The wizard has seven steps. It saves your progress and resumes at the first unfinished step.

1. **Choose a method.** Shizuku works without a computer. Computer (ADB) uses a USB cable.
2. **Give log access.** Follow the [steps for your method](#log-access-shizuku--adb).
3. **Turn on accessibility.** See [below](#how-to-grant-the-accessibility-permission).
4. **Background operation (optional).** Allow the battery-optimisation exemption to reduce ColorOS interruptions, or tap **Skip**. Background reliability is not guaranteed.
5. **Notifications (optional).** Allow alerts for paused detection, or tap **Skip**. Actions run without notifications.
6. **Teach the key.** Set Oppo's own Snap Key action to **None** in phone Settings. Then follow the prompts: press three times and hold once.
7. **Choose actions.** Tap **Press**, **Double press**, **Press & hold**, or **Tap, then hold**. Assign at least one action, then tap **Finish**.

Test each gesture and check **Last:** in the Ready card. You can change actions on the home screen at any time. To repeat setup without clearing your mappings, choose **Run setup again** in Help.

## Log access (Shizuku & ADB)

ColorOS does not pass the Snap Key to apps. SnapKey Map detects it from the system log, so it needs the `READ_LOGS` permission once.

### Shizuku (no computer)

1. Install and start [Shizuku](https://shizuku.rikka.app/guide/setup/) with wireless debugging (Android 11+).
2. In the wizard's log-access step, tap **Grant with Shizuku** and allow the permission request. The button is enabled while Shizuku is running.

Keep Shizuku running. The app then reads logs through Shizuku and Android's device-logs prompt never appears. Shizuku may need starting again after a phone restart. If Shizuku stops, the app pauses until you allow Android's one-time device-log prompt or start Shizuku again. True Silent in ringer presets also needs Shizuku.

If ColorOS blocks the grant, see [ADB or Shizuku refuses to grant log access](troubleshooting.md#adb-or-shizuku-refuses-to-grant-log-access). You can also go back and choose Computer (ADB).

### ADB (with a computer)

1. Download Google's free [SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools) and unzip them.
2. On the phone, open **Settings → About device → Version**. Tap the build or version number seven times to enable Developer options.
3. In Developer options, turn on **USB debugging**.
4. Connect the phone with a USB cable and accept the USB debugging prompt.
5. Open a terminal in the unzipped `platform-tools` folder and run:

```shell
adb shell pm grant com.heykay.snapkeymap android.permission.READ_LOGS
```

In Windows PowerShell, use `.\adb` in place of `adb` if the command is not on your PATH. Success returns to the prompt without a message.

The ADB grant survives restarts and app updates. Without Shizuku, Android asks for one-time device-log access after each app or phone restart, and may ask again when the reader reconnects. The accessibility service tries to show that prompt for you. If ColorOS blocks it, reopen SnapKey Map and choose **Allow**.

## How to grant the accessibility permission

1. In the wizard, tap **Open accessibility settings**.
2. Find **SnapKey Map** and turn it on.
3. If Android blocks the switch, open **App info** for SnapKey Map, tap the three-dot menu, choose **Allow restricted settings**, and try again.

The service runs accessibility actions such as screenshot and lock, opens apps and the device-logs prompt from the background, receives supported key events, and helps keep the app running. It also sees which app is in front by package name only. The latest package name stays in memory and is used only to pause in the apps you choose. It cannot read screen content.
