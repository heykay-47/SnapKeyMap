package com.heykay.snapkeymap

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener

const val BACKUP_FORMAT = 1

data class SettingsBackup(
    val actions: Map<Gesture, SnapAction>,
    val ringerPresets: List<List<RingerMode>>,
    val unlockedOnly: Set<Gesture>,
    val pausedApps: Set<String>,
    val doublePressWindowMs: Long,
    val holdMs: Long,
    val theme: ThemeMode,
    val showConfirmations: Boolean,
    val keyEvent: LearnedKey?,
    val inputKey: InputKey?,
    val logSignature: KeySignature?,
    val logKeyCode: String?,
    val holdRepeats: Boolean,
    val releaseGapMs: Long,
) {
    fun applyTo(current: Config): Config = current.copy(
        actions = actions,
        ringerPresets = ringerPresets,
        unlockedOnly = unlockedOnly,
        pausedApps = pausedApps,
        doublePressWindowMs = doublePressWindowMs,
        holdMs = holdMs,
        theme = theme,
        showConfirmations = showConfirmations,
        keyEvent = keyEvent,
        inputKey = inputKey,
        logSignature = logSignature,
        logKeyCode = logKeyCode,
        holdRepeats = holdRepeats,
        releaseGapMs = releaseGapMs,
    )
}

sealed interface BackupResult {
    data class Ok(val backup: SettingsBackup) : BackupResult
    data class Error(val message: String) : BackupResult
}

fun backupToJson(config: Config): String = JSONObject().apply {
    put("format", BACKUP_FORMAT)
    put("actions", JSONObject().apply {
        config.actions.forEach { (gesture, action) -> put(gesture.name, JSONObject(action.toJson())) }
    })
    put("ringerPresets", JSONArray(ringerPresetsToJson(config.ringerPresets)))
    put("unlockedOnly", JSONArray(config.unlockedOnly.map { it.name }))
    put("pausedApps", JSONArray(config.pausedApps.toList()))
    put("doublePressWindowMs", config.doublePressWindowMs)
    put("holdMs", config.holdMs)
    put("theme", config.theme.name)
    put("showConfirmations", config.showConfirmations)
    put("key", JSONObject().apply {
        put("keyEvent", config.keyEvent?.let {
            JSONObject().put("keyCode", it.keyCode).put("scanCode", it.scanCode)
        } ?: JSONObject.NULL)
        put("inputKey", config.inputKey?.code ?: JSONObject.NULL)
        put("logSignature", config.logSignature?.let {
            JSONObject().put("tag", it.tag).put("message", it.message)
                .put("uid", it.uid?.let(::normalizeLogUid) ?: JSONObject.NULL)
        } ?: JSONObject.NULL)
        put("logKeyCode", config.logKeyCode ?: JSONObject.NULL)
        put("holdRepeats", config.holdRepeats)
        put("releaseGapMs", config.releaseGapMs)
    })
}.toString(2)

fun parseBackup(text: String): BackupResult = try {
    val tokener = JSONTokener(text)
    val json = JSONObject(tokener)
    require(tokener.nextClean() == '\u0000') { "The backup has unexpected text after the JSON." }
    val format = json.opt("format")
    require(format is Int || format is Long) { "The backup must have an integer format version." }
    val version = (format as Number).toLong()
    require(version <= BACKUP_FORMAT) { "This backup is from a newer SnapKey Map. Update the app first." }
    require(version == BACKUP_FORMAT.toLong()) { "This backup format is not supported." }

    val actionJson = json.getJSONObject("actions")
    val actions = actionJson.keys().asSequence().associate { name ->
        val gesture = backupEnum<Gesture>(name, "gesture")
        val action = actionJson.getJSONObject(name)
        gesture to requireNotNull(SnapAction.fromJson(action.toString())) { "Invalid action for $name." }
    }
    val presetsJson = json.getJSONArray("ringerPresets")
    val presets = List(presetsJson.length()) { backupRingerModes(presetsJson.getJSONArray(it)) }
    val key = json.getJSONObject("key")
    val keyEvent = key.backupNullableObject("keyEvent")?.let {
        LearnedKey(it.backupInt("keyCode"), it.backupInt("scanCode"))
    }
    val signature = key.backupNullableObject("logSignature")?.let {
        KeySignature(
            it.backupString("tag"),
            it.backupString("message"),
            it.backupNullableString("uid")?.let(::normalizeLogUid),
        )
    }
    BackupResult.Ok(SettingsBackup(
        actions = actions,
        ringerPresets = loadRingerPresets(ringerPresetsToJson(presets), actions.values),
        unlockedOnly = json.getJSONArray("unlockedOnly").backupStrings().map { backupEnum<Gesture>(it, "gesture") }.toSet(),
        pausedApps = json.getJSONArray("pausedApps").backupStrings().toSet(),
        doublePressWindowMs = json.backupLong("doublePressWindowMs"),
        holdMs = json.backupLong("holdMs"),
        theme = backupEnum<ThemeMode>(json.backupString("theme"), "theme"),
        showConfirmations = json.backupBoolean("showConfirmations"),
        keyEvent = keyEvent,
        inputKey = key.backupNullableString("inputKey")?.let(::InputKey),
        logSignature = signature,
        logKeyCode = key.backupNullableString("logKeyCode"),
        holdRepeats = key.backupBoolean("holdRepeats"),
        releaseGapMs = key.backupLong("releaseGapMs"),
    ))
} catch (_: JSONException) {
    BackupResult.Error("This file isn't a valid SnapKey Map backup. A required field is missing or malformed.")
} catch (error: IllegalArgumentException) {
    BackupResult.Error(error.message ?: "This file isn't a valid SnapKey Map backup.")
}

private inline fun <reified T : Enum<T>> backupEnum(name: String, field: String): T =
    enumValues<T>().firstOrNull { it.name == name }
        ?: throw IllegalArgumentException("Unknown $field: $name.")

private fun backupRingerModes(array: JSONArray): List<RingerMode> {
    val modes = array.backupStrings().map { backupEnum<RingerMode>(it, "ringer mode") }
    return requireNotNull(runCatching { SnapAction.Ringer(modes) }.getOrNull()) {
        "A ringer preset must contain two or three different ringer modes."
    }.modes
}

private fun JSONArray.backupStrings(): List<String> = List(length()) {
    requireNotNull(get(it) as? String) { "The backup contains a list entry that is not text." }
}

private fun JSONObject.backupString(name: String): String =
    requireNotNull(get(name) as? String) { "The backup field $name must be text." }

private fun JSONObject.backupNullableString(name: String): String? =
    if (get(name) == JSONObject.NULL) null else backupString(name)

private fun JSONObject.backupNullableObject(name: String): JSONObject? =
    if (get(name) == JSONObject.NULL) null else getJSONObject(name)

private fun JSONObject.backupBoolean(name: String): Boolean =
    requireNotNull(get(name) as? Boolean) { "The backup field $name must be true or false." }

private fun JSONObject.backupLong(name: String): Long {
    val value = get(name)
    require(value is Int || value is Long) { "The backup field $name must be an integer." }
    return (value as Number).toLong()
}

private fun JSONObject.backupInt(name: String): Int {
    val value = backupLong(name)
    require(value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "The backup field $name is out of range." }
    return value.toInt()
}
