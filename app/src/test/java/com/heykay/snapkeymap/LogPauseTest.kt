package com.heykay.snapkeymap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogPauseTest {
    @Test
    fun readsLogsOnlyWithoutPreciseInputOrWhileTeaching() {
        val pause = LogPause()
        assertTrue(pause.shouldRead(precise = false, teaching = false))
        assertFalse(pause.shouldRead(precise = true, teaching = false))
        assertTrue(pause.shouldRead(precise = true, teaching = true))
        assertTrue(pause.shouldRead(precise = false, teaching = true))
    }

    @Test
    fun pausesOnceUntilResumed() {
        val pause = LogPause()
        assertEquals(listOf(true, false, false), List(3) { pause.pause() })
        assertTrue(pause.paused)
        assertTrue(pause.resume())
        assertFalse(pause.paused)
        assertTrue(pause.pause())
    }

    @Test
    fun resumeWithoutPauseDoesNothing() {
        val pause = LogPause()
        assertFalse(pause.resume())
        assertFalse(pause.paused)
    }
}
