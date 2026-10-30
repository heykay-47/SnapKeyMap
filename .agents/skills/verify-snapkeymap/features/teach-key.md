# Teach the Snap Key

The teach wizard learns how the phone reports the Snap Key. That's either a normal key event or a line in the system log, plus the precise input device when Shizuku is running. It asks the user to press the key a few times, then shows `Got it!` or `Couldn't find your Snap Key`.

## Sub-features

- `teach-preflight`: before `Start`, cards warn `First: set Oppo's Snap Key action to None`, `Turn on the SnapKey Map service first`, `No log access yet` or `Connect to the system log`, as needed.
- `teach-run`: `Start`, then on-screen instructions while the user presses, with a `Cancel` button.
- `teach-success`: `Got it!`, with what was matched (for example `Matched input key: …`), then `Done` or `Teach it again`.
- `teach-failure`: `Couldn't find your Snap Key`, with `Try again` and `Back`.

## How to get to it (user POV)

- Home → `Teach the Snap Key again`.
- Home status card → `Fix` when it reads `Teach the Snap Key again so it can be detected.`
- Setup → the teach page.

## Driving it with sk

Preconditions:

- Baseline from the README.
- The user is there to press the key, and agrees to re-teach. A successful run replaces the stored key. A cancelled run keeps the old one.
- `sk doctor` shows the accessibility service on and READ_LOGS granted, unless you're testing the preflight cards.

- **Open.** Run `sk tap "Teach the Snap Key again" --expect "Teach your Snap Key"`, then `sk ui teach-intro`. Note which preflight cards show. Each one should match a `sk doctor` line.
- **Start.** Run `sk logcat start`, then `sk tap Start`, then `sk ui teach-running`. Ask the user to press the Snap Key as the screen says, and don't drive the UI while they press.
- **Result.** Run `sk wait "Got it!" --timeout 60`, then `sk ui teach-result` and `sk shot teach-result`. Then `sk logcat stop`. The log should contain the `KEYLOG_PhoneWindowManagerExtImpl … keyCode=KEYCODE_ACTION_BUTTON_CLICK` line (scanCode 735) followed by the `OplusKeyEventUtil` line.
- **Finish.** Run `sk tap Done --expect "What the Snap Key does"`. Then ask for one press: the card should read `Last: press · just now`.
- **Cancel path.** Run `sk tap Start`, then `sk tap Cancel` before pressing anything. You're back on the intro, and Home still detects the key with the old configuration.

## Gotchas

- Injected key events aren't a valid stand-in. They skip `getevent`, and Oppo's log writes each edge two or three times within microseconds. Only real presses prove learning.
- Leaving the app while learning, for example to check something in Settings, can cost the log-access consent (cached for only 60 s), so the run fails with `Couldn't find your Snap Key`.
- If Oppo's own action is still on, pressing the key also runs it (Mind Space, the camera, and so on), which takes the app out of the foreground. Check `First: set Oppo's Snap Key action to None` first.
- For bug reports, ask the reporter first what the wizard showed: the matched line, or `Couldn't find your Snap Key`.
