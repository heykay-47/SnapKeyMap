package com.heykay.snapkeymap

internal sealed interface KeyBinding {
    data class Direct(val key: LearnedKey) : KeyBinding
    data class Logged(
        val signature: KeySignature?,
        val logKeyCode: String?,
        val inputKey: InputKey?,
    ) : KeyBinding
}

internal fun Config.keyBinding(): KeyBinding = keyEvent?.let { KeyBinding.Direct(it) }
    ?: KeyBinding.Logged(logSignature, logKeyCode, inputKey)

internal sealed interface KeySample {
    data class Accessibility(val code: Int, val scan: Int, val down: Boolean, val repeat: Int = 0) : KeySample
    data class Input(val event: InputKeyEvent) : KeySample
    data class Log(
        val uid: String, val tag: String, val message: String, val threadKey: String?, val threadId: String = "",
    ) : KeySample
}

internal object KeyMatching {
    fun matches(binding: KeyBinding, sample: KeySample): Boolean = when {
        binding is KeyBinding.Direct && sample is KeySample.Accessibility ->
            sample.code == binding.key.keyCode && (sample.code != 0 || sample.scan == binding.key.scanCode)
        binding is KeyBinding.Logged && sample is KeySample.Input ->
            binding.inputKey != null && sample.event.key == binding.inputKey
        binding is KeyBinding.Logged && sample is KeySample.Log ->
            acceptsKeyLog(binding.signature, sample.uid, sample.tag, sample.message) &&
                (!isKnownOplusKeyLine(sample.uid, sample.tag, sample.message) ||
                    acceptsLoggedKey(binding.logKeyCode, sample.threadKey))
        else -> false
    }
}

internal class LoggedKeyContext {
    private val keys = mutableMapOf<String, String>()
    private var boundary: Pair<Int, Long>? = null

    fun sample(generation: Int, epoch: Long, uid: String, tid: String, tag: String, message: String): KeySample.Log {
        val next = generation to epoch
        if (boundary != next) {
            keys.clear()
            boundary = next
        }
        loggedKeyCode(uid, tag, message)?.let { keys[tid] = it }
        return KeySample.Log(uid, tag, message, keys[tid], tid)
    }
}
