package com.heykay.snapkeymap

import org.json.JSONArray
import org.json.JSONObject

enum class Gesture(val title: String) {
    SINGLE("Press"),
    DOUBLE("Double press"),
    LONG("Press & hold"),
    TAP_HOLD("Tap, then hold"),
}

enum class Section(val title: String) {
    PHONE("Phone"),
    SCREEN("Screen & navigation"),
    MEDIA("Music & video"),
    OPEN("Open"),
}

/** Built-in things the key can do. [needsService] actions go through the accessibility service. */
enum class DeviceAction(val label: String, val section: Section, val needsService: Boolean = false) {
    FLASHLIGHT("Flashlight on / off", Section.PHONE),
    DO_NOT_DISTURB("Do Not Disturb on / off", Section.PHONE),
    SCREENSHOT("Take a screenshot", Section.SCREEN, needsService = true),
    SEND_SCREENSHOT_TO_MIND_SPACE("Send screenshot to Mind Space", Section.SCREEN, needsService = true),
    CIRCLE_TO_SEARCH("Circle to Search", Section.SCREEN),
    LOCK_SCREEN("Lock the screen", Section.SCREEN, needsService = true),
    NOTIFICATIONS("Show notifications", Section.SCREEN, needsService = true),
    QUICK_SETTINGS("Show quick settings", Section.SCREEN, needsService = true),
    RECENT_APPS("Recent apps", Section.SCREEN, needsService = true),
    HOME("Go to home screen", Section.SCREEN, needsService = true),
    BACK("Go back", Section.SCREEN, needsService = true),
    POWER_MENU("Power menu", Section.SCREEN, needsService = true),
    PLAY_PAUSE("Play / pause", Section.MEDIA),
    NEXT_TRACK("Next track", Section.MEDIA),
    PREVIOUS_TRACK("Previous track", Section.MEDIA),
    MEDIA_VOLUME_UP("Media volume up", Section.MEDIA),
    MEDIA_VOLUME_DOWN("Media volume down", Section.MEDIA),
    MEDIA_MUTE("Mute / unmute media", Section.MEDIA),
    VOLUME_PANEL("Show volume slider", Section.MEDIA),
    CAMERA("Camera", Section.OPEN),
    VIDEO_CAMERA("Video camera", Section.OPEN),
    ASSISTANT("Voice assistant", Section.OPEN),
    WIFI_PANEL("Wi-Fi panel", Section.OPEN),
    BLUETOOTH_SETTINGS("Bluetooth settings", Section.OPEN),
    NFC_PANEL("NFC panel", Section.OPEN),
    INTERNET_PANEL("Internet / mobile data panel", Section.OPEN),
}

enum class RingerMode(val label: String) { RING("Ring"), VIBRATE("Vibrate"), SILENT("Silent") }

sealed interface SnapAction {
    data object NoAction : SnapAction
    data class OpenApp(val packageName: String, val appName: String) : SnapAction
    /** An app shortcut (direct dial, a Tasker task, a Maps route...) stored as an intent URI. */
    data class OpenShortcut(val intentUri: String, val name: String) : SnapAction
    data class OpenLink(val url: String) : SnapAction
    data class CallContact(val number: String, val name: String) : SnapAction
    data class Device(val action: DeviceAction) : SnapAction
    data class Ringer(val modes: List<RingerMode>) : SnapAction {
        init {
            require(modes.size in 2..3 && modes.distinct().size == modes.size)
        }

        fun nextMode(current: RingerMode?, allowSilent: Boolean = true): RingerMode {
            val index = (modes.indexOf(current) + 1) % modes.size
            val next = modes[index]
            return if (next == RingerMode.SILENT && !allowSilent) modes[(index + 1) % modes.size] else next
        }
    }

    val label: String
        get() = when (this) {
            NoAction -> "Do nothing"
            is OpenApp -> "Open $appName"
            is OpenShortcut -> name
            is OpenLink -> "Open $url"
            is CallContact -> "Call $name"
            is Device -> action.label
            is Ringer -> modes.joinToString(" → ") { it.label }
        }

    fun toJson(): String {
        val json = JSONObject()
        when (this) {
            NoAction -> json.put("type", "none")
            is OpenApp -> json.put("type", "app").put("package", packageName).put("name", appName)
            is OpenShortcut -> json.put("type", "shortcut").put("uri", intentUri).put("name", name)
            is OpenLink -> json.put("type", "link").put("url", url)
            is CallContact -> json.put("type", "contact").put("number", number).put("name", name)
            is Device -> json.put("type", "device").put("action", action.name)
            is Ringer -> json.put("type", "ringer").put("modes", JSONArray(modes.map { it.name }))
        }
        return json.toString()
    }

    companion object {
        fun fromJson(text: String?): SnapAction? {
            if (text == null) return null
            return runCatching {
                val json = JSONObject(text)
                when (json.getString("type")) {
                    "none" -> NoAction
                    "app" -> OpenApp(json.getString("package"), json.getString("name"))
                    "shortcut" -> OpenShortcut(json.getString("uri"), json.getString("name"))
                    "link" -> OpenLink(json.getString("url"))
                    "contact" -> CallContact(json.getString("number"), json.getString("name"))
                    "device" -> when (val name = json.getString("action")) {
                        "RINGER_CYCLE" -> Ringer(RingerMode.entries.toList())
                        // Android 13+ ignores the split screen global action, so the action was removed.
                        "SPLIT_SCREEN" -> NoAction
                        else -> Device(DeviceAction.valueOf(name))
                    }
                    "ringer" -> {
                        val modes = json.getJSONArray("modes")
                        Ringer(List(modes.length()) { RingerMode.valueOf(modes.getString(it)) })
                    }
                    else -> null
                }
            }.getOrNull()
        }
    }
}
