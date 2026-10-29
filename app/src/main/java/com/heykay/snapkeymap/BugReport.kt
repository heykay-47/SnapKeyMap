package com.heykay.snapkeymap

import android.content.Context
import android.os.Build
import java.net.URLEncoder
import rikka.shizuku.Shizuku

data class ReportInfo(
    val versionName: String,
    val versionCode: Long,
    val phoneModel: String,
    val colorOsVersion: String,
    val androidRelease: String,
    val sdk: Int,
    val shizukuState: String,
    val detectionMode: String,
    val hasLogPermission: Boolean,
)

fun gatherReportInfo(context: Context, config: Config, precise: Boolean): ReportInfo {
    val version = context.packageManager.getPackageInfo(context.packageName, 0)
    val colorOs = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, "ro.build.version.oplusrom") as String
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: "unknown"
    val shizuku = runCatching {
        when {
            !Shizuku.pingBinder() -> "Not running"
            canUseShizuku() -> "Running and allowed"
            else -> "Running, not allowed"
        }
    }.getOrDefault("Unavailable")
    return ReportInfo(
        versionName = version.versionName ?: "unknown",
        versionCode = version.longVersionCode,
        phoneModel = Build.MODEL,
        colorOsVersion = colorOs,
        androidRelease = Build.VERSION.RELEASE,
        sdk = Build.VERSION.SDK_INT,
        shizukuState = shizuku,
        detectionMode = when {
            !config.needsLogAccess -> "Accessibility key events"
            precise -> "Precise input via Shizuku"
            else -> "System logs"
        },
        hasLogPermission = SnapEngine.hasLogPermission(),
    )
}

internal fun learnedKeySummary(config: Config): String = listOf(
    config.keyEvent?.let { "keyEvent: code=${it.keyCode}, scan=${it.scanCode}" } ?: "keyEvent: not set",
    config.inputKey?.let { "inputKey: ${it.code}" } ?: "inputKey: not set",
    config.logSignature?.let { "logSignature: tag=${it.tag}" } ?: "logSignature: not set",
    config.logKeyCode?.let { "logKeyCode: $it" } ?: "logKeyCode: not set",
).joinToString("\n")

private fun actionKind(action: SnapAction): String = when (action) {
    SnapAction.NoAction -> "Do nothing"
    is SnapAction.OpenApp -> "Open an app"
    is SnapAction.OpenShortcut -> "Open a shortcut"
    is SnapAction.OpenLink -> "Open a link"
    is SnapAction.CallContact -> "Call a contact"
    is SnapAction.Device -> action.action.label
    is SnapAction.Ringer -> "Ringer: ${action.modes.joinToString(" → ") { it.label }}"
}

private fun actionsSummary(config: Config): String = Gesture.entries.joinToString("\n") { gesture ->
    "${gesture.title}: ${actionKind(config.action(gesture))}; unlocked-only: ${if (gesture in config.unlockedOnly) "on" else "off"}"
}

private fun detectionText(detected: SnapEngine.Detected, timeFormat: (Long) -> String): String =
    "${detected.gesture.title} at ${timeFormat(detected.atMs)} [${detected.source}]"

fun buildReport(
    info: ReportInfo,
    config: Config,
    detections: List<SnapEngine.Detected>,
    timeFormat: (Long) -> String,
): String = buildString {
    appendLine("SnapKey Map ${info.versionName} (${info.versionCode})")
    appendLine("Phone: ${info.phoneModel}")
    appendLine("ColorOS: ${info.colorOsVersion}")
    appendLine("Android: ${info.androidRelease} (SDK ${info.sdk})")
    appendLine("Grant method: ${grantMethodText(config.grantMethod)}")
    appendLine("Log permission: ${if (info.hasLogPermission) "granted" else "not granted"}")
    appendLine("Shizuku: ${info.shizukuState}")
    appendLine("Detection mode: ${info.detectionMode}")
    appendLine(learnedKeySummary(config))
    appendLine("Double-press window: ${config.doublePressWindowMs} ms")
    appendLine("Hold threshold: ${config.holdMs} ms")
    appendLine()
    appendLine(actionsSummary(config))
    appendLine()
    appendLine("Last $DETECTION_HISTORY_SIZE detections:")
    if (detections.isEmpty()) appendLine("None yet")
    else detections.takeLast(DETECTION_HISTORY_SIZE).forEach { appendLine(detectionText(it, timeFormat)) }
}.trimEnd()

private fun grantMethodText(method: GrantMethod?): String = when (method) {
    GrantMethod.SHIZUKU -> "Shizuku"
    GrantMethod.ADB -> "Computer (ADB)"
    null -> "Not granted yet"
}

fun bugReportUrl(
    info: ReportInfo,
    config: Config,
    last: SnapEngine.Detected?,
    timeFormat: (Long) -> String,
): String {
    val logAccess = if (config.needsLogAccess && !info.hasLogPermission) "Not granted yet"
        else grantMethodText(config.grantMethod)
    val fields = listOf(
        "app-version" to encodeReportField("${info.versionName} (${info.versionCode})", 100),
        "device" to encodeReportField("${info.phoneModel}, ColorOS ${info.colorOsVersion}, Android ${info.androidRelease} (SDK ${info.sdk})", 300),
        "log-access" to encodeReportField(logAccess, 100),
        "teach" to encodeReportField(learnedKeySummary(config), 400),
        "actions" to encodeReportField(actionsSummary(config) + "\nLast: " +
            (last?.let { detectionText(it, timeFormat) } ?: "None yet"), 700),
    )
    return "https://github.com/heykay-47/SnapKeyMap/issues/new?template=bug_report.yml&" +
        fields.joinToString("&") { (key, value) -> "$key=$value" }
}

private fun encodeReportField(value: String, maxLength: Int): String {
    var text = value.take(maxLength)
    var encoded = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
    while (encoded.length > maxLength) {
        text = text.dropLast(1)
        encoded = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
    }
    return encoded
}
