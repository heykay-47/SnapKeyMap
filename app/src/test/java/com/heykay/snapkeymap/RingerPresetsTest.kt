package com.heykay.snapkeymap

import org.junit.Assert.assertEquals
import org.junit.Test

class RingerPresetsTest {
    private val ringVibrate = listOf(RingerMode.RING, RingerMode.VIBRATE)
    private val silentRing = listOf(RingerMode.SILENT, RingerMode.RING)

    @Test
    fun persistenceRoundTripPreservesPresetsAndOrder() {
        val presets = listOf(silentRing, ringVibrate, RingerMode.entries.reversed())
        assertEquals(presets, loadRingerPresets(ringerPresetsToJson(presets), emptyList()))
    }

    @Test
    fun dropsInvalidEntriesAndDuplicatesButKeepsOtherEntries() {
        val json = """[null,42,[],["RING"],["RING","RING"],["RING","UNKNOWN"],
            ["RING","VIBRATE","SILENT","RING"],["RING","VIBRATE"],
            ["RING","VIBRATE"],["SILENT","RING"]]"""
        assertEquals(listOf(ringVibrate, silentRing), loadRingerPresets(json, emptyList()))
    }

    @Test
    fun emptyOrInvalidStorageFallsBackToDefault() {
        listOf(null, "not json", "{}", "[]", "[[\"RING\"]]").forEach {
            assertEquals(DEFAULT_RINGER_PRESETS, loadRingerPresets(it, emptyList()))
        }
    }

    @Test
    fun restoresMissingAssignedPresetsWithoutDuplicates() {
        val actions = listOf(SnapAction.Ringer(silentRing), SnapAction.Ringer(silentRing), SnapAction.NoAction)
        assertEquals(listOf(ringVibrate, silentRing), loadRingerPresets(ringerPresetsToJson(listOf(ringVibrate)), actions))
        assertEquals(DEFAULT_RINGER_PRESETS + listOf(silentRing), loadRingerPresets(null, actions))
    }

    @Test
    fun editingUpdatesEveryMatchingGestureAndLeavesOthersAlone() {
        val other = SnapAction.Device(DeviceAction.FLASHLIGHT)
        val config = Config(
            ringerPresets = listOf(ringVibrate, RingerMode.entries.toList()),
            actions = mapOf(
                Gesture.SINGLE to SnapAction.Ringer(ringVibrate),
                Gesture.DOUBLE to other,
                Gesture.LONG to SnapAction.Ringer(ringVibrate),
            ),
        )
        val edited = config.editRingerPreset(ringVibrate, silentRing)
        assertEquals(listOf(silentRing, RingerMode.entries.toList()), edited.ringerPresets)
        assertEquals(SnapAction.Ringer(silentRing), edited.action(Gesture.SINGLE))
        assertEquals(SnapAction.Ringer(silentRing), edited.action(Gesture.LONG))
        assertEquals(other, edited.action(Gesture.DOUBLE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun editingCannotDuplicateAnotherPreset() {
        Config(ringerPresets = listOf(ringVibrate, silentRing)).editRingerPreset(ringVibrate, silentRing)
    }
}
