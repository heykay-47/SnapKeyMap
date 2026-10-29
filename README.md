<p align="center">
  <a href="#support"><img width="720" alt="Find SnapKey Map useful? Please support its development." src="docs/snapkeymap-svg/support-banner.svg" /></a>
</p>

<p align="center">
  <img width="128" alt="SnapKey Map icon" src="docs/snapkeymap-svg/snapkeymap-icon.svg" />
</p>

<h1 align="center">SnapKey Map</h1>

<p align="center">
  Map the Oppo Find X9's Snap Key to any app or action.<br>
  A different action for a press, a double press, a press and hold, and a tap then hold. No root needed.
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
- [Requirements](#requirements)
- [Get started](#get-started)
- [Documentation](#documentation)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)
- [Support](#support)

# Screenshots

<p align="center">
  <img width="32%" alt="Home screen with the Ready card and four gestures, including Circle to Search on Double press" src="docs/screenshots/snapkeymap_ss1.jpg" />
  <img width="32%" alt="Lower home screen with Timing, Show confirmations, Pause in apps, and tips" src="docs/screenshots/snapkeymap_ss2.jpg" />
  <img width="32%" alt="Action picker with Phone and Screen and navigation actions, including Circle to Search" src="docs/screenshots/snapkeymap_ss3.jpg" />
</p>
<p align="center">
  <img width="32%" alt="Action picker with Open actions and More actions such as Call a contact, Open a link, and App shortcut" src="docs/screenshots/snapkeymap_ss4.jpg" />
  <img width="32%" alt="Timing dialog with the double-press window and press-and-hold sliders" src="docs/screenshots/snapkeymap_ss5.jpg" />
  <img width="32%" alt="Help screen with Run setup again and Getting started" src="docs/screenshots/snapkeymap_ss6.jpg" />
</p>

# Features

Assign one action each to **Press**, **Double press**, **Press & hold**, and **Tap, then hold**. For the last gesture, tap once, then hold the second press. When it is assigned, a quick double press runs on release instead of on the second press down.

Tap the lock next to a gesture to run it only when your phone is unlocked.

| Category | Actions |
| --- | --- |
| Phone | Flashlight, ringer presets you can create and edit (ring, vibrate, silent), a distinct vibration for each ringer mode, Do Not Disturb |
| Screen & navigation | Screenshot, Circle to Search, lock screen, notifications, quick settings, recent apps, home, back, power menu |
| Music & video | Play / pause, next and previous track, media volume up and down, mute / unmute media, volume slider |
| Open | Camera, video camera, voice assistant, Wi-Fi panel, Bluetooth settings, NFC panel, internet and mobile data panel |
| More | Call a contact (opens the dialer with their number), open a link (including `sms:` and `wa.me` links with a message filled in), run a supported app shortcut |
| Open an app | Any installed app |

On the home screen you can pause the key in chosen apps, such as the camera, and hide confirmation pop-ups. Errors still show.

Tap ▶ next to a gesture to try its action without pressing the key. Turn the mapping on or off from the **Snap Key** Quick Settings tile. Back up your settings, including the learned key, to a file and restore them after a reinstall. **Details** on the status card can copy a bug report or open a prefilled bug form.

The app follows Material 3 with dynamic colour and a System, Light, or Dark theme. It declares no internet permission.

# Requirements

- An Oppo Find X9. Other devices and ColorOS versions may behave differently. Users have also reported it working on the Find X9 Pro, X9 Ultra, X9s, X9s Pro, OnePlus 15R, and OnePlus 13s.
- Android 10 or later.
- One of these, for the one-time log permission:
  - [Shizuku](https://shizuku.rikka.app/guide/setup/), running through wireless debugging. No computer needed.
  - A USB cable and a computer with Google's [SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools).

# Get started

1. Download `SnapKeyMap-*.apk` from the [latest release](https://github.com/heykay-47/SnapKeyMap/releases/latest) on your phone and install it.
2. Open SnapKey Map and follow the setup wizard. It gives the app log access through Shizuku or ADB, turns on the accessibility service, and teaches it your Snap Key.
3. Assign actions to the gestures and test each one. **Last:** in the Ready card shows what the app detected.

To update, install the new APK over the existing app. Do not uninstall first, or you lose your mappings and the log permission.

The [setup guide](docs/setup.md) explains each step, including the ADB command.

# Documentation

- [Setup guide](docs/setup.md). Installing and updating, the setup wizard, log access with Shizuku or ADB, and the accessibility permission.
- [Troubleshooting](docs/troubleshooting.md). Refused grants, the key stopping after a restart, and missed double presses.
- [Privacy and limits](docs/privacy.md). What the app reads, what it keeps, and what it cannot do.
- [Roadmap](ROADMAP.md). What comes next and what each release delivered.
- [Help and FAQ](https://heykay-47.github.io/SnapKeyMap/help.html) on the website.

# Roadmap

Next is the F-Droid release. The [roadmap](ROADMAP.md) lists every step and what each release delivered.

Have an idea? [Request a feature](https://github.com/heykay-47/SnapKeyMap/issues/new?template=feature_request.yml).

# Contributing

<a href="https://github.com/heykay-47/SnapKeyMap/graphs/contributors">
  <img alt="Contributors" src="https://contrib.rocks/image?repo=heykay-47/SnapKeyMap" />
</a>

Bug reports, fixes, and device notes from other ColorOS versions are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) for the code layout, Find X9 device notes, and how to build, sign, and release the app.

# License

SnapKey Map is free software under the [GNU General Public License v3.0 or later](LICENSE).

The third-party GSAP files in [`docs/vendor/gsap`](docs/vendor/gsap/README.md) retain their own license. They are used only by the website and are not part of the Android app.

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
