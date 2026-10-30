# SnapKey Map verification map

This folder is the maintained source for verifying what SnapKey Map does for a user. Read this index before driving the phone, then use the matching feature file as the recipe. `sk` means `.claude/skills/verify-snapkeymap/scripts/sk`, run from the repo root.

## Baseline preconditions

- The user's Find X9 (CPH2797) is attached over USB, awake and unlocked. `sk doctor` prints `OK to drive`.
- The build under test is installed. `sk doctor --expect-version <versionName>` passes.
- Setup is complete, so `sk launch` prints `ready: SnapKey Map` (Home). If it opens the setup flow instead, stop and ask the user. Don't click through setup on their behalf.
- A run is open: `sk run <feature-id>`.
- There is no seed data. The baseline is the user's own configuration, so record every value before you change it and put it back afterwards.

## Driving conventions

- Start every journey from Home. `sk back` returns there from any inner screen.
- Use exact visible labels and content descriptions with `sk tap`. Never tap raw coordinates for app elements: the layout moves with the status card's state.
- Pass `sk tap --expect "<label>"` (or follow the tap with `sk wait`) for every tap that should change the screen.
- Compose lists show only what's on screen. `sk scroll down` before you look for lower items.
- Snap Key presses come from the user. Ask for exactly one gesture at a time, and say which one.

## Proof and skip reporting

- Capture the screen before and after each user action (`sk ui <name>`, plus `sk shot <name>` for anything visual).
- Prove settings by leaving and re-entering the screen, then showing the value again.
- Prove an action's effect with `sk state` before and after (torch, ringer, DND, vibrations) as well as on screen.
- Name the feature ID and entry point in the run label.
- If a journey can't run (it needs a data wipe, a key press with nobody there, or Oppo's own setting changed), report the unmet precondition and the exact command that stopped. Don't report it verified through another path.

## Feature entry contract

Each feature file starts with an H1 title and one paragraph describing what the user sees. It then has four H2 sections, in this order: `Sub-features`, `How to get to it (user POV)`, `Driving it with sk` (starting with `Preconditions:`), and `Gotchas`.

## Features

- [About and licences](./about-licences.md): version, links, and the offline licence texts. Read-only, so a safe first run. Proven end to end on 2026-10-28.
- [Home status](./home-status.md): the status card, the master switch, Details, Fix and Connect.
- [Gesture actions](./gesture-actions.md): choose what each gesture does, and prove the action runs when the key is pressed.
- [Timing and settings](./timing-and-settings.md): the timing sliders, Show confirmations, Pause in apps, the per-gesture lock, and the theme.
- [Teach the Snap Key](./teach-key.md): the teach wizard that learns how the key shows up.
- [Help and setup](./help-and-setup.md): the Help screen and "Run setup again". Setup changes the user's configuration, so it needs their consent.
