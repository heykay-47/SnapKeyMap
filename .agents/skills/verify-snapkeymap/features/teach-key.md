# Teach the Snap Key

The teach wizard learns how the phone reports the Snap Key. That's either a normal key event or a line in the system log, plus the precise input device when Shizuku is running. It asks the user to press the key a few times, then shows `Got it!` or `Couldn't find your Snap Key`.

## Sub-features

- `teach-preflight`: before `Start`, cards warn `First: set Oppo's Snap Key action to None`, `Turn on the SnapKey Map service first`, `No log access yet` or `Connect to the system log`, as needed.
- `teach-run`: `Hands off` for 5 seconds, then three 3-second `Press the Snap Key once` windows, each followed by 1 second of `Let go`, then 3.5 seconds of `Now press and hold it`. `Cancel` returns to the intro without saving a key.
- `teach-success`: `Got it!`, matched-key information, and `Try it now: press the Snap Key.` A later press changes that line to `Detected: <gesture>`. Buttons are `Done` and `Teach it again`.
- `teach-failure`: `Couldn't find your Snap Key`, with `Try again` and `Back`.

## How to get to it (user POV)

- Home → `Teach the Snap Key again`.
- Home status card → `Fix` when it reads `Teach the Snap Key again so it can be detected.`
- Setup → the teach page.

## Driving it with sk

Preconditions:

- Baseline from the README.
- For a full run, the user agrees to re-teach and is there to press the key. A successful run replaces the stored key; cancellation and NothingFound keep it. A maintenance pass can inspect the intro and leave without starting.
- `sk doctor` shows the accessibility service on and READ_LOGS granted, unless you're testing the preflight cards.

- **Open.** Scroll Home's status card offscreen, then run `sk tap "Teach the Snap Key again" --expect "Teach your Snap Key"`. Capture `sk ui teach-intro` and `sk shot teach-intro`. The dump can contain a false service-warning card, which moves Start before a tap lands.
- **Start.** Only during an approved teaching run, ask the user to tap Start if the helper blocks it. Follow the screen's prompts without dumping or driving while they press.
- **Result.** After the user completes the prompts, capture `sk ui teach-result` and `sk shot teach-result`. Record Got it! and the matched-key information, or Couldn't find your Snap Key. Do not report success from the phase timer alone.
- **Finish.** Run `sk tap Done --expect "What the Snap Key does"`. Then ask for one press: the card should read `Last: press · just now`.
- **Cancel path.** Ask the user to tap Start, then run `sk tap Cancel` during Hands off, before any key presses. Confirm the intro returns. Do not report this path tested if you inspected only the intro.

## Gotchas

- Injected key events aren't a valid stand-in. They skip `getevent`, and Oppo's log writes each edge two or three times within microseconds. Only real presses prove learning.
- Leaving the app while learning, for example to check something in Settings, can cost the log-access consent (cached for only 60 s), so the run fails with `Couldn't find your Snap Key`.
- If Oppo's own action is still on, pressing the key also runs it (Mind Space, the camera, and so on), which takes the app out of the foreground. Check `First: set Oppo's Snap Key action to None` first.
- For bug reports, ask the reporter first what the wizard showed: the matched line, or `Couldn't find your Snap Key`.
- Start requires the accessibility service and, when READ_LOGS is granted, FULL log access. Use doctor and a screenshot before diagnosing a disabled Start from XML alone.
