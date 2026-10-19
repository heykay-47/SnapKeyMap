package com.heykay.snapkeymap

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Config(
    val enabled: Boolean = true,
    val actions: Map<Gesture, SnapAction> = mapOf(Gesture.SINGLE to SnapAction.Device(DeviceAction.FLASHLIGHT)),
    /** Set when Android hands the Snap Key to apps as a normal key. Then no log access is needed. */
    val keyEvent: LearnedKey? = null,
    /** Set when the Snap Key can only be seen in the system log. */
    val logSignature: KeySignature? = null,
    val holdRepeats: Boolean = true,
    val releaseGapMs: Long = DEFAULT_RELEASE_GAP_MS,
    val theme: ThemeMode = ThemeMode.SYSTEM,
) {
    val needsLogAccess get() = keyEvent == null
    val isTaught get() = keyEvent != null || logSignature != null
    /** A hold needs either explicit down/up edges or log lines that repeat while held. */
    val canDetectHold get() = keyEvent != null || logSignature?.hasExplicitKeyEdges == true || holdRepeats

    fun action(gesture: Gesture): SnapAction = actions[gesture] ?: SnapAction.NoAction
}

class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("snapkey", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val config: StateFlow<Config> = state.asStateFlow()

    fun update(change: (Config) -> Config) {
        val next = change(state.value)
        state.value = next
        save(next)
    }

    private fun load(): Config {
        val defaults = Config()
        val actions = Gesture.entries.associateWith { gesture ->
            SnapAction.fromJson(prefs.getString("action_${gesture.name}", null)) ?: defaults.action(gesture)
        }
        val keyCode = prefs.getInt("key_code", -1)
        val tag = prefs.getString("log_tag", null)
        val message = prefs.getString("log_message", null)
        val theme = prefs.getString("theme", null)
        return Config(
            enabled = prefs.getBoolean("enabled", true),
            actions = actions,
            keyEvent = if (keyCode >= 0) LearnedKey(keyCode, prefs.getInt("scan_code", 0)) else null,
            logSignature = if (tag != null && message != null) KeySignature(tag, message) else null,
            holdRepeats = prefs.getBoolean("hold_repeats", true),
            releaseGapMs = prefs.getLong("release_gap", DEFAULT_RELEASE_GAP_MS),
            theme = ThemeMode.entries.firstOrNull { it.name == theme } ?: ThemeMode.SYSTEM,
        )
    }

    private fun save(config: Config) {
        prefs.edit().apply {
            putBoolean("enabled", config.enabled)
            putString("theme", config.theme.name)
            Gesture.entries.forEach { putString("action_${it.name}", config.action(it).toJson()) }
            putInt("key_code", config.keyEvent?.keyCode ?: -1)
            putInt("scan_code", config.keyEvent?.scanCode ?: 0)
            if (config.logSignature != null) {
                putString("log_tag", config.logSignature.tag)
                putString("log_message", config.logSignature.message)
            } else {
                remove("log_tag")
                remove("log_message")
            }
            putBoolean("hold_repeats", config.holdRepeats)
            putLong("release_gap", config.releaseGapMs)
        }.apply()
    }
}
