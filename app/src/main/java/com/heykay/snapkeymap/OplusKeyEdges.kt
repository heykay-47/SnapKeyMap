package com.heykay.snapkeymap

import kotlin.math.abs

/**
 * Find X9's OplusKeyEventUtil logs each down and up two or three times, a fraction of a millisecond apart.
 * The copies can land on either side of a millisecond, so they are matched by time, not by
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

    fun reset(pressed: Boolean = false) {
        lastEdgeMs = null
        this.pressed = pressed
    }

    internal fun resetFrom(other: OplusKeyEdges) {
        lastEdgeMs = other.lastEdgeMs
        pressed = other.pressed
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

internal class TeachingLogEdges {
    private val byCode = mutableMapOf<String?, OplusKeyEdges>()

    fun begin(code: String?, source: OplusKeyEdges) {
        reset()
        byCode[null] = OplusKeyEdges({}, {}).apply { resetFrom(source) }
        if (code != null) byCode[code] = OplusKeyEdges({}, {}).apply { resetFrom(source) }
    }

    fun observe(timestamp: String, sample: KeySample.Log) {
        byCode.getOrPut(null) { OplusKeyEdges({}, {}) }
        sample.threadKey?.let { byCode.getOrPut(it) { OplusKeyEdges({}, {}) } }
        byCode.forEach { (code, edges) ->
            if (KeyMatching.matches(KeyBinding.Logged(null, code, null), sample)) edges.observe(timestamp)
        }
    }

    fun reset() {
        byCode.clear()
    }

    fun restore(code: String?, target: OplusKeyEdges) {
        val source = byCode[code] ?: byCode[null]
        if (source == null) target.reset() else target.resetFrom(source)
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
