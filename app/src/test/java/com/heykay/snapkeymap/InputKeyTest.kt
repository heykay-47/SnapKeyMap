package com.heykay.snapkeymap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InputKeyTest {
    @Test
    fun readsExplicitEdgesAcrossDevicesAndWhitespace() {
        assertEquals(
            InputKeyEvent(InputKey("BTN_TRIGGER_HAPPY32"), true),
            parseInputKeyEvent("[   42401.336123] /dev/input/event0: EV_KEY       BTN_TRIGGER_HAPPY32   DOWN"),
        )
        assertEquals(
            InputKeyEvent(InputKey("BTN_TRIGGER_HAPPY32"), false),
            parseInputKeyEvent("[42401.430456] /dev/input/event12: EV_KEY BTN_TRIGGER_HAPPY32 UP  "),
        )
        assertEquals(
            InputKeyEvent(InputKey("KEY_VOLUMEUP"), true),
            parseInputKeyEvent("[  42.000001] /dev/input/event2: EV_KEY KEY_VOLUMEUP DOWN"),
        )
    }

    @Test
    fun ignoresRepeatsSyncDeviceInfoAndMalformedLines() {
        listOf(
            "[42401.500000] /dev/input/event0: EV_KEY BTN_TRIGGER_HAPPY32 REPEAT",
            "[42401.500000] /dev/input/event0: EV_SYN SYN_REPORT 00000000",
            "[42401.500000] /dev/input/event0: EV_ABS ABS_MT_POSITION_X 00000001",
            "add device 1: /dev/input/event0",
            "  name:     \"qpnp_pon\"",
            "could not open /dev/input/event0, Permission denied",
            "[42401.5] /dev/input/event0: EV_KEY BTN_TRIGGER_HAPPY32 00000001",
            "EV_KEY BTN_TRIGGER_HAPPY32 DOWN",
            "garbage",
            "",
        ).forEach { assertNull(it, parseInputKeyEvent(it)) }
    }
}
