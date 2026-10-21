package com.heykay.snapkeymap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class GrantMethod { SHIZUKU, ADB }
enum class SetupPage { METHOD, LOG_ACCESS, ACCESSIBILITY, BATTERY, NOTIFICATIONS, TEACH, ACTIONS }

data class Config(
    val enabled: Boolean = true,
    val actions: Map<Gesture, SnapAction> = mapOf(Gesture.SINGLE to SnapAction.Device(DeviceAction.FLASHLIGHT)),
    /** Set when Android hands the Snap Key to apps as a normal key. Then no log access is needed. */
    val keyEvent: LearnedKey? = null,
    val inputKey: InputKey? = null,
    /** Set when the Snap Key can only be seen in the system log. */
    val logSignature: KeySignature? = null,
    val holdRepeats: Boolean = true,
    val releaseGapMs: Long = DEFAULT_RELEASE_GAP_MS,
    val doublePressWindowMs: Long = 350L,
    val holdMs: Long = 650L,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val setupComplete: Boolean = false,
    /** Null until the user confirms a method, so an interrupted first run starts with the choice. */
    val grantMethod: GrantMethod? = null,
    val setupSkipped: Set<SetupPage> = emptySet(),
) {
    val needsLogAccess get() = keyEvent == null
    val isTaught get() = keyEvent != null || logSignature?.isUsable == true
    /** A hold needs either explicit down/up edges or log lines that repeat while held. */
    val canDetectHold get() = keyEvent != null || inputKey != null || logSignature?.hasExplicitKeyEdges == true || holdRepeats

    fun action(gesture: Gesture): SnapAction = actions[gesture] ?: SnapAction.NoAction
}

class ConfigStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("snapkey", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())
    val config: StateFlow<Config> = state.asStateFlow()

    fun update(change: (Config) -> Config) {
        val changed = change(state.value)
        val next = changed.copy(
            doublePressWindowMs = changed.doublePressWindowMs.coerceIn(200L, 600L),
            holdMs = changed.holdMs.coerceIn(300L, 1200L),
        )
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
        val config = Config(
            enabled = prefs.getBoolean("enabled", true),
            actions = actions,
            keyEvent = if (keyCode >= 0) LearnedKey(keyCode, prefs.getInt("scan_code", 0)) else null,
            inputKey = prefs.getString("input_code", null)?.let(::InputKey),
            logSignature = if (tag != null && message != null) {
                KeySignature(tag, message, prefs.getString("log_uid", null)?.let(::normalizeLogUid))
            } else null,
            holdRepeats = prefs.getBoolean("hold_repeats", true),
            releaseGapMs = prefs.getLong("release_gap", DEFAULT_RELEASE_GAP_MS),
            doublePressWindowMs = prefs.getLong("double_press_window", 350L).coerceIn(200L, 600L),
            holdMs = prefs.getLong("hold_ms", 650L).coerceIn(300L, 1200L),
            theme = ThemeMode.entries.firstOrNull { it.name == theme } ?: ThemeMode.SYSTEM,
            setupComplete = prefs.getBoolean("setup_complete", false),
            grantMethod = GrantMethod.entries.firstOrNull { it.name == prefs.getString("grant_method", null) },
            setupSkipped = setOf(SetupPage.BATTERY, SetupPage.NOTIFICATIONS).filter {
                it.name in prefs.getStringSet("setup_skipped", emptySet()).orEmpty()
            }.toSet(),
        )
        if (!prefs.contains("setup_complete")) {
            val migrated = config.copy(
                setupComplete = config.isTaught &&
                    context.checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED,
            )
            save(migrated)
            return migrated
        }
        return config
    }

    private fun save(config: Config) {
        prefs.edit().apply {
            putBoolean("enabled", config.enabled)
            putString("theme", config.theme.name)
            putBoolean("setup_complete", config.setupComplete)
            putString("grant_method", config.grantMethod?.name)
            putStringSet("setup_skipped", config.setupSkipped.map { it.name }.toSet())
            Gesture.entries.forEach { putString("action_${it.name}", config.action(it).toJson()) }
            putInt("key_code", config.keyEvent?.keyCode ?: -1)
            putInt("scan_code", config.keyEvent?.scanCode ?: 0)
            putString("input_code", config.inputKey?.code)
            if (config.logSignature != null) {
                putString("log_tag", config.logSignature.tag)
                putString("log_message", config.logSignature.message)
                putString("log_uid", config.logSignature.uid?.let(::normalizeLogUid))
            } else {
                remove("log_tag")
                remove("log_message")
                remove("log_uid")
            }
            putBoolean("hold_repeats", config.holdRepeats)
            putLong("release_gap", config.releaseGapMs)
            putLong("double_press_window", config.doublePressWindowMs)
            putLong("hold_ms", config.holdMs)
        }.apply()
    }
}
