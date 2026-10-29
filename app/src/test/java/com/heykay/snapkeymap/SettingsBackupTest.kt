package com.heykay.snapkeymap

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {
    private val ringSilent = listOf(RingerMode.RING, RingerMode.SILENT)
    private val vibrateRing = listOf(RingerMode.VIBRATE, RingerMode.RING)
    private val source = Config(
        enabled = false,
        showConfirmations = false,
        pausedApps = setOf("com.example.paused", "com.example.other"),
        unlockedOnly = setOf(Gesture.DOUBLE, Gesture.TAP_HOLD),
        unlockHintShown = true,
        actions = mapOf(
            Gesture.SINGLE to SnapAction.OpenApp("com.example.app", "Example app"),
            Gesture.DOUBLE to SnapAction.CallContact("+123456789", "Example contact"),
            Gesture.LONG to SnapAction.Ringer(ringSilent),
            Gesture.TAP_HOLD to SnapAction.OpenLink("https://example.com/path?q=backup"),
        ),
        ringerPresets = listOf(vibrateRing, ringSilent),
        keyEvent = LearnedKey(317, 759),
        inputKey = InputKey("KEY_F24"),
        logSignature = KeySignature("ExampleKey", "keyCode=# down", "1000"),
        logKeyCode = "317",
        holdRepeats = false,
        releaseGapMs = 420L,
        doublePressWindowMs = 500L,
        holdMs = 900L,
        theme = ThemeMode.DARK,
        setupComplete = true,
        introSeen = true,
        grantMethod = GrantMethod.SHIZUKU,
        setupSkipped = setOf(SetupPage.BATTERY, SetupPage.NOTIFICATIONS),
    )

    @Test
    fun roundTripRestoresAllBackedUpFieldsAndPreservesCurrentSetupState() {
        val current = Config(
            enabled = false,
            setupComplete = true,
            introSeen = true,
            grantMethod = GrantMethod.ADB,
            setupSkipped = setOf(SetupPage.NOTIFICATIONS),
            unlockHintShown = true,
        )
        val actionKinds = listOf(
            SnapAction.NoAction,
            SnapAction.OpenApp("com.example.missing", "Missing app"),
            SnapAction.OpenShortcut("intent:#Intent;action=example;end", "Example shortcut"),
            SnapAction.OpenLink("https://example.com"),
            SnapAction.CallContact("+987654321", "Another contact"),
            SnapAction.Device(DeviceAction.SCREENSHOT),
            SnapAction.Ringer(vibrateRing),
        )
        actionKinds.forEach { action ->
            val original = source.copy(actions = source.actions + (Gesture.SINGLE to action))
            val backup = parsed(backupToJson(original))
            listOf(Config(), current).forEach { destination ->
                assertEquals(original.copy(
                    enabled = destination.enabled,
                    setupComplete = destination.setupComplete,
                    introSeen = destination.introSeen,
                    grantMethod = destination.grantMethod,
                    setupSkipped = destination.setupSkipped,
                    unlockHintShown = destination.unlockHintShown,
                ), backup.applyTo(destination))
            }
        }
    }

    @Test
    fun nullLearnedKeyFieldsAndSparseActionsRoundTrip() {
        val defaults = Config()
        assertEquals(defaults, parsed(backupToJson(defaults)).applyTo(source).copy(
            enabled = defaults.enabled,
            setupComplete = defaults.setupComplete,
            introSeen = defaults.introSeen,
            grantMethod = defaults.grantMethod,
            setupSkipped = defaults.setupSkipped,
            unlockHintShown = defaults.unlockHintShown,
        ))
    }

    @Test
    fun newerVersionHasAnActionableError() {
        assertEquals(
            BackupResult.Error("This backup is from a newer SnapKey Map. Update the app first."),
            parseBackup("""{"format":2}"""),
        )
    }

    @Test
    fun rejectsMalformedTextAndMissingOrNonIntegerFormat() {
        listOf("not json", "", "[]", "{}", "{\"format\":", backupToJson(source) + " trailing").forEach(::assertRejected)
        listOf("1", 1.5, true, JSONObject.NULL).forEach { format ->
            assertRejected(json().put("format", format).toString())
        }
        assertRejected(json().put("format", 0).toString())
        assertRejected(json().apply { remove("format") }.toString())
    }

    @Test
    fun rejectsUnknownGesturesEnumsAndActionKinds() {
        assertRejected(json().apply {
            getJSONObject("actions").put("TRIPLE", JSONObject(SnapAction.NoAction.toJson()))
        }.toString())
        assertRejected(json().put("unlockedOnly", JSONArray(listOf("TRIPLE"))).toString())
        assertRejected(json().put("theme", "UNKNOWN").toString())
        assertRejected(json().apply {
            getJSONObject("actions").put("SINGLE", JSONObject().put("type", "unknown"))
        }.toString())
        assertRejected(json().apply {
            getJSONObject("actions").put("SINGLE", JSONObject().put("type", "device").put("action", "UNKNOWN"))
        }.toString())
    }

    @Test
    fun rejectsInvalidRingerPresetsAndActionsWithoutApplyingAnything() {
        listOf(
            listOf("RING", "UNKNOWN"),
            listOf("RING", "RING"),
            listOf("RING"),
            emptyList(),
        ).forEach { modes ->
            assertRejected(json().put("ringerPresets", JSONArray().put(JSONArray(modes))).toString())
            assertRejected(json().apply {
                getJSONObject("actions").put("LONG", JSONObject().put("type", "ringer").put("modes", JSONArray(modes)))
            }.toString())
        }
    }

    @Test
    fun rejectsWrongFieldTypesInsteadOfCoercingThem() {
        assertRejected(json().put("showConfirmations", "false").toString())
        assertRejected(json().put("holdMs", "900").toString())
        assertRejected(json().put("pausedApps", JSONArray(listOf(42))).toString())
        assertRejected(json().apply { getJSONObject("key").getJSONObject("keyEvent").put("keyCode", 2147483648L) }.toString())
        assertRejected(json().apply { getJSONObject("key").remove("inputKey") }.toString())
        assertRejected(json().apply { getJSONObject("actions").getJSONObject("DOUBLE").put("number", 123) }.toString())
    }

    @Test
    fun presetsIncludeAssignedModesWithoutDuplicates() {
        val backup = parsed(json().put("ringerPresets", JSONArray(ringerPresetsToJson(listOf(vibrateRing, vibrateRing)))).toString())
        assertEquals(listOf(vibrateRing, ringSilent), backup.applyTo(Config()).ringerPresets)
        val empty = parsed(json().put("ringerPresets", JSONArray()).toString())
        assertEquals(DEFAULT_RINGER_PRESETS + listOf(ringSilent), empty.applyTo(Config()).ringerPresets)
    }

    @Test
    fun logUidUsesTheExistingNormalization() {
        val backup = parsed(json().apply { getJSONObject("key").getJSONObject("logSignature").put("uid", "system") }.toString())
        assertEquals("1000", backup.applyTo(Config()).logSignature?.uid)
    }

    @Test
    fun jsonUsesNestedObjectsAndExcludesSetupAndPermissionState() {
        val json = json()
        assertTrue(json.getJSONObject("actions").get("SINGLE") is JSONObject)
        assertTrue(json.get("ringerPresets") is JSONArray)
        listOf("enabled", "setupComplete", "introSeen", "grantMethod", "setupSkipped", "unlockHintShown").forEach {
            assertFalse("Backup must not contain $it", json.has(it))
            assertFalse("Key must not contain $it", json.getJSONObject("key").has(it))
        }
    }

    private fun json(): JSONObject = JSONObject(backupToJson(source))

    private fun parsed(text: String): SettingsBackup {
        val result = parseBackup(text)
        assertTrue("Expected a valid backup, got $result", result is BackupResult.Ok)
        return (result as BackupResult.Ok).backup
    }

    private fun assertRejected(text: String) {
        val result = parseBackup(text)
        assertTrue("Expected a rejected backup, got $result", result is BackupResult.Error)
        assertTrue((result as BackupResult.Error).message.isNotBlank())
    }
}
