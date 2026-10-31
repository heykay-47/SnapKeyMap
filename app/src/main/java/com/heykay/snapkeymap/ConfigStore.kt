package com.heykay.snapkeymap

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class GrantMethod { SHIZUKU, ADB }
enum class SetupPage { METHOD, LOG_ACCESS, ACCESSIBILITY, BATTERY, NOTIFICATIONS, TEACH, ACTIONS }

data class Config(
    val enabled: Boolean = true,
    val showConfirmations: Boolean = true,
    val pausedApps: Set<String> = emptySet(),
    val unlockedOnly: Set<Gesture> = emptySet(),
    val unlockHintShown: Boolean = false,
    val actions: Map<Gesture, SnapAction> = mapOf(Gesture.SINGLE to SnapAction.Device(DeviceAction.FLASHLIGHT)),
    val ringerPresets: List<List<RingerMode>> = DEFAULT_RINGER_PRESETS,
    /** Set when Android hands the Snap Key to apps as a normal key. Then no log access is needed. */
    val keyEvent: LearnedKey? = null,
    val inputKey: InputKey? = null,
    /** Set when the Snap Key can only be seen in the system log. */
    val logSignature: KeySignature? = null,
    val logKeyCode: String? = null,
    val holdRepeats: Boolean = true,
    val releaseGapMs: Long = DEFAULT_RELEASE_GAP_MS,
    val doublePressWindowMs: Long = 350L,
    val holdMs: Long = 650L,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val setupComplete: Boolean = false,
    val introSeen: Boolean = false,
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

internal val DEFAULT_RINGER_PRESETS = listOf(RingerMode.entries.toList())

internal fun ringerPresetsToJson(presets: List<List<RingerMode>>): String =
    JSONArray(presets.map { modes -> JSONArray(modes.map { it.name }) }).toString()

internal fun loadRingerPresets(text: String?, actions: Collection<SnapAction>): List<List<RingerMode>> {
    val array = runCatching { JSONArray(text ?: "[]") }.getOrNull()
    val presets = (0 until (array?.length() ?: 0)).mapNotNull { index ->
        runCatching {
            val modes = array!!.getJSONArray(index)
            SnapAction.Ringer(List(modes.length()) { RingerMode.valueOf(modes.getString(it)) }).modes
        }.getOrNull()
    }.distinct().ifEmpty { DEFAULT_RINGER_PRESETS }
    return (presets + actions.filterIsInstance<SnapAction.Ringer>().map { it.modes }).distinct()
}

internal fun Config.editRingerPreset(oldModes: List<RingerMode>, newModes: List<RingerMode>): Config {
    val replacement = SnapAction.Ringer(newModes)
    require(newModes == oldModes || newModes !in ringerPresets)
    require(oldModes in ringerPresets)
    return copy(
        ringerPresets = ringerPresets.map { if (it == oldModes) newModes else it },
        actions = actions.mapValues { (_, action) ->
            if (action == SnapAction.Ringer(oldModes)) replacement else action
        },
    )
}

class ConfigStore internal constructor(
    private val prefs: SharedPreferences,
    private val hasLogPermission: () -> Boolean,
) {
    constructor(context: Context) : this(
        context.getSharedPreferences("snapkey", Context.MODE_PRIVATE),
        { context.checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED },
    )

    private val state = MutableStateFlow(load())
    val config: StateFlow<Config> = state.asStateFlow()

    @Synchronized
    fun update(change: (Config) -> Config) {
        state.update { current ->
            bounded(change(current))
        }
        save(state.value)
    }

    @Synchronized
    internal fun updateConfirmed(change: (Config) -> Config): Boolean {
        val changed = bounded(change(state.value))
        val previous = prefs.all.mapValues { (_, value) -> if (value is Set<*>) value.toSet() else value }
        val saved = runCatching { edit(changed).commit() }.getOrDefault(false)
        if (!saved) {
            // commit can change SharedPreferences memory even when its disk write fails.
            runCatching {
                prefs.edit().apply {
                    clear()
                    previous.forEach { (key, value) ->
                        when (value) {
                            is Boolean -> putBoolean(key, value)
                            is Int -> putInt(key, value)
                            is Long -> putLong(key, value)
                            is Float -> putFloat(key, value)
                            is String -> putString(key, value)
                            is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                            else -> Unit
                        }
                    }
                }.commit()
            }
            return false
        }
        state.value = changed
        return true
    }

    private fun bounded(config: Config) = config.copy(
        doublePressWindowMs = config.doublePressWindowMs.coerceIn(200L, 600L),
        holdMs = config.holdMs.coerceIn(300L, 1200L),
    )

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
            showConfirmations = prefs.getBoolean("show_confirmations", true),
            pausedApps = prefs.getStringSet("paused_apps", emptySet()).orEmpty().toSet(),
            unlockedOnly = Gesture.entries.filter {
                it.name in prefs.getStringSet("unlocked_only", emptySet()).orEmpty()
            }.toSet(),
            unlockHintShown = prefs.getBoolean("unlock_hint_shown", false),
            actions = actions,
            ringerPresets = loadRingerPresets(prefs.getString("ringer_presets", null), actions.values),
            keyEvent = if (keyCode >= 0) LearnedKey(keyCode, prefs.getInt("scan_code", 0)) else null,
            inputKey = prefs.getString("input_code", null)?.let(::InputKey),
            logKeyCode = prefs.getString("log_key_code", null),
            logSignature = if (tag != null && message != null) {
                KeySignature(tag, message, prefs.getString("log_uid", null)?.let(::normalizeLogUid))
            } else null,
            holdRepeats = prefs.getBoolean("hold_repeats", true),
            releaseGapMs = prefs.getLong("release_gap", DEFAULT_RELEASE_GAP_MS),
            doublePressWindowMs = prefs.getLong("double_press_window", 350L).coerceIn(200L, 600L),
            holdMs = prefs.getLong("hold_ms", 650L).coerceIn(300L, 1200L),
            theme = ThemeMode.entries.firstOrNull { it.name == theme } ?: ThemeMode.SYSTEM,
            setupComplete = prefs.getBoolean("setup_complete", false),
            // Older versions persist setup_complete on first open, even before any step is finished.
            introSeen = prefs.getBoolean("intro_seen", prefs.contains("setup_complete") ||
                prefs.getString("grant_method", null) != null ||
                prefs.getStringSet("setup_skipped", emptySet()).orEmpty().isNotEmpty() ||
                keyCode >= 0 || prefs.getString("input_code", null) != null || tag != null || message != null ||
                prefs.getString("log_key_code", null) != null),
            grantMethod = GrantMethod.entries.firstOrNull { it.name == prefs.getString("grant_method", null) },
            setupSkipped = setOf(SetupPage.BATTERY, SetupPage.NOTIFICATIONS).filter {
                it.name in prefs.getStringSet("setup_skipped", emptySet()).orEmpty()
            }.toSet(),
        )
        if (!prefs.contains("setup_complete")) {
            val migrated = config.copy(
                setupComplete = config.isTaught && hasLogPermission(),
            )
            save(migrated)
            return migrated
        }
        return config
    }

    private fun save(config: Config) {
        edit(config).apply()
    }

    private fun edit(config: Config): SharedPreferences.Editor =
        prefs.edit().apply {
            putBoolean("enabled", config.enabled)
            putBoolean("show_confirmations", config.showConfirmations)
            putStringSet("paused_apps", config.pausedApps)
            putStringSet("unlocked_only", config.unlockedOnly.map { it.name }.toSet())
            putBoolean("unlock_hint_shown", config.unlockHintShown)
            putString("theme", config.theme.name)
            putBoolean("setup_complete", config.setupComplete)
            putBoolean("intro_seen", config.introSeen)
            putString("grant_method", config.grantMethod?.name)
            putStringSet("setup_skipped", config.setupSkipped.map { it.name }.toSet())
            Gesture.entries.forEach { putString("action_${it.name}", config.action(it).toJson()) }
            putString("ringer_presets", ringerPresetsToJson(config.ringerPresets))
            putInt("key_code", config.keyEvent?.keyCode ?: -1)
            putInt("scan_code", config.keyEvent?.scanCode ?: 0)
            putString("input_code", config.inputKey?.code)
            putString("log_key_code", config.logKeyCode)
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
        }
}
