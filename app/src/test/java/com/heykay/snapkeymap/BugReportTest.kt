package com.heykay.snapkeymap

import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BugReportTest {
    private val info = ReportInfo(
        versionName = "1.2.0",
        versionCode = 10200,
        phoneModel = "Find X9",
        colorOsVersion = "16.0",
        androidRelease = "16",
        sdk = 36,
        shizukuState = "Running and allowed",
        detectionMode = "Precise input via Shizuku",
        hasLogPermission = true,
    )
    private val timeFormat: (Long) -> String = { "time-$it" }

    @Test
    fun reportAndUrlIncludeActionKindsWithoutPrivateTargetsOrLogMessages() {
        val config = Config(
            actions = mapOf(
                Gesture.SINGLE to SnapAction.CallContact("+15551234567", "Mum"),
                Gesture.DOUBLE to SnapAction.OpenLink("https://secret.example/x"),
                Gesture.LONG to SnapAction.OpenApp("com.whatsapp", "WhatsApp"),
                Gesture.TAP_HOLD to SnapAction.OpenShortcut("intent:#Intent;component=a/b;end", "Route"),
            ),
            logSignature = KeySignature("KeyTag", "private raw log message", "1000"),
            pausedApps = setOf("com.private.app"),
            unlockedOnly = setOf(Gesture.SINGLE),
        )
        val last = SnapEngine.Detected(Gesture.SINGLE, 123L, DetectionSource.PRECISE_INPUT)
        val report = buildReport(info, config, listOf(last), timeFormat)
        val url = bugReportUrl(info, config, last, timeFormat)
        val decodedUrl = URLDecoder.decode(url, "UTF-8")

        listOf(report, url, decodedUrl).forEach { text ->
            listOf("+15551234567", "https://secret.example/x", "secret.example", "com.whatsapp", "WhatsApp",
                "intent:", "Route", "Mum", "private raw log message", "com.private.app").forEach { secret ->
                assertFalse("Leaked $secret", text.contains(secret))
            }
        }
        listOf(report, decodedUrl).forEach { text ->
            listOf("Call a contact", "Open a link", "Open an app", "Open a shortcut", "unlocked-only: on",
                "tag=KeyTag", "time-123", "PRECISE_INPUT").forEach { expected ->
                assertTrue("Missing $expected", text.contains(expected))
            }
        }
        assertTrue(decodedUrl.contains("Last: Press"))
    }

    @Test
    fun historyKeepsExactlyTheLastTwentyDetectionsInOrder() {
        val detections = (0L until 25L).map {
            SnapEngine.Detected(Gesture.SINGLE, it, DetectionSource.KEY_EVENT)
        }
        val history = detections.fold(emptyList<SnapEngine.Detected>(), ::appendDetection)

        assertEquals(20, history.size)
        assertEquals(detections.takeLast(20), history)
    }

    @Test
    fun reportIncludesEnvironmentTimingLearnedKeysLocksAndBoundedHistory() {
        val config = Config(
            grantMethod = GrantMethod.SHIZUKU,
            keyEvent = LearnedKey(42, 24),
            inputKey = InputKey("02ab"),
            logKeyCode = "999",
            actions = mapOf(
                Gesture.SINGLE to SnapAction.Device(DeviceAction.SCREENSHOT),
                Gesture.DOUBLE to SnapAction.Ringer(listOf(RingerMode.RING, RingerMode.VIBRATE)),
            ),
        )
        val detections = (0L until 25L).map {
            SnapEngine.Detected(Gesture.SINGLE, it, DetectionSource.SYSTEM_LOG)
        }
        val report = buildReport(info, config, detections, timeFormat)

        listOf("1.2.0 (10200)", "Find X9", "ColorOS: 16.0", "Android: 16 (SDK 36)",
            "Grant method: Shizuku", "Running and allowed", "Precise input via Shizuku",
            "keyEvent: code=42, scan=24", "inputKey: 02ab", "logSignature: not set", "logKeyCode: 999",
            "Double-press window: 350 ms", "Hold threshold: 650 ms", "Take a screenshot",
            "Ringer: Ring → Vibrate", "Do nothing", "unlocked-only: off", "time-24 [SYSTEM_LOG]").forEach {
            assertTrue("Missing $it", report.contains(it))
        }
        assertEquals(20, report.lines().count { it.contains("[SYSTEM_LOG]") })
        assertFalse(report.contains("time-4 [SYSTEM_LOG]"))
    }

    @Test
    fun logAccessMatchesTheFormOptionsForEveryGrantAndPermissionState() {
        val options = setOf("Shizuku", "Computer (ADB)", "Not granted yet")
        (GrantMethod.entries + null).forEach { method ->
            listOf(false, true).forEach { permission ->
                listOf(false, true).forEach { needsLog ->
                    val config = Config(grantMethod = method, keyEvent = if (needsLog) null else LearnedKey(42, 0))
                    val url = bugReportUrl(info.copy(hasLogPermission = permission), config, null, timeFormat)
                    val access = queryFields(url).getValue("log-access")

                    assertTrue(access in options)
                    assertEquals(when {
                        needsLog && !permission -> "Not granted yet"
                        method == GrantMethod.SHIZUKU -> "Shizuku"
                        method == GrantMethod.ADB -> "Computer (ADB)"
                        else -> "Not granted yet"
                    }, access)
                    assertTrue(url.length < 2000)
                    assertFalse(url.contains("+"))
                    assertEquals(setOf("template", "app-version", "device", "log-access", "teach", "actions"),
                        queryFields(url).keys)
                }
            }
        }
    }

    @Test
    fun urlStaysBoundedWithLongUnicodeEnvironmentAndLearnedFields() {
        val longText = "手機 &?".repeat(1000)
        val url = bugReportUrl(
            info.copy(versionName = longText, phoneModel = longText, colorOsVersion = longText),
            Config(inputKey = InputKey(longText), logSignature = KeySignature(longText, "never include me")),
            SnapEngine.Detected(Gesture.LONG, 123L, DetectionSource.SYSTEM_LOG),
            { longText },
        )

        assertTrue(url.length < 1800)
        assertEquals("bug_report.yml", queryFields(url)["template"])
        assertFalse(url.contains("never"))
    }

    private fun queryFields(url: String): Map<String, String> = URI(url).rawQuery.split("&").associate { field ->
        val (key, value) = field.split("=", limit = 2)
        key to URLDecoder.decode(value, "UTF-8")
    }
}
