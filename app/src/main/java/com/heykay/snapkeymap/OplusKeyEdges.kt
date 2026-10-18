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
        if (!advance(timestamp)) return
        if (pressed) onDown() else onUp()
    }

    fun observe(timestamp: String) {
        advance(timestamp)
    }

    private fun advance(timestamp: String): Boolean {
        if (timestamp == lastTimestamp) return false
        lastTimestamp = timestamp
        pressed = !pressed
        return true
    }

    fun reset() {
        lastTimestamp = null
        pressed = false
    }
}
