package com.heykay.snapkeymap

/** How long after the last Snap Key log line we treat the key as released, before calibration. */
const val DEFAULT_RELEASE_GAP_MS = 270L

private val NUMBERS = Regex("""\d+""")

/** Strips the parts of a log message that change between presses (timestamps, counters, ids). */
fun normalizeLogMessage(message: String): String = message.trim().replace(NUMBERS, "#").take(160)

/** The log line ColorOS / OxygenOS writes when its side key is pressed (seen on the OnePlus 15). */
fun isKnownOplusKeyLine(tag: String, message: String): Boolean =
    tag.contains("OplusKeyEventUtil") && message.contains("restrict listen mode")

/** A system log line that shows up every time the Snap Key is pressed. */
data class KeySignature(val tag: String, val message: String) {
    fun matches(lineTag: String, lineMessage: String): Boolean =
        lineTag == tag && normalizeLogMessage(lineMessage) == message
}

/** A real key event that Android passes to accessibility services. */
data class LearnedKey(val keyCode: Int, val scanCode: Int)

/** Something seen while learning: a normalized log line or a key event, and when it arrived. */
data class Observation(val timeMs: Long, val key: String)

sealed interface LearnResult {
    data class KeyEventFound(val key: LearnedKey) : LearnResult
    data class LogLineFound(
        val signature: KeySignature,
        /** True if the log line keeps repeating while the key is held, so press & hold can be detected. */
        val holdRepeats: Boolean,
        val releaseGapMs: Long,
    ) : LearnResult
    data object NothingFound : LearnResult
}

object KeyLearning {
    private const val SEP = '\u0001'

    fun logKey(tag: String, message: String) = "log$SEP$tag$SEP${normalizeLogMessage(message)}"
    fun keyEventKey(keyCode: Int, scanCode: Int) = "key$SEP$keyCode$SEP$scanCode"

    /**
     * Finds what appears in (almost) every press window but never while the phone was left alone.
     * Key events win over log lines, because they give exact press and release times.
     */
    fun analyze(
        observations: List<Observation>,
        baseline: LongRange,
        taps: List<LongRange>,
        hold: LongRange,
    ): LearnResult {
        fun keysIn(range: LongRange) = observations.filter { it.timeMs in range }.map { it.key }.toSet()

        val baselineKeys = keysIn(baseline)
        val tapSets = taps.map(::keysIn)
        val minRounds = if (taps.size >= 3) taps.size - 1 else taps.size
        val candidates = tapSets.flatten().toSet()
            .filter { it !in baselineKeys }
            .associateWith { key -> tapSets.count { key in it } }
            .filterValues { it >= minRounds }
        if (candidates.isEmpty()) return LearnResult.NothingFound

        candidates.entries
            .filter { it.key.startsWith("key$SEP") }
            .maxByOrNull { it.value }
            ?.let { entry ->
                val (_, code, scan) = entry.key.split(SEP)
                return LearnResult.KeyEventFound(LearnedKey(code.toInt(), scan.toInt()))
            }

        val holdKeys = keysIn(hold)
        val best = candidates.entries.maxByOrNull { (key, rounds) ->
            val linesPerTap = observations.count { o -> o.key == key && taps.any { o.timeMs in it } } / taps.size
            var score = rounds * 100
            if (key.contains("key", ignoreCase = true)) score += 50
            if (key in holdKeys) score += 10
            score - minOf(linesPerTap, 30) // chatty lines are usually animations, not the key itself
        }!!.key

        val (_, tag, message) = best.split(SEP)
        val holdTimes = observations.filter { it.key == best && it.timeMs in hold }.map { it.timeMs }.sorted()
        val holdRepeats = holdTimes.size >= 3 && holdTimes.last() - holdTimes.first() >= 700
        val releaseGap = if (holdRepeats) {
            val gaps = holdTimes.zipWithNext { a, b -> b - a }.sorted()
            (gaps[gaps.size / 2] * 3 / 2).coerceIn(150L, 450L)
        } else {
            DEFAULT_RELEASE_GAP_MS
        }
        return LearnResult.LogLineFound(KeySignature(tag, message), holdRepeats, releaseGap)
    }
}
