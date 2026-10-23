package com.heykay.snapkeymap

/** How long after the last Snap Key log line we treat the key as released, before calibration. */
const val DEFAULT_RELEASE_GAP_MS = 270L

private val NUMBERS = Regex("""\d+""")
private val LOGGED_KEY_CODE = Regex("""\bkeyCode=([^,\s}]+)""")

/** Strips the parts of a log message that change between presses (timestamps, counters, ids). */
fun normalizeLogMessage(message: String): String = message.trim().replace(NUMBERS, "#").take(160)

internal fun normalizeLogUid(uid: String): String = if (uid == "system") "1000" else uid

fun loggedKeyCode(uid: String, tag: String, message: String): String? {
    if (normalizeLogUid(uid) != "1000" || !tag.contains("PhoneWindowManagerExtImpl") ||
        !message.contains("overrideInterceptKeyBeforeQueueing:KeyEvent")) return null
    return LOGGED_KEY_CODE.find(message)?.groupValues?.get(1)
}

internal fun acceptsLoggedKey(logKeyCode: String?, lineKey: String?): Boolean =
    logKeyCode == null || lineKey == null || lineKey == logKeyCode

private fun hasOplusKeyMessage(tag: String, message: String): Boolean =
    tag.contains("OplusKeyEventUtil") && message.contains("restrict listen mode")

/** The log line ColorOS / OxygenOS writes when its side key is pressed (seen on the OnePlus 15). */
fun isKnownOplusKeyLine(uid: String, tag: String, message: String): Boolean =
    normalizeLogUid(uid) == "1000" && hasOplusKeyMessage(tag, message)

/** A system log line that shows up every time the Snap Key is pressed. */
data class KeySignature(val tag: String, val message: String, val uid: String? = null) {
    val hasExplicitKeyEdges: Boolean get() = isKnownOplusKeyLine(uid ?: "1000", tag, message)
    val isUsable: Boolean get() = uid != null || hasExplicitKeyEdges

    fun matches(lineUid: String, lineTag: String, lineMessage: String): Boolean =
        isUsable && normalizeLogUid(lineUid) == normalizeLogUid(uid ?: "1000") &&
            (!hasOplusKeyMessage(tag, message) || isKnownOplusKeyLine(lineUid, lineTag, lineMessage)) &&
            lineTag == tag && normalizeLogMessage(lineMessage) == message
}

internal fun acceptsKeyLog(signature: KeySignature?, uid: String, tag: String, message: String): Boolean =
    signature?.matches(uid, tag, message) ?: isKnownOplusKeyLine(uid, tag, message)

/** A real key event that Android passes to accessibility services. */
data class LearnedKey(val keyCode: Int, val scanCode: Int)

/** Something seen while learning: a normalized log line or a key event, and when it arrived. */
data class Observation(val timeMs: Long, val key: String)

sealed interface LearnResult {
    data class KeyEventFound(val key: LearnedKey, val inputKey: InputKey? = null) : LearnResult
    data class LogLineFound(
        val signature: KeySignature,
        /** True if the log line keeps repeating while the key is held, so press & hold can be detected. */
        val holdRepeats: Boolean,
        val releaseGapMs: Long,
        val inputKey: InputKey? = null,
        val logKeyCode: String? = null,
    ) : LearnResult {
        val canDetectHold: Boolean get() = inputKey != null || signature.hasExplicitKeyEdges || holdRepeats
    }
    data object NothingFound : LearnResult
}

object KeyLearning {
    private const val SEP = '\u0001'

    fun logKey(uid: String, tag: String, message: String) =
        "log$SEP$tag$SEP${normalizeLogMessage(message)}$SEP${normalizeLogUid(uid)}"
    fun keyEventKey(keyCode: Int, scanCode: Int) = "key$SEP$keyCode$SEP$scanCode"
    fun inputKey(code: String) = "input$SEP$code"
    fun loggedKey(code: String) = "logkey$SEP$code"

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
            .filter { key ->
                if (!key.startsWith("log$SEP")) true else {
                    val (_, tag, message, uid) = key.split(SEP)
                    !hasOplusKeyMessage(tag, message) || isKnownOplusKeyLine(uid, tag, message)
                }
            }
            .associateWith { key -> tapSets.count { key in it } }
            .filterValues { it >= minRounds }
        if (candidates.isEmpty()) return LearnResult.NothingFound

        val holdKeys = keysIn(hold)
        val inputKey = candidates.entries
            .filter { it.key.startsWith("input$SEP") && it.key in holdKeys }
            .maxByOrNull { it.value }?.key?.substringAfter(SEP)?.let(::InputKey)

        candidates.entries
            .filter { it.key.startsWith("key$SEP") }
            .maxByOrNull { it.value }
            ?.let { entry ->
                val (_, code, scan) = entry.key.split(SEP)
                return LearnResult.KeyEventFound(LearnedKey(code.toInt(), scan.toInt()), inputKey)
            }

        val logs = candidates.entries.filter { it.key.startsWith("log$SEP") }
        val explicitEdges = logs.filter { (key, _) ->
            val (_, tag, message, uid) = key.split(SEP)
            isKnownOplusKeyLine(uid, tag, message)
        }
        val logCandidates = explicitEdges.ifEmpty { logs }
        val best = logCandidates.maxByOrNull { (key, rounds) ->
            val linesPerTap = observations.count { o -> o.key == key && taps.any { o.timeMs in it } } / taps.size
            var score = rounds * 100
            if (key.contains("key", ignoreCase = true)) score += 50
            if (key in holdKeys) score += 10
            score - minOf(linesPerTap, 30) // chatty lines are usually animations, not the key itself
        }?.key ?: return LearnResult.NothingFound

        val (_, tag, message, uid) = best.split(SEP)
        val holdTimes = observations.filter { it.key == best && it.timeMs in hold }.map { it.timeMs }.sorted()
        val holdRepeats = holdTimes.size >= 3 && holdTimes.last() - holdTimes.first() >= 700
        val releaseGap = if (holdRepeats) {
            val gaps = holdTimes.zipWithNext { a, b -> b - a }.sorted()
            (gaps[gaps.size / 2] * 3 / 2).coerceIn(150L, 450L)
        } else {
            DEFAULT_RELEASE_GAP_MS
        }
        val signature = KeySignature(tag, message, uid)
        val logKeyCode = if (signature.hasExplicitKeyEdges) {
            candidates.entries.filter { it.key.startsWith("logkey$SEP") }
                .maxByOrNull { it.value }?.key?.substringAfter(SEP)
        } else null
        return LearnResult.LogLineFound(signature, holdRepeats, releaseGap, inputKey, logKeyCode)
    }
}
