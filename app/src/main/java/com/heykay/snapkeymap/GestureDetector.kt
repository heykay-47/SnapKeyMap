package com.heykay.snapkeymap

/**
 * Turns key down/up into press, double press, press & hold or tap then hold. Runs on the main thread.
 *
 * It only waits when it has to: with no double press or either hold assigned, a press fires on key
 * down; a double press fires on the second key down unless tap then hold is assigned.
 */
class GestureDetector(
    private val schedule: (Runnable, Long) -> Unit,
    private val cancel: (Runnable) -> Unit,
    private val holdMs: () -> Long,
    private val onGesture: (Gesture) -> Unit,
    private val doublePressWindowMs: () -> Long = { 350L },
) {
    var wantsDouble = false
    var wantsLong = false
    var wantsTapHold = false

    private enum class Phase { FIRST, WAITING_FOR_SECOND, SECOND }
    private var pressed = false
    private var handled = false
    private var phase = Phase.FIRST
    val hasHandledPress: Boolean get() = pressed && handled

    private val longPress = Runnable {
        handled = true
        onGesture(Gesture.LONG)
    }
    private val singlePress = Runnable {
        phase = Phase.FIRST
        onGesture(Gesture.SINGLE)
    }
    private val tapHold = Runnable {
        handled = true
        onGesture(Gesture.TAP_HOLD)
    }

    fun down() {
        if (pressed) return
        pressed = true
        handled = false
        when {
            phase == Phase.WAITING_FOR_SECOND -> {
                cancel(singlePress)
                phase = Phase.SECOND
                if (wantsTapHold) schedule(tapHold, holdMs()) else {
                    handled = true
                    onGesture(Gesture.DOUBLE)
                }
            }
            !wantsDouble && !wantsLong && !wantsTapHold -> {
                handled = true
                onGesture(Gesture.SINGLE)
            }
            wantsLong -> schedule(longPress, holdMs())
        }
    }

    fun up() {
        if (!pressed) return
        pressed = false
        cancel(longPress)
        cancel(tapHold)
        if (handled) {
            phase = Phase.FIRST
            return
        }
        if (phase == Phase.SECOND) {
            phase = Phase.FIRST
            onGesture(Gesture.DOUBLE)
        } else if (wantsDouble || wantsTapHold) {
            phase = Phase.WAITING_FOR_SECOND
            schedule(singlePress, doublePressWindowMs())
        } else {
            onGesture(Gesture.SINGLE)
        }
    }

    fun reset() {
        cancel(longPress)
        cancel(singlePress)
        cancel(tapHold)
        pressed = false
        handled = false
        phase = Phase.FIRST
    }
}
