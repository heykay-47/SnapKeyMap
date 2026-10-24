package com.heykay.snapkeymap

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class InputStreamFilterTest {
    @Test
    fun forwardsOnlyKeyEventsAcrossDevices() {
        val down = "[  42.000001] /dev/input/event0: EV_KEY       BTN_TRIGGER_HAPPY32 DOWN"
        val up = "[  42.100001] /dev/input/event12: EV_KEY BTN_TRIGGER_HAPPY32 UP"
        val other = "[  42.200001] /dev/input/event2: EV_KEY KEY_VOLUMEUP DOWN"
        val lines = listOf(
            "add device 1: /dev/input/event0",
            "[  42.000000] /dev/input/event3: EV_ABS ABS_MT_POSITION_X 00000001",
            down,
            "[  42.000002] /dev/input/event3: EV_SYN SYN_REPORT 00000000",
            up,
            other,
        )
        var flushes = 0
        val output = object : ByteArrayOutputStream() {
            override fun flush() { flushes++ }
        }

        copyInputKeyEvents(lines.joinToString("\n").byteInputStream(), output)

        assertEquals(listOf(down, up, other), output.toString("UTF-8").lineSequence().filter { it.isNotEmpty() }.toList())
        assertEquals(3, flushes)
    }
}
