<p align="center">
  <img width="128" alt="SnapKey Map icon" src="docs/snapkeymap-svg/snapkeymap-icon.svg" />
</p>

<h1 align="center">SnapKey Map</h1>

<p align="center">
  Map the Oppo Find X9's Snap Key to any app or action.<br>
  A different action for a press, a double press, and a press and hold. No root needed.
</p>

<p align="center">
  <a href="https://github.com/heykay-47/SnapKeyMap/releases/latest"><img alt="Downloads" src="https://img.shields.io/github/downloads/heykay-47/SnapKeyMap/total?style=for-the-badge&logo=android&logoColor=%23fff&labelColor=%230657EA&color=%230657EA"></a>
  <a href="https://github.com/heykay-47/SnapKeyMap/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/heykay-47/SnapKeyMap?style=for-the-badge&logo=github&logoColor=%23fff&label=release&labelColor=%23072C86&color=%23072C86"></a>
  <a href="https://github.com/heykay-47/SnapKeyMap/issues/new?template=bug_report.yml"><img alt="Report a bug" src="https://img.shields.io/github/issues/heykay-47/SnapKeyMap/bug?style=for-the-badge&logo=openbugbounty&logoColor=%23fff&label=bugs&labelColor=%23d73a4a&color=%23d73a4a"></a>
  <a href="https://github.com/heykay-47/SnapKeyMap/issues/new?template=feature_request.yml"><img alt="Request a feature" src="https://img.shields.io/github/issues/heykay-47/SnapKeyMap/enhancement?style=for-the-badge&logo=apachespark&logoColor=%23fff&label=feature%20requests&labelColor=%231499F8&color=%231499F8"></a>
  <a href="https://heykay-47.github.io/SnapKeyMap/"><img alt="Website" src="https://img.shields.io/badge/Website-38BCFB?style=for-the-badge&logo=googlechrome&logoColor=%23072C86"></a>
  <a href="https://github.com/sponsors/heykay-47"><img alt="Sponsor on GitHub" src="https://img.shields.io/badge/Sponsor-EA4AAA?style=for-the-badge&logo=githubsponsors&logoColor=%23fff&labelColor=%23EA4AAA&color=%23EA4AAA"></a>
  <a href="https://buymeatea.in/heykay47"><img alt="Buy Me a Tea" src="https://img.shields.io/badge/Buy%20Me%20a%20Tea-C8811E?style=for-the-badge&logo=buymeacoffee&logoColor=%23fff&labelColor=%23C8811E&color=%23C8811E"></a>
</p>

---

## Navigation

