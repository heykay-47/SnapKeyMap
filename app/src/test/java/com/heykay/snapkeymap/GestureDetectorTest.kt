package com.heykay.snapkeymap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureDetectorTest {
    private class Harness {
        var now = 0L
        var holdMs = 650L
        var windowMs = 350L
        val gestures = mutableListOf<Gesture>()
        private val tasks = mutableMapOf<Runnable, Long>()
        val detector = GestureDetector(
            schedule = { task, delay -> tasks[task] = now + delay },
            cancel = { tasks.remove(it); Unit },
            holdMs = { holdMs },
            doublePressWindowMs = { windowMs },
            onGesture = gestures::add,
        ).apply {
            wantsDouble = true
            wantsLong = true
        }

        fun advanceTo(time: Long) {
            while (true) {
                val next = tasks.entries.minByOrNull { it.value } ?: break
                if (next.value > time) break
                now = next.value
                tasks.remove(next.key)
                next.key.run()
            }
            now = time
        }

        fun tap(time: Long) {
            advanceTo(time)
            detector.down()
            advanceTo(time + 50)
            detector.up()
        }
    }

    @Test
    fun holdingSecondPressFiresOnlyTapHold() {
        val h = Harness()
        h.detector.wantsTapHold = true
        h.tap(0)
        h.advanceTo(200)
        h.detector.down()
        h.advanceTo(849)
        assertTrue(h.gestures.isEmpty())
        h.detector.down()
        h.advanceTo(850)
        assertEquals(listOf(Gesture.TAP_HOLD), h.gestures)
        assertTrue(h.detector.hasHandledPress)
        h.detector.up()
        h.advanceTo(3000)
        assertEquals(listOf(Gesture.TAP_HOLD), h.gestures)
    }

    @Test
    fun quickSecondTapWithTapHoldAssignedFiresDoubleOnRelease() {
        val h = Harness()
        h.detector.wantsDouble = false
        h.detector.wantsTapHold = true
        h.tap(0)
        h.advanceTo(200)
        h.detector.down()
        assertTrue(h.gestures.isEmpty())
        h.advanceTo(250)
        h.detector.up()
        assertEquals(listOf(Gesture.DOUBLE), h.gestures)
        h.advanceTo(3000)
        assertEquals(listOf(Gesture.DOUBLE), h.gestures)
    }

    @Test
    fun withoutTapHoldDoubleStillFiresOnSecondDown() {
        val h = Harness()
        h.tap(0)
        h.advanceTo(200)
        h.detector.down()
        assertEquals(listOf(Gesture.DOUBLE), h.gestures)
        h.advanceTo(1000)
        h.detector.up()
        h.advanceTo(3000)
        assertEquals(listOf(Gesture.DOUBLE), h.gestures)
    }

    @Test
    fun singleWaitsForWindowWhenOnlyTapHoldIsAssigned() {
        val h = Harness()
        h.detector.wantsDouble = false
        h.detector.wantsLong = false
        h.detector.wantsTapHold = true
        h.tap(0)
        h.advanceTo(399)
        assertTrue(h.gestures.isEmpty())
        h.advanceTo(400)
        assertEquals(listOf(Gesture.SINGLE), h.gestures)
    }

    @Test
    fun resetCancelsPendingTapHold() {
        val h = Harness()
        h.detector.wantsTapHold = true
        h.tap(0)
        h.advanceTo(200)
        h.detector.down()
        h.advanceTo(300)
        h.detector.reset()
        h.detector.up()
        h.advanceTo(3000)
        assertTrue(h.gestures.isEmpty())
        h.tap(4000)
        h.advanceTo(5000)
        assertEquals(listOf(Gesture.SINGLE), h.gestures)
    }

    @Test
    fun tapHoldAssignmentDoesNotChangeFirstPressHold() {
        val h = Harness()
        h.detector.wantsTapHold = true
        h.detector.down()
        h.advanceTo(650)
        h.detector.up()
        h.advanceTo(3000)
        assertEquals(listOf(Gesture.LONG), h.gestures)
    }

    @Test
    fun holdingWithDuplicateDownsNeverYieldsDoubleOrSingle() {
        for (hold in listOf(300L, 650L, 1200L)) {
            val h = Harness().apply { holdMs = hold }
            h.detector.down()
            h.advanceTo(hold - 1)
            h.detector.down()
            assertTrue(h.gestures.isEmpty())
            h.advanceTo(hold)
            assertEquals(listOf(Gesture.LONG), h.gestures)
            h.detector.down()
            h.advanceTo(2000)
            h.detector.up()
            h.advanceTo(3000)
            assertEquals(listOf(Gesture.LONG), h.gestures)
        }
    }

    @Test
    fun secondPressInsideCustomWindowIsOnlyDouble() {
        val h = Harness().apply { windowMs = 600 }
        h.tap(0)
        h.tap(649)
        h.advanceTo(3000)
        assertEquals(listOf(Gesture.DOUBLE), h.gestures)
    }

    @Test
    fun secondPressOutsideCustomWindowIsTwoSingles() {
        val h = Harness().apply { windowMs = 200 }
        h.tap(0)
        h.tap(251)
        h.advanceTo(3000)
        assertEquals(listOf(Gesture.SINGLE, Gesture.SINGLE), h.gestures)
    }

    @Test
    fun changedTimingIsReadOnTheNextDownAndUp() {
        val h = Harness()
        h.holdMs = 1000
        h.detector.down()
        h.advanceTo(999)
        assertTrue(h.gestures.isEmpty())
        h.advanceTo(1000)
        h.detector.up()
        assertEquals(listOf(Gesture.LONG), h.gestures)

        h.holdMs = 300
        h.advanceTo(2000)
        h.detector.down()
        h.advanceTo(2299)
        h.windowMs = 600
        h.detector.up()
        h.advanceTo(2898)
        assertEquals(listOf(Gesture.LONG), h.gestures)
        h.advanceTo(2899)
        assertEquals(listOf(Gesture.LONG, Gesture.SINGLE), h.gestures)
    }

    @Test
    fun resetCancelsHalfPressAndPendingSingleWhenSourcesSwitch() {
        val h = Harness()
        h.detector.down()
        h.advanceTo(100)
        h.detector.reset()
        h.detector.up()
        h.advanceTo(2000)
        assertTrue(h.gestures.isEmpty())
        h.tap(3000)
        h.detector.reset()
        h.advanceTo(4000)
        assertTrue(h.gestures.isEmpty())
        h.tap(5000)
        h.advanceTo(6000)
        assertEquals(listOf(Gesture.SINGLE), h.gestures)
    }

    @Test
    fun handoffCanDistinguishAnAlreadyDispatchedPressFromAPendingOne() {
        val h = Harness()
        h.detector.wantsDouble = false
        h.detector.wantsLong = false
        h.detector.down()
        assertTrue(h.detector.hasHandledPress)
        h.detector.reset()
        assertFalse(h.detector.hasHandledPress)
        h.detector.wantsLong = true
        h.detector.down()
        assertFalse(h.detector.hasHandledPress)
        h.advanceTo(650)
        assertTrue(h.detector.hasHandledPress)
        h.detector.up()
        assertFalse(h.detector.hasHandledPress)
    }
}
