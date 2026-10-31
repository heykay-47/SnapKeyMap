package com.heykay.snapkeymap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputKeyDeliveryTest {
    private val key = InputKey("BTN_TRIGGER_HAPPY32")

    @Test
    fun queuedReleaseAfterCancelClearsSuppressionWithoutRuntimeOrRetryDelivery() {
        for (retry in listOf(false, true)) {
            val teaching = TeachingDelivery()
            val input = InputKeyDelivery(teaching)
            teaching.advance(TeachingHandle(1))
            var teachingSamples = 0
            input.deliver(InputKeyEvent(key, true), teaching.stamp) { teachingSamples++ }
            val queuedUp = teaching.stamp
            teaching.advance(null)
            assertTrue(input.suppressHeld(key))
            if (retry) teaching.advance(TeachingHandle(2))
            input.deliver(InputKeyEvent(key, false), queuedUp) { error("Stale release was delivered") }
            assertEquals(1, teachingSamples)
            teaching.advance(null)
            if (retry) assertFalse(input.suppressHeld(key))
            val edges = mutableListOf<Boolean>()
            input.deliver(InputKeyEvent(key, true), teaching.stamp) { ignored -> if (!ignored) edges += true }
            input.deliver(InputKeyEvent(key, false), teaching.stamp) { ignored -> if (!ignored) edges += false }
            assertEquals(listOf(true, false), edges)
        }
    }

    @Test
    fun releaseBeforeTeachingTimeBoundaryStillClearsHeldState() {
        val teaching = TeachingDelivery()
        val input = InputKeyDelivery(teaching)
        input.deliver(InputKeyEvent(key, true), teaching.stamp) { }
        teaching.advance(TeachingHandle(1))
        input.deliver(InputKeyEvent(key, false), teaching.stamp) { }
        teaching.advance(null)
        assertFalse(input.suppressHeld(key))
    }

    @Test
    fun readerLossDiscardsHeldSuppressionAndReplacementCanStartWithACompletePress() {
        val teaching = TeachingDelivery()
        val input = InputKeyDelivery(teaching)
        input.deliver(InputKeyEvent(key, true), teaching.stamp) { }
        assertTrue(input.suppressHeld(key))
        input.readerLost()
        assertFalse(input.suppressHeld(key))
        val edges = mutableListOf<Boolean>()
        input.deliver(InputKeyEvent(key, true), teaching.stamp) { if (!it) edges += true }
        input.deliver(InputKeyEvent(key, false), teaching.stamp) { if (!it) edges += false }
        assertEquals(listOf(true, false), edges)
    }

    @Test
    fun releaseOfHeldKeyIsSuppressedButTheNextPressIsDelivered() {
        val teaching = TeachingDelivery()
        val input = InputKeyDelivery(teaching)
        input.deliver(InputKeyEvent(key, true), teaching.stamp) { }
        assertTrue(input.suppressHeld(key))
        val edges = mutableListOf<Boolean>()
        input.deliver(InputKeyEvent(key, false), teaching.stamp) { if (!it) edges += false }
        input.deliver(InputKeyEvent(key, true), teaching.stamp) { if (!it) edges += true }
        input.deliver(InputKeyEvent(key, false), teaching.stamp) { if (!it) edges += false }
        assertEquals(listOf(true, false), edges)
    }
}
