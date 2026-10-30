package com.heykay.snapkeymap

internal class InputKeyDelivery(private val teaching: TeachingDelivery) {
    private val held = mutableSetOf<InputKey>()
    private var suppressed: InputKey? = null

    fun deliver(event: InputKeyEvent, stamp: TeachingDelivery.Stamp, action: (Boolean) -> Unit) {
        val ignore = event.key == suppressed
        if (event.down) held += event.key else {
            held -= event.key
            if (ignore) suppressed = null
        }
        teaching.deliver(stamp) { action(ignore) }
    }

    fun suppressUntilUp(key: InputKey?) {
        suppressed = key
    }

    fun suppressHeld(key: InputKey?): Boolean {
        suppressed = key?.takeIf { it in held }
        return suppressed != null
    }

    fun readerLost() {
        held.clear()
        suppressed = null
    }
}
