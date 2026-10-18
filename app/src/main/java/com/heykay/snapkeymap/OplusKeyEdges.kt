package com.heykay.snapkeymap

import kotlin.math.abs

/**
 * Find X9's OplusKeyEventUtil logs each down and up twice, a fraction of a millisecond apart.
 * The two copies can land on either side of a millisecond, so they are matched by time, not by
 * identical timestamps. Real edges are at least ~60 ms apart.
 * Unlike a repeating-key log, a held key is silent between its down and up.
 * Deduplicate using the source timestamp, not delivery time: logcat can buffer events.
 */
class OplusKeyEdges(private val onDown: () -> Unit, private val onUp: () -> Unit) {
    private var lastEdgeMs: Long? = null
    private var pressed = false

    fun accept(timestamp: String) {
        if (!advance(timestamp)) return
        if (pressed) onDown() else onUp()
    }

    fun observe(timestamp: String) {
        advance(timestamp)
    }

    private fun advance(timestamp: String): Boolean {
        val time = logTimeOfDayMs(timestamp) ?: return false
        val last = lastEdgeMs
        if (last != null && isSameEdge(time, last)) return false
        lastEdgeMs = time
        pressed = !pressed
        return true
    }

    fun reset() {
        lastEdgeMs = null
        pressed = false
    }

    private companion object {
        const val DUPLICATE_WINDOW_MS = 3L
        const val DAY_MS = 24 * 60 * 60 * 1000L

        fun isSameEdge(a: Long, b: Long): Boolean {
            val apart = abs(a - b)
            return minOf(apart, DAY_MS - apart) <= DUPLICATE_WINDOW_MS
        }
    }
}

private val LOG_TIME = Regex("""^\d\d-\d\d\s+(\d\d):(\d\d):(\d\d)(?:\.(\d+))?$""")

/** Reads the time of day from a logcat "MM-DD HH:MM:SS.mmm" timestamp, in milliseconds. */
internal fun logTimeOfDayMs(timestamp: String): Long? {
    val match = LOG_TIME.matchEntire(timestamp.trim()) ?: return null
    val (hour, minute, second, fraction) = match.destructured
    val seconds = (hour.toLong() * 60 + minute.toLong()) * 60 + second.toLong()
    return seconds * 1000 + (fraction + "000").take(3).toLong()
}
