package com.heykay.snapkeymap

internal data class TeachingAccess(val accessibility: Boolean, val fullLogs: Boolean, val inputLive: Boolean) {
    constructor(accessibility: Boolean, logs: LogAccess, inputLive: Boolean) :
        this(accessibility, logs == LogAccess.FULL, inputLive)
}

internal enum class TeachingFailure { NOT_FOUND, COLLISION, NO_DIFFERENT_KEY, ACCESS_LOST }

internal sealed interface TeachingState {
    data object Capturing : TeachingState
    data class Candidate(val result: LearnResult) : TeachingState
    data class Checking(val result: LearnResult, val startedAtMs: Long) : TeachingState
    data class Ready(val result: LearnResult) : TeachingState
    data class SaveFailed(val result: LearnResult) : TeachingState
    data class Failed(val reason: TeachingFailure) : TeachingState
    data object Closed : TeachingState
}

internal class TeachingSession(private var access: TeachingAccess) {
    var state: TeachingState = TeachingState.Capturing
        private set
    private val observations = mutableListOf<Observation>()
    private sealed interface WitnessKey {
        data class Accessibility(val key: LearnedKey) : WitnessKey
        data class Input(val key: InputKey) : WitnessKey
        data class LoggedVolume(val threadId: String, val code: String) : WitnessKey
    }
    private val pressed = mutableSetOf<WitnessKey>()
    private var completedPress = false

    fun observe(sample: KeySample, atMs: Long) {
        when (val current = state) {
            TeachingState.Capturing -> captureSample(sample, atMs)
            is TeachingState.Checking -> {
                if (atMs < current.startedAtMs) return
                if (KeyMatching.matches(current.result.binding(), sample)) {
                    fail(TeachingFailure.COLLISION)
                    return
                }
                if (atMs <= current.startedAtMs + CHECK_MS + SETTLE_MS) {
                    witness(current.result, sample, atMs <= current.startedAtMs + CHECK_MS)
                }
            }
            else -> {
                val result = validatedResult() ?: return
                if (KeyMatching.matches(result.binding(), sample)) fail(TeachingFailure.COLLISION)
            }
        }
    }

    fun capture(baseline: LongRange, taps: List<LongRange>, hold: LongRange) {
        if (state != TeachingState.Capturing) return
        val result = KeyLearning.analyze(observations, baseline, taps, hold)
        observations.clear()
        state = if (result == LearnResult.NothingFound) TeachingState.Failed(TeachingFailure.NOT_FOUND)
        else TeachingState.Candidate(result)
    }

    fun beginCheck(atMs: Long) {
        val candidate = state as? TeachingState.Candidate ?: return
        if (!requiredAccess(candidate.result)) {
            fail(TeachingFailure.ACCESS_LOST)
            return
        }
        pressed.clear()
        completedPress = false
        state = TeachingState.Checking(candidate.result, atMs)
    }

    fun finishCheck(atMs: Long) {
        val current = state as? TeachingState.Checking ?: return
        if (atMs < current.startedAtMs + CHECK_MS + SETTLE_MS) return
        state = when {
            !requiredAccess(current.result) -> TeachingState.Failed(TeachingFailure.ACCESS_LOST)
            !completedPress || pressed.isNotEmpty() -> TeachingState.Failed(TeachingFailure.NO_DIFFERENT_KEY)
            else -> TeachingState.Ready(current.result)
        }
    }

    fun accessChanged(next: TeachingAccess) {
        access = next
        val result = when (val current = state) {
            is TeachingState.Checking -> current.result
            else -> validatedResult() ?: return
        }
        if (!requiredAccess(result)) fail(TeachingFailure.ACCESS_LOST)
    }

    fun sourceRestarted(logs: Boolean, input: Boolean = false, accessibility: Boolean = false) {
        val result = when (val current = state) {
            is TeachingState.Checking -> current.result
            else -> validatedResult() ?: return
        }
        if ((result is LearnResult.KeyEventFound && accessibility) ||
            (result is LearnResult.LogLineFound && (logs || (input && result.inputKey != null)))) {
            fail(TeachingFailure.ACCESS_LOST)
        }
    }

    fun save(write: (LearnedKeyPatch) -> Boolean): Boolean {
        val result = validatedResult() ?: return false
        if (!requiredAccess(result)) {
            fail(TeachingFailure.ACCESS_LOST)
            return false
        }
        val saved = runCatching { write(LearnedKeyPatch(result)) }.getOrDefault(false)
        state = if (saved) TeachingState.Closed else TeachingState.SaveFailed(result)
        return saved
    }

