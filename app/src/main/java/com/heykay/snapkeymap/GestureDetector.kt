package com.heykay.snapkeymap

/**
 * Turns key down/up into press, double press or press & hold. Runs on the main thread.
 *
 * It only waits when it has to: with nothing set for double press or hold, a press fires on key
 * down; a double press fires on the second key down.
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

    private var pressed = false
    private var handled = false
    private var waitingForSecond = false
    val hasHandledPress: Boolean get() = pressed && handled

    private val longPress = Runnable {
        handled = true
        onGesture(Gesture.LONG)
    }
    private val singlePress = Runnable {
        waitingForSecond = false
        onGesture(Gesture.SINGLE)
    }

    fun down() {
        if (pressed) return
        pressed = true
        handled = false
        when {
            waitingForSecond -> {
                cancel(singlePress)
                waitingForSecond = false
                handled = true
                onGesture(Gesture.DOUBLE)
            }
            !wantsDouble && !wantsLong -> {
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
        if (handled) return
        if (wantsDouble) {
            waitingForSecond = true
            schedule(singlePress, doublePressWindowMs())
        } else {
            onGesture(Gesture.SINGLE)
        }
    }

    fun reset() {
        cancel(longPress)
        cancel(singlePress)
        pressed = false
        handled = false
        waitingForSecond = false
    }
}
