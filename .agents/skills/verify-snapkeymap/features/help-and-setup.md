# Help and setup

Help explains how to get started, how to troubleshoot, and whether the app is safe. It links to the full help on the website and can restart the setup flow, which walks through the grant method, log access, the accessibility service, battery, notifications, teaching the key, and choosing actions.

## Sub-features

- `help-read`: the `Help` screen with `Run setup again`, `Getting started`, `Troubleshooting`, `Is it safe?` and `Full help on the website`.
- `setup-rerun`: `Run setup` resets the setup progress and the grant method, then opens the setup flow at the method choice. Actions and the taught key are kept.
- `intro`: the first-run intro. It shows only when no data has been stored yet.

## How to get to it (user POV)

- Home → the `Help` (?) icon in the toolbar.
- Help → `Run setup`.
- A fresh install opens the intro, then setup.

## Driving it with sk

Preconditions:

- Baseline from the README.
- For `setup-rerun`: the user has agreed. The run clears `setupComplete` and the stored grant method, so Home stays hidden until setup is finished again.

- **Read Help.** Run `sk tap Help --expect "Run setup again"`, then `sk ui help`. Scroll with `sk scroll down` and check that `Getting started`, `Troubleshooting`, `Is it safe?` and `Full help on the website` are there. Leave with `sk back`.
- **Website link.** Run `sk tap "Full help on the website"`, then `sk ui --all web`. The browser opens the project's help page. Come back with `sk back`.
- **Re-run setup (consent needed).** Run `sk tap "Run setup"`, then `sk ui setup-method`. The setup flow opens at the method choice (Shizuku or ADB). Finish every page with the user's existing choices so that Home returns. The run isn't complete until `sk launch` prints `ready: SnapKey Map` again.

## Gotchas

- The intro can't be reached on the user's phone without `pm clear`, which is forbidden. Report `intro` as unverified unless a spare device or a fresh install has been agreed.
- Leaving the setup flow halfway leaves the app on setup at the next launch. That's correct behaviour, not a crash, but the user's Home is gone until setup is finished.
- ColorOS 16+ with permission monitoring blocks `pm grant` from adb. The ADB grant page can't be completed from the shell; use the Shizuku route or the user's help.
