package com.heykay.snapkeymap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnapActionTest {
    @Test
    fun migratesTheSavedRingerCycle() {
        val action = SnapAction.fromJson("""{"type":"device","action":"RINGER_CYCLE"}""")

        assertEquals(SnapAction.Ringer(listOf(RingerMode.RING, RingerMode.VIBRATE, RingerMode.SILENT)), action)
        assertEquals(action, SnapAction.fromJson(action!!.toJson()))
    }

    @Test
    fun ringerRoundTripPreservesEveryCycleAndItsOrder() {
        cycles().forEach { modes ->
            val action = SnapAction.Ringer(modes)
            assertEquals(action, SnapAction.fromJson(action.toJson()))
        }
    }

    @Test
    fun cyclesInTheChosenOrderAndStartsAtFirstModeWhenOutsideTheList() {
        cycles().forEach { modes ->
            val action = SnapAction.Ringer(modes)
            modes.forEachIndexed { index, mode ->
                assertEquals(modes[(index + 1) % modes.size], action.nextMode(mode))
            }
            (RingerMode.entries - modes.toSet()).forEach { outside ->
                assertEquals(modes.first(), action.nextMode(outside))
            }
            assertEquals(modes.first(), action.nextMode(null))
        }
    }

    @Test
    fun withoutAccessSilentIsSkippedButTheCycleNeverLeavesTheChosenModes() {
        cycles().forEach { modes ->
            val action = SnapAction.Ringer(modes)
            (RingerMode.entries + null).forEach { current ->
                val requested = action.nextMode(current)
                val expected = if (requested == RingerMode.SILENT) action.nextMode(requested) else requested
                assertEquals(expected, action.nextMode(current, allowSilent = false))
            }
        }
        assertEquals(RingerMode.VIBRATE, SnapAction.Ringer(listOf(RingerMode.VIBRATE, RingerMode.SILENT))
            .nextMode(RingerMode.VIBRATE, allowSilent = false))
    }

    @Test
    fun labelsUseTheChosenModes() {
        assertEquals("Ring → Vibrate", SnapAction.Ringer(listOf(RingerMode.RING, RingerMode.VIBRATE)).label)
        assertEquals("Ring → Silent → Vibrate", SnapAction.Ringer(
            listOf(RingerMode.RING, RingerMode.SILENT, RingerMode.VIBRATE),
        ).label)
    }

    @Test
    fun invalidSavedCyclesAreRejected() {
        listOf("[]", "[\"RING\"]", "[\"RING\",\"RING\"]", "[\"RING\",\"UNKNOWN\"]").forEach { modes ->
            assertNull(SnapAction.fromJson("""{"type":"ringer","modes":$modes}"""))
        }
    }

    @Test
    fun deviceActionsStillRoundTripIncludingMediaMute() {
        DeviceAction.entries.forEach { device ->
            val action = SnapAction.Device(device)
            assertEquals(action, SnapAction.fromJson(action.toJson()))
        }
    }

    private fun cycles(): List<List<RingerMode>> = RingerMode.entries.flatMap { first ->
        (RingerMode.entries - first).flatMap { second ->
            listOf(listOf(first, second), listOf(first, second) + (RingerMode.entries - setOf(first, second)))
        }
    }
}
