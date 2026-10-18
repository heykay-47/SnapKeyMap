package com.heykay.snapkeymap

import android.os.Handler

/**
 * Turns key down/up into press, double press or press & hold. Runs on the main thread.
 *
 * It only waits when it has to: with nothing set for double press or hold, a press fires on key
 * down; a double press fires on the second key down.
 */
class GestureDetector(
    private val handler: Handler,
    private val longPressMs: Long,
    private val onGesture: (Gesture) -> Unit,
) {
    var wantsDouble = false
    var wantsLong = false

    private var pressed = false
    private var handled = false
    private var waitingForSecond = false

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
                handler.removeCallbacks(singlePress)
                waitingForSecond = false
                handled = true
                onGesture(Gesture.DOUBLE)
            }
            !wantsDouble && !wantsLong -> {
                handled = true
                onGesture(Gesture.SINGLE)
            }
            wantsLong -> handler.postDelayed(longPress, longPressMs)
        }
    }

    fun up() {
        if (!pressed) return
        pressed = false
        handler.removeCallbacks(longPress)
        if (handled) return
        if (wantsDouble) {
            waitingForSecond = true
            handler.postDelayed(singlePress, DOUBLE_PRESS_WINDOW_MS)
        } else {
            onGesture(Gesture.SINGLE)
        }
    }

    fun reset() {
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(singlePress)
        pressed = false
        handled = false
        waitingForSecond = false
    }

    private companion object {
        const val DOUBLE_PRESS_WINDOW_MS = 350L
    }
}