- [Screenshots](#screenshots)
- [Features](#features)
- [Roadmap](#roadmap)
- [Requirements](#requirements)
- [Installation](#installation)
- [Setup wizard](#setup-wizard)
- [Log access (Shizuku & ADB)](#log-access-shizuku--adb)
- [Accessibility permission](#how-to-grant-the-accessibility-permission)
- [Troubleshooting](#troubleshooting)
- [Privacy and limits](#privacy-and-limits)
- [Contributing](#contributing)
- [Support](#support)
- [Full help and FAQ](https://heykay-47.github.io/SnapKeyMap/help.html)

# Screenshots

<p align="center">
  <img width="32%" alt="Home screen in the dark theme, showing the Ready card and the three gesture mappings" src="docs/screenshots/snapkeymap_ss1.jpg" />
  <img width="32%" alt="Home screen in the light theme" src="docs/screenshots/snapkeymap_ss2.jpg" />
  <img width="32%" alt="Action picker for the Press gesture, with Phone and Screen and navigation actions" src="docs/screenshots/snapkeymap_ss3.jpg" />
</p>
<p align="center">
  <img width="32%" alt="Action picker for the Double press gesture, with Screen and navigation and Music and video actions" src="docs/screenshots/snapkeymap_ss4.jpg" />
  <img width="32%" alt="Action picker with Open actions and More actions such as Call a contact, Open a link, and App shortcut" src="docs/screenshots/snapkeymap_ss5.jpg" />
  <img width="32%" alt="Action picker listing installed apps to open" src="docs/screenshots/snapkeymap_ss6.jpg" />
</p>
<p align="center">
  <img width="32%" alt="Help screen with Getting started, Troubleshooting, and Is it safe sections" src="docs/screenshots/snapkeymap_ss7.jpg" />
</p>

# Features

Assign one action each to **Press**, **Double press**, and **Press & hold**.

| Category | Actions |
| --- | --- |
| Phone | Flashlight, ring → vibrate → silent, Do Not Disturb |
| Screen & navigation | Screenshot, lock screen, notifications, quick settings, recent apps, home, back, power menu, split screen |
| Music & video | Play / pause, next and previous track, media volume up and down, volume slider |
| Open | Camera, video camera, voice assistant, Wi-Fi panel, Bluetooth settings, NFC panel, internet and mobile data panel |
| More | Call a contact (opens the dialer with their number), open a link, run a supported app shortcut |
| Open an app | Any installed app |

The app follows Material 3 with dynamic colour and a System, Light, or Dark theme. It declares no internet permission.

# Roadmap

Planned from feedback on the [launch post](https://www.reddit.com/r/Oppo/comments/1xaze1m/i_built_an_app_to_remap_the_oppo_find_x9s_snap_key/). Each item ships once it has been tested on a Find X9.

- [ ] **Silent mode that works out of the box.** Ring → Vibrate → Silent asks for Do Not Disturb access when you assign it, instead of quietly skipping Silent.
- [ ] **Choose your ringer modes.** Pick which modes the ringer action cycles through and in what order, such as Ring ↔ Vibrate only.
- [ ] **Grant Do Not Disturb access with Shizuku.** For phones where Settings won't let you allow it.
- [ ] **More precise key detection with Shizuku.** Read the key's real press and release while Shizuku runs, so a long hold can never count as a double press.
- [ ] **Adjustable timing.** Set the double-press window and how long counts as a hold.
- [ ] **Mute media toggle.** Mute and unmute media with one press.

Have an idea? [Request a feature](https://github.com/heykay-47/SnapKeyMap/issues/new?template=feature_request.yml).

# Requirements

- An Oppo Find X9. Other devices and ColorOS versions may behave differently. Users have also reported it working on the Find X9 Pro, X9 Ultra, X9s, X9s Pro, and OnePlus 15R.
- Android 10 or later.
- One of these, for the one-time log permission:
  - [Shizuku](https://shizuku.rikka.app/guide/setup/), running through wireless debugging. No computer needed.
  - A USB cable and a computer with Google's [SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools).

# Installation

1. Open the [latest release](https://github.com/heykay-47/SnapKeyMap/releases/latest) on your phone.
2. Download `SnapKeyMap-*.apk` and open it.
3. Allow your browser or file manager to install apps when Android asks.

You do not need a GitHub account to download the APK.

To update, install the new APK over the existing app. Do not uninstall first. An update signed with the same key keeps your mappings and the granted log permission.

With Shizuku running and allowed, run **Teach the Snap Key again** after updating to enable precise press and release detection. Your mappings stay unchanged. Without Shizuku, the app keeps using the system log.

> [!IMPORTANT]
> After updating from v1.0.3 or earlier, run **Teach the Snap Key again** once before you test your mappings. Older calibration can pick a log line that matches only key-down events and merges double presses. Your action assignments stay unchanged.

# Setup wizard

Open SnapKey Map. The wizard has seven steps. It saves your progress and resumes at the first unfinished step.

1. **Choose a method.** Shizuku works without a computer. Computer (ADB) uses a USB cable.
2. **Give log access.** Follow the [steps for your method](#log-access-shizuku--adb).
3. **Turn on accessibility.** See [below](#how-to-grant-the-accessibility-permission).
4. **Background operation (optional).** Allow the battery-optimisation exemption to reduce ColorOS interruptions, or tap **Skip**. Background reliability is not guaranteed.
5. **Notifications (optional).** Allow alerts for paused detection, or tap **Skip**. Actions run without notifications.
6. **Teach the key.** Set Oppo's own Snap Key action to **None** in phone Settings. Then follow the prompts: press three times and hold once.
7. **Choose actions.** Tap **Press**, **Double press**, or **Press & hold**. Assign at least one action, then tap **Finish**.

Test each gesture and check **Last:** in the Ready card. You can change actions on the home screen at any time. To repeat setup without clearing your mappings, choose **Run setup again** in Help.

# Log access (Shizuku & ADB)

ColorOS does not pass the Snap Key to apps. SnapKey Map detects it from the system log, so it needs the `READ_LOGS` permission once.

### Shizuku (no computer)

1. Install and start [Shizuku](https://shizuku.rikka.app/guide/setup/) with wireless debugging (Android 11+).
2. In the wizard's log-access step, tap **Grant with Shizuku** and allow the permission request. The button is enabled while Shizuku is running.

Keep Shizuku running. The app then reads logs through Shizuku and Android's device-logs prompt never appears. Shizuku may need starting again after a phone restart.

If ColorOS blocks the grant, see [ADB or Shizuku refuses to grant log access](#adb-or-shizuku-refuses-to-grant-log-access). You can also go back and choose Computer (ADB).

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

# How to grant the accessibility permission

1. In the wizard, tap **Open accessibility settings**.
2. Find **SnapKey Map** and turn it on.
3. If Android blocks the switch, open **App info** for SnapKey Map, tap the three-dot menu, choose **Allow restricted settings**, and try again.

The service runs accessibility actions such as screenshot and lock, opens apps and the device-logs prompt from the background, receives supported key events, and helps keep the app running. It cannot read screen content.

# Troubleshooting

### ADB or Shizuku refuses to grant log access

An error containing `Neither user 2000` and `GRANT_RUNTIME_PERMISSIONS` means ColorOS is blocking the shell from granting permissions.

1. Open Developer options on the phone.
2. Turn on **Disable permission monitoring**. Newer firmware may call it **Disable system optimization**.
3. If neither setting appears, switch the system language to English (United States) for a moment and reopen Developer options.
4. Read the warning before you accept. This setting relaxes manufacturer restrictions and may increase background power use.
5. Restart the phone. On ColorOS 16 the setting takes effect only after a restart. Then grant again.

If the setting is still missing, include your ColorOS version when you [report the problem](https://github.com/heykay-47/SnapKeyMap/issues/new?template=bug_report.yml).

### The key stops working after a restart

Open SnapKey Map and allow device-log access again. If the app shows **Paused**, tap **Connect**. You do not need to repeat the ADB grant unless you uninstalled the app or revoked the permission.

If you declined the log-access prompt, wait a minute before you try again. Check that the accessibility service is still on and that ColorOS allows background activity.

### A double press runs the single-press action

Set Oppo's own Snap Key action to None. Keep SnapKey Map open, double-press the key, and read the **Last:** text.

- `press` means the app did not recognise the double press. Update to v1.0.3 or later.
- `double press` means detection worked. Check the assigned action and that the selected app is still installed.

When you report a detection problem, include your app version, ColorOS version, assigned actions, and the text under **Last:**. Do not share an unfiltered system log. Device logs can contain sensitive information.

# Privacy and limits

SnapKey Map reads the system log on the phone to detect the Snap Key. The log permission covers more than key events. Lines are checked in memory and discarded, never saved or uploaded. Teaching keeps observations in memory and saves a learned log signature and, when available, an input key code. The app accepts a key log line only from the app that wrote it while you taught the key, which on the Find X9 is the system, so other apps can't fake a press.

With Shizuku allowed, the app runs `logcat` and also reads raw key input events using `getevent` as the shell user through Shizuku. Input events are filtered in memory, never stored or uploaded. A learned input key uses explicit press and release events while the input stream is live; otherwise detection falls back to the system log. Shizuku is not used to run mapped actions. Apps and websites you open through an action have their own privacy policies.

Keep these limits in mind:

- A double-press assignment adds a wait after release before the single-press action runs: 350 ms by default, adjustable from 200 to 600 ms in **Timing**. The hold threshold defaults to 650 ms and can be set from 300 to 1200 ms.
- With a learned input key and a live Shizuku input stream, press and hold uses explicit key edges. Log fallback on the Find X9 infers press and release from known key lines. Other learned log formats need the line to repeat while the key is held.
- Screen-off detection and background reliability depend on ColorOS and are not guaranteed.
- The shortcut picker lists apps that provide `ACTION_CREATE_SHORTCUT`. It cannot list every pinned shortcut. A deep link may work instead.
- Calls are not placed directly, because the app has no calling permission. Call a contact opens the dialer, and direct-dial shortcuts show a message instead.
- Silent mode and Do Not Disturb need notification policy access. Without it, the ringer action cycles between ring and vibrate.
- Mappings and the learned key are not included in Android backups.
- The app cannot bypass the lock screen or control every function in every app.

# Contributing

<a href="https://github.com/heykay-47/SnapKeyMap/graphs/contributors">
  <img alt="Contributors" src="https://contrib.rocks/image?repo=heykay-47/SnapKeyMap" />
</a>

Bug reports, fixes, and device notes from other ColorOS versions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) for the code layout, Find X9 device notes, and how to build, sign, and release the app.

# Support

SnapKey Map is free and has no ads. If it saves you a few taps, you can support its development.

<p align="center">
  <a href="https://github.com/sponsors/heykay-47"><img alt="Sponsor on GitHub" src="https://img.shields.io/badge/Sponsor-EA4AAA?style=for-the-badge&logo=githubsponsors&logoColor=%23fff&labelColor=%23EA4AAA&color=%23EA4AAA"></a>
  <a href="https://buymeatea.in/heykay47"><img alt="Buy Me a Tea" src="https://img.shields.io/badge/Buy%20Me%20a%20Tea-C8811E?style=for-the-badge&logo=buymeacoffee&logoColor=%23fff&labelColor=%23C8811E&color=%23C8811E"></a>
</p>

# Thanks

- [SelenStudio/plus-key-remapper](https://github.com/SelenStudio/plus-key-remapper). Its OnePlus 15 Plus Key approach informed the system-log detection.
- [Shizuku](https://github.com/RikkaApps/Shizuku). Log access without a computer.
- [GSAP](https://gsap.com/). Animations on the website.

SnapKey Map is made by Krithik ([@heykay-47](https://github.com/heykay-47)). Tap the info button on the home screen for the About page.

# Stars <3

<a href="https://star-history.com/#heykay-47/SnapKeyMap&Date">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=heykay-47/SnapKeyMap&type=Date&theme=dark" />
    <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=heykay-47/SnapKeyMap&type=Date" />
    <img alt="Star history chart" src="https://api.star-history.com/svg?repos=heykay-47/SnapKeyMap&type=Date" />
  </picture>
</a>
