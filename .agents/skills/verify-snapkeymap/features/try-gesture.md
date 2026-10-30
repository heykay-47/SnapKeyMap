# Try a gesture

The play button beside an assigned gesture runs its action without a Snap Key press. Its label is `Try <gesture>`, such as `Try Press`.

## Sub-features

- `try-run`: a tap runs the assigned action immediately, without a confirmation dialog.
- `try-confirmation`: Flashlight, Ringer and Do Not Disturb show their result even with Show confirmations off.
- `try-hidden`: Do nothing rows have no Try button. Unavailable hold gestures have none either.

## How to get to it (user POV)

- Home, beside each available assigned gesture, to the left of its lock icon.

## Driving it with sk

Preconditions:

- Baseline from the README. Read the assigned action before every Try tap.
- Use only a harmless action such as Flashlight. Never Try Lock the screen, Power menu, Call a contact or Open a link.
- Record the initial torch state. If a temporary Flashlight mapping is necessary, record and restore the original action.

1. Scroll the status card offscreen with `sk scroll down --px 700`. Confirm the row and `Try Press` with `sk ui` and `sk shot`.
2. Run `sk state before-try`, then `sk tap "Try Press"` once. After the action settles, capture `sk shot try-result` and `sk state after-try`.
3. Check for a new torch event with SnapKey Map's current PID. The result should also appear in a Flashlight on or off toast.
4. Tap the same Try button once more. Verify the torch returns to its starting state with `sk state after-undo`.
5. Restore any temporary mapping and confirm the row after leaving and reopening Home.

## Gotchas

- Omit `--expect` on Try taps. Its automatic retry could execute the action twice.
- A toast alone does not prove the hardware effect. Compare the camera-service events.
- Try bypasses gesture detection. Only a real key press together with a recent Last entry and the action's effect proves detection.