    private fun validatedResult(): LearnResult? = when (val current = state) {
        is TeachingState.Ready -> current.result
        is TeachingState.SaveFailed -> current.result
        else -> null
    }

    fun cancel() {
        observations.clear()
        pressed.clear()
        state = TeachingState.Closed
    }

    private fun requiredAccess(result: LearnResult): Boolean = when (result) {
        is LearnResult.KeyEventFound -> access.accessibility
        is LearnResult.LogLineFound -> access.fullLogs && (result.inputKey == null || access.inputLive)
        LearnResult.NothingFound -> false
    }

    private fun fail(reason: TeachingFailure) {
        pressed.clear()
        state = TeachingState.Failed(reason)
    }

    private fun captureSample(sample: KeySample, atMs: Long) {
        when (sample) {
            is KeySample.Accessibility -> if (sample.down && sample.repeat == 0 && sample.code !in IGNORED_CAPTURE_KEYS) {
                observations += Observation(atMs, KeyLearning.keyEventKey(sample.code, sample.scan))
            }
            is KeySample.Input -> if (sample.event.down) {
                observations += Observation(atMs, KeyLearning.inputKey(sample.event.key.code))
            }
            is KeySample.Log -> {
                observations += Observation(atMs, KeyLearning.logKey(sample.uid, sample.tag, sample.message))
                loggedKeyCode(sample.uid, sample.tag, sample.message)?.let {
                    observations += Observation(atMs, KeyLearning.loggedKey(it))
                }
            }
        }
    }

    private fun witness(result: LearnResult, sample: KeySample, allowDown: Boolean) {
        val edge: Pair<WitnessKey, Boolean> = when (sample) {
            is KeySample.Accessibility -> {
                if (sample.repeat != 0) return
                WitnessKey.Accessibility(LearnedKey(sample.code, if (sample.code == 0) sample.scan else 0)) to sample.down
            }
            is KeySample.Input -> {
                if (result !is LearnResult.LogLineFound) return
                val code = sample.event.key.code
                if (code == "BTN_TOUCH" || code.startsWith("BTN_TOOL_") || code.startsWith("BTN_STYLUS")) return
                WitnessKey.Input(sample.event.key) to sample.event.down
            }
            is KeySample.Log -> {
                if (result !is LearnResult.LogLineFound) return
                val code = loggedKeyCode(sample.uid, sample.tag, sample.message) ?: return
                if (code !in VOLUME_CODES || code == result.logKeyCode) return
                val action = LOG_ACTION.find(sample.message)?.groupValues?.get(1) ?: return
                WitnessKey.LoggedVolume(sample.threadId, code) to (action == "ACTION_DOWN" || action == "0")
            }
        }
        if (edge.second) {
            if (allowDown) pressed += edge.first
        } else if (pressed.remove(edge.first)) completedPress = true
    }

    private fun LearnResult.binding(): KeyBinding = when (this) {
        is LearnResult.KeyEventFound -> KeyBinding.Direct(key)
        is LearnResult.LogLineFound -> KeyBinding.Logged(signature, logKeyCode, inputKey)
        LearnResult.NothingFound -> error("No candidate")
    }

    internal class LearnedKeyPatch(private val result: LearnResult) {
        private var used = false

        fun applyTo(current: Config): Config {
            check(!used) { "Teaching patch already used" }
            used = true
            return when (result) {
                is LearnResult.KeyEventFound -> current.copy(
                    keyEvent = result.key, logSignature = null, logKeyCode = null, inputKey = result.inputKey,
                )
                is LearnResult.LogLineFound -> current.copy(
                    keyEvent = null, logSignature = result.signature, logKeyCode = result.logKeyCode,
                    inputKey = result.inputKey, holdRepeats = result.holdRepeats, releaseGapMs = result.releaseGapMs,
                )
                LearnResult.NothingFound -> error("No candidate")
            }
        }
    }

    companion object {
        const val CHECK_MS = 5000L
        const val SETTLE_MS = 750L
        private val IGNORED_CAPTURE_KEYS = setOf(24, 25, 164, 26, 4, 3, 187)
        private val VOLUME_CODES = setOf("KEYCODE_VOLUME_UP", "KEYCODE_VOLUME_DOWN", "KEYCODE_VOLUME_MUTE", "24", "25", "164")
        private val LOG_ACTION = Regex("""\baction=(ACTION_DOWN|ACTION_UP|0|1)(?=[,\s}])""")
    }
}
