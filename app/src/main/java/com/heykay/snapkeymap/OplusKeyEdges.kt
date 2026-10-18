package com.heykay.snapkeymap

/**
 * Find X9's OplusKeyEventUtil logs each down and up twice at the same timestamp.
 * Unlike a repeating-key log, a held key is silent between its down and up.
 * Deduplicate using the source timestamp, not delivery time: logcat can buffer events.
 */
class OplusKeyEdges(private val onDown: () -> Unit, private val onUp: () -> Unit) {
    private var lastTimestamp: String? = null
    private var pressed = false

    fun accept(timestamp: String) {
        if (timestamp == lastTimestamp) return
        lastTimestamp = timestamp
        pressed = !pressed
        if (pressed) onDown() else onUp()
    }

    fun reset() {
        lastTimestamp = null
        pressed = false
    }
}
